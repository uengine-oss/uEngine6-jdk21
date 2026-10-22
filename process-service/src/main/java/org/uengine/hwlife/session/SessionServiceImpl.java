package org.uengine.hwlife.session;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.iam.ExternalIAMService;
import org.uengine.hwlife.iam.dto.RsaKyprResponse;
import org.uengine.hwlife.iam.dto.UserCtfnLognRequest;
import org.uengine.hwlife.iam.dto.UserCtfnLognResponse;
import org.uengine.hwlife.session.dto.SessionLoginRequest;
import org.uengine.hwlife.session.dto.SessionResponse;
import org.uengine.hwlife.session.dto.SessionSsoRequest;
import org.uengine.hwlife.session.dto.SessionTokenRequest;
import org.uengine.hwlife.session.dto.SessionUserInfo;
import org.uengine.hwlife.session.entity.UserSessionEntity;
import org.uengine.hwlife.session.repository.UserSessionRepository;
import org.uengine.hwlife.session.support.ClientIpResolver;
import org.uengine.hwlife.session.support.JwtSessionTokenService;
import org.uengine.hwlife.session.support.SessionIdlePolicy;
import org.uengine.hwlife.session.support.SsoSessionEvidenceChecker;
import org.uengine.hwlife.session.support.SsoUserResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@CrossOrigin(origins = "*")
@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private static final String ESB_OK = "01";
    private static final String ESB_FAIL = "00";

    private final UserSessionRepository sessionRepository;
    private final SessionIdlePolicy idlePolicy;
    private final ClientIpResolver clientIpResolver;
    private final SsoUserResolver ssoUserResolver;
    private final SsoSessionEvidenceChecker ssoEvidenceChecker;
    private final JwtSessionTokenService jwtSessionTokenService;
    private final ObjectMapper objectMapper;
    private final ExternalIAMService externalIamService = ExternalIAMService.getDefault();

    public SessionServiceImpl(
            UserSessionRepository sessionRepository,
            SessionIdlePolicy idlePolicy,
            ClientIpResolver clientIpResolver,
            SsoUserResolver ssoUserResolver,
            SsoSessionEvidenceChecker ssoEvidenceChecker,
            JwtSessionTokenService jwtSessionTokenService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.idlePolicy = idlePolicy;
        this.clientIpResolver = clientIpResolver;
        this.ssoUserResolver = ssoUserResolver;
        this.ssoEvidenceChecker = ssoEvidenceChecker;
        this.jwtSessionTokenService = jwtSessionTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RsaKyprResponse getRsaKypr() {
        return externalIamService.getRsaKypr();
    }

    @Override
    @Transactional
    public SessionResponse loginSso(@RequestBody(required = false) SessionSsoRequest request) {
        HttpServletRequest http = currentRequest();
        String ip = clientIpResolver.resolve(http);
        String smSessionId = ssoUserResolver.resolveSmServerSessionId(
                http, request != null ? request.getSmServerSessionId() : null);
        String userId = ssoUserResolver.resolveUserId(http);
        Integer idleReq = request != null ? request.getIdleTimeoutMinutes() : null;

        if (smSessionId == null) {
            saveFail(UserSessionEntity.LOGIN_TYPE_SSO, userId, null, ip,
                    "SSO_SESSION_ID_MISSING", "SM_SERVERSESSIONID is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO_SESSION_ID_MISSING");
        }
        if (userId == null) {
            saveFail(UserSessionEntity.LOGIN_TYPE_SSO, null, null, ip,
                    "SSO_USER_MISSING", "SM_USER header/cookie is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO_USER_MISSING");
        }

        SessionUserInfo user = new SessionUserInfo();
        user.setEmnb(userId);
        return completeLogin(UserSessionEntity.LOGIN_TYPE_SSO, user, ip, idleReq);
    }

    @Override
    @Transactional
    public SessionResponse loginAlternate(@RequestBody SessionLoginRequest request) {
        HttpServletRequest http = currentRequest();
        String ip = clientIpResolver.resolve(http);
        String emnb = request != null ? trimToNull(request.getEmnb()) : null;
        String pw = request != null ? trimToNull(request.getLognPswd()) : null;

        if (emnb == null || pw == null) {
            saveFail(UserSessionEntity.LOGIN_TYPE_ALTERNATE, emnb, null, ip,
                    "ALT_REQUEST_INVALID", "emnb/lognPswd required");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ALT_REQUEST_INVALID");
        }

        UserCtfnLognRequest ctfnReq = new UserCtfnLognRequest();
        ctfnReq.setEmnb(emnb);
        ctfnReq.setLognPswd(pw);

        UserCtfnLognResponse ctfn;
        try {
            ctfn = externalIamService.requestUserCtfnLogn(ctfnReq);
        } catch (Exception e) {
            log.error("[session] ESB ctfn-logn error emnb={}", emnb, e);
            saveFail(UserSessionEntity.LOGIN_TYPE_ALTERNATE, emnb, null, ip,
                    "ALT_ESB_ERROR", truncate(e.getMessage(), 255));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ALT_ESB_ERROR");
        }

        String code = ctfn != null ? trimToNull(ctfn.getRspnCodeVal()) : null;
        String uniqid = ctfn != null ? trimToNull(ctfn.getRspnMsgeUniqid()) : null;
        String msge = ctfn != null ? trimToNull(ctfn.getRspnMsgeCntn()) : null;
        // 스텁(빈 응답)은 개발 편의상 성공으로 간주. 실연동 시 01만 성공.
        boolean ok = code == null || ESB_OK.equals(code);
        if (!ok || ESB_FAIL.equals(code)) {
            // reason_code 에 ESB 오류코드(rspnMsgeUniqid) 우선 저장
            // COME0157/0162/0163/0121:해당아이디없음, COMV0118:비밀번호값없음,
            // COME0303:5회잠김, COME0304:비밀번호오류(횟수포함) 등
            String reasonCode = uniqid != null ? uniqid : "ALT_ESB_AUTH_FAIL";
            String reasonMsg = msge != null ? msge : "로그인 처리 중 에러 발생";
            saveFail(UserSessionEntity.LOGIN_TYPE_ALTERNATE, emnb, null, ip,
                    reasonCode, truncate(reasonMsg, 255));

            SessionResponse fail = new SessionResponse();
            fail.setLoginType(UserSessionEntity.LOGIN_TYPE_ALTERNATE);
            fail.setSessionStatus(UserSessionEntity.STATUS_FAIL);
            fail.setRspnCodeVal(code != null ? code : ESB_FAIL);
            fail.setRspnMsgeUniqid(uniqid);
            fail.setRspnMsgeCntn(reasonMsg);
            fail.setReasonCode(reasonCode);
            fail.setReasonMsg(reasonMsg);
            return fail;
        }

        SessionUserInfo user = fromCtfn(emnb, ctfn);
        SessionResponse okResp = completeLogin(
                UserSessionEntity.LOGIN_TYPE_ALTERNATE, user, ip, request.getIdleTimeoutMinutes());
        okResp.setRspnCodeVal(code != null ? code : ESB_OK);
        return okResp;
    }

    @Override
    @Transactional
    public SessionResponse logout(@RequestBody SessionTokenRequest request) {
        UserSessionEntity session = requireSession(request, false);
        if (UserSessionEntity.STATUS_ACTIVE.equals(session.getStatus())) {
            session.setStatus(UserSessionEntity.STATUS_LOGGED_OUT);
            session.setLogoutAt(new Date());
            session.setReasonCode("LOGGED_OUT");
            sessionRepository.save(session);
        }
        SessionResponse response = new SessionResponse();
        response.setSessionToken(session.getSessionToken());
        response.setLoginType(session.getLoginType());
        response.setSessionStatus(session.getStatus());
        response.setReasonCode(session.getReasonCode());
        return response;
    }

    @Override
    @Transactional
    public SessionResponse check(@RequestBody SessionTokenRequest request) {
        return evaluateSession(request, false);
    }

    @Override
    @Transactional
    public SessionResponse extend(@RequestBody SessionTokenRequest request) {
        SessionResponse evaluated = evaluateSession(request, false);
        if (!UserSessionEntity.STATUS_ACTIVE.equals(evaluated.getSessionStatus())) {
            return evaluated;
        }
        UserSessionEntity session = requireSession(request, false);
        Date now = new Date();
        HttpServletRequest http = currentRequest();
        session.setLastSeenAt(now);
        session.setLastSeenIp(clientIpResolver.resolve(http));
        sessionRepository.save(session);
        return toActiveResponse(session, null, null, false);
    }

    @Override
    @Transactional
    public SessionResponse me(@RequestBody SessionTokenRequest request) {
        return evaluateSession(request, true);
    }

    private SessionResponse evaluateSession(SessionTokenRequest request, boolean includeLastLogin) {
        UserSessionEntity session = requireSession(request, true);
        Date now = new Date();
        HttpServletRequest http = currentRequest();

        if (!UserSessionEntity.STATUS_ACTIVE.equals(session.getStatus())) {
            SessionResponse response = baseStatusResponse(session);
            if (includeLastLogin) {
                fillLastLogin(response, session.getUserId(), session.getSessId());
            }
            return response;
        }

        String ssoReason = ssoEvidenceChecker.invalidReason(session, http);
        if (ssoReason != null) {
            expire(session, ssoReason, "SSO session ended");
            SessionResponse response = baseStatusResponse(session);
            response.setReasonCode(ssoReason);
            return response;
        }

        if (idlePolicy.isExpired(session, now)) {
            expire(session, "IDLE_EXPIRED", "Idle timeout");
            return baseStatusResponse(session);
        }

        // 동일 세션 IP 변경(위치 이동) — 세션 유지, IP만 갱신
        String ip = clientIpResolver.resolve(http);
        if (ip != null && !ip.equals(session.getLastSeenIp())) {
            session.setLastSeenIp(ip);
            sessionRepository.save(session);
        }

        SessionResponse response = toActiveResponse(session, null, null, false);
        if (includeLastLogin) {
            fillLastLogin(response, session.getUserId(), session.getSessId());
            response.setUser(readSnapshot(session));
        }
        return response;
    }

    private SessionResponse completeLogin(
            String loginType,
            SessionUserInfo user,
            String ip,
            Integer idleTimeoutMinutes) {

        String userId = user != null ? trimToNull(user.getEmnb()) : null;
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED");
        }
        String userName = user != null ? trimToNull(user.getEmpeNm()) : null;

        LastLogin prev = findPreviousSuccess(userId);
        boolean displaced = displaceActive(userId);

        Date now = new Date();
        int idle = idlePolicy.clampIdleMinutes(idleTimeoutMinutes);

        UserSessionEntity session = new UserSessionEntity();
        session.setResult(UserSessionEntity.RESULT_SUCCESS);
        session.setStatus(UserSessionEntity.STATUS_ACTIVE);
        session.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setUserName(userName);
        session.setLoginType(loginType);
        session.setLoginIp(ip);
        session.setLastSeenIp(ip);
        session.setLoginAt(now);
        session.setLastSeenAt(now);
        session.setIdleTimeoutMinutes(idle);
        session.setUserSnapshotJson(writeSnapshot(user));
        sessionRepository.save(session);

        String jwt = jwtSessionTokenService.issue(session, userClaims(user));
        SessionResponse response = toActiveResponse(session, prev, displaced, true);
        response.setAccessToken(jwt);
        response.setUser(user);
        return response;
    }

    /** @return 기존 ACTIVE 세션을 DISPLACE 했으면 true */
    private boolean displaceActive(String userId) {
        List<UserSessionEntity> actives =
                sessionRepository.findByUserIdAndStatus(userId, UserSessionEntity.STATUS_ACTIVE);
        if (actives == null || actives.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (UserSessionEntity active : actives) {
            active.setStatus(UserSessionEntity.STATUS_DISPLACED);
            active.setLogoutAt(now);
            active.setReasonCode("DISPLACED");
            active.setReasonMsg("Displaced by new login");
            sessionRepository.save(active);
        }
        return true;
    }

    private void expire(UserSessionEntity session, String reasonCode, String reasonMsg) {
        session.setStatus(UserSessionEntity.STATUS_EXPIRED);
        session.setLogoutAt(new Date());
        session.setReasonCode(reasonCode);
        session.setReasonMsg(reasonMsg);
        sessionRepository.save(session);
    }

    private void saveFail(
            String loginType,
            String userId,
            String userName,
            String ip,
            String reasonCode,
            String reasonMsg) {
        UserSessionEntity fail = new UserSessionEntity();
        fail.setResult(UserSessionEntity.RESULT_FAIL);
        fail.setStatus(UserSessionEntity.STATUS_FAIL);
        fail.setLoginType(loginType);
        fail.setUserId(userId);
        fail.setUserName(userName);
        fail.setLoginIp(ip);
        fail.setLoginAt(new Date());
        fail.setReasonCode(reasonCode);
        fail.setReasonMsg(reasonMsg);
        sessionRepository.save(fail);
    }

    private UserSessionEntity requireSession(SessionTokenRequest request, boolean allowInactive) {
        String sid = resolveSessionToken(request);
        if (sid == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SESSION_TOKEN_REQUIRED");
        }
        UserSessionEntity session = sessionRepository.findBySessionToken(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SESSION_NOT_FOUND"));
        if (!allowInactive && !UserSessionEntity.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, session.getStatus());
        }
        return session;
    }

    private String resolveSessionToken(SessionTokenRequest request) {
        if (request == null) {
            return null;
        }
        String direct = trimToNull(request.getSessionToken());
        if (direct != null) {
            return direct;
        }
        String accessToken = trimToNull(request.getAccessToken());
        if (accessToken == null) {
            return null;
        }
        try {
            return jwtSessionTokenService.extractSessionToken(accessToken);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        }
    }

    private SessionResponse toActiveResponse(
            UserSessionEntity session,
            LastLogin prev,
            Boolean displaced,
            boolean loginResponse) {
        Date now = new Date();
        SessionResponse response = new SessionResponse();
        response.setSessionToken(session.getSessionToken());
        response.setLoginType(session.getLoginType());
        response.setSessionStatus(session.getStatus());
        response.setLoginAt(session.getLoginAt());
        response.setLoginIp(session.getLoginIp());
        response.setIdleTimeoutMinutes(session.getIdleTimeoutMinutes());
        response.setIdleRemainingSeconds(idlePolicy.remainingSeconds(session, now));
        response.setExtendPrompt(idlePolicy.shouldPromptExtend(session, now));
        response.setExpiresAt(idlePolicy.expiresAt(session));
        if (loginResponse) {
            response.setDisplacedExisting(Boolean.TRUE.equals(displaced));
            if (prev != null) {
                response.setLastLoginAt(prev.at);
                response.setLastLoginIp(prev.ip);
            }
        }
        return response;
    }

    private SessionResponse baseStatusResponse(UserSessionEntity session) {
        SessionResponse response = new SessionResponse();
        response.setSessionToken(session.getSessionToken());
        response.setLoginType(session.getLoginType());
        response.setSessionStatus(session.getStatus());
        response.setReasonCode(session.getReasonCode());
        response.setReasonMsg(session.getReasonMsg());
        response.setLoginAt(session.getLoginAt());
        response.setLoginIp(session.getLoginIp());
        return response;
    }

    private void fillLastLogin(SessionResponse response, String userId, Long excludeSessId) {
        LastLogin prev = findPreviousSuccess(userId, excludeSessId);
        if (prev != null) {
            response.setLastLoginAt(prev.at);
            response.setLastLoginIp(prev.ip);
        } else {
            // 현재가 최초면 현재 로그인 정보를 최종으로 노출
            response.setLastLoginAt(response.getLoginAt());
            response.setLastLoginIp(response.getLoginIp());
        }
    }

    private LastLogin findPreviousSuccess(String userId) {
        return findPreviousSuccess(userId, null);
    }

    private LastLogin findPreviousSuccess(String userId, Long excludeSessId) {
        if (userId == null) {
            return null;
        }
        List<UserSessionEntity> list = sessionRepository.findSuccessByUserIdOrderByLoginAtDesc(
                userId, PageRequest.of(0, 5));
        for (UserSessionEntity row : list) {
            if (excludeSessId != null && excludeSessId.equals(row.getSessId())) {
                continue;
            }
            return new LastLogin(row.getLoginAt(), row.getLoginIp());
        }
        return null;
    }

    private SessionUserInfo fromCtfn(String emnb, UserCtfnLognResponse ctfn) {
        SessionUserInfo user = new SessionUserInfo();
        user.setEmnb(emnb);
        if (ctfn == null) {
            return user;
        }
        user.setEmpeNm(ctfn.getEmpeNm());
        user.setBelnOrgnCode(ctfn.getBelnOrgnCode());
        user.setBelnOrgnNm(ctfn.getBelnOrgnNm());
        user.setBrocOrgnCode(ctfn.getBrocOrgnCode());
        user.setBrocNm(ctfn.getBrocNm());
        user.setOrgnAbrvNm(ctfn.getOrgnAbrvNm());
        user.setHgrnOrgnCode(ctfn.getHgrnOrgnCode());
        user.setHgrnOrgnNm(ctfn.getHgrnOrgnNm());
        user.setLcscOrgnCode(ctfn.getLcscOrgnCode());
        user.setLcscNm(ctfn.getLcscNm());
        user.setMailAddr(ctfn.getMailAddr());
        user.setUserDvsnCode(ctfn.getUserDvsnCode());
        user.setOgztFormDvsnCode(ctfn.getOgztFormDvsnCode());
        user.setRsofCode(ctfn.getRsofCode());
        return user;
    }

    private Map<String, Object> userClaims(SessionUserInfo user) {
        Map<String, Object> claims = new HashMap<>();
        if (user == null) {
            return claims;
        }
        put(claims, "belnOrgnCode", user.getBelnOrgnCode());
        put(claims, "belnOrgnNm", user.getBelnOrgnNm());
        put(claims, "mailAddr", user.getMailAddr());
        put(claims, "rsofCode", user.getRsofCode());
        return claims;
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private String writeSnapshot(SessionUserInfo user) {
        try {
            return objectMapper.writeValueAsString(user);
        } catch (Exception e) {
            return null;
        }
    }

    private SessionUserInfo readSnapshot(UserSessionEntity session) {
        if (session.getUserSnapshotJson() == null || session.getUserSnapshotJson().isBlank()) {
            SessionUserInfo user = new SessionUserInfo();
            user.setEmnb(session.getUserId());
            user.setEmpeNm(session.getUserName());
            return user;
        }
        try {
            return objectMapper.readValue(session.getUserSnapshotJson(), SessionUserInfo.class);
        } catch (Exception e) {
            SessionUserInfo user = new SessionUserInfo();
            user.setEmnb(session.getUserId());
            user.setEmpeNm(session.getUserName());
            return user;
        }
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class LastLogin {
        private final Date at;
        private final String ip;

        private LastLogin(Date at, String ip) {
            this.at = at;
            this.ip = ip;
        }
    }
}
