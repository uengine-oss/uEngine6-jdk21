package org.uengine.hwlife.session;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.iam.dto.RsaKyprResponse;
import org.uengine.hwlife.session.dto.SessionLoginRequest;
import org.uengine.hwlife.session.dto.SessionResponse;
import org.uengine.hwlife.session.dto.SessionSsoRequest;
import org.uengine.hwlife.session.dto.SessionTokenRequest;

/**
 * 세션 로그인(SSO·대체) / 유효성 / 연장 / 로그아웃 API.
 *
 * <pre>
 *   GET  /session/rsa-kypr  대체로그인용 RSA 공개키
 *   POST /session/login     대체로그인 (ESB ctfn-logn + 세션/JWT)
 *   POST /session/sso       SSO 핸드셰이크
 * </pre>
 */
@RequestMapping("/session")
public interface SessionService {

    @RequestMapping(value = "/rsa-kypr", method = RequestMethod.GET)
    RsaKyprResponse getRsaKypr() throws Exception;

    @RequestMapping(value = "/sso", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse loginSso(@RequestBody(required = false) SessionSsoRequest request) throws Exception;

    @RequestMapping(value = "/login", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse loginAlternate(@RequestBody SessionLoginRequest request) throws Exception;

    @RequestMapping(value = "/logout", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse logout(@RequestBody SessionTokenRequest request) throws Exception;

    @RequestMapping(value = "/check", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse check(@RequestBody SessionTokenRequest request) throws Exception;

    @RequestMapping(value = "/extend", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse extend(@RequestBody SessionTokenRequest request) throws Exception;

    @RequestMapping(value = "/me", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    SessionResponse me(@RequestBody SessionTokenRequest request) throws Exception;
}
