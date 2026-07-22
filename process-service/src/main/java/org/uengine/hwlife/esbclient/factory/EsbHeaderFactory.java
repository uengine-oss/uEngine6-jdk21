package org.uengine.hwlife.esbclient.factory;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.uengine.hwlife.esbclient.dto.EsbCodes;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;

/**
 * ESB 요청용 {@link EsbCommonHeader} 생성 팩토리.
 *
 * <p>호출자(업무 코드)가 매번 공통 헤더 필드를 채우지 않도록,
 * {@code itfcId}, {@code rcveSrvcId}만 넘기면 요청에 필요한
 * <b>시스템 공통부 + 요청정보</b>가 세팅된 헤더를 만들어 준다.
 * 응답정보/메시지 구역은 채우지 않는다 (응답 조립 시 {@code EsbEnvelope} 이 채움).</p>
 *
 * <ul>
 *   <li>설정: serverType ({@code esb.server-type}) — application.yml/환경변수 값</li>
 *   <li>시스템 공통부: trnmSysCode, prsnInfoIncsYn, rspnDvsnCode, ipAddr, tlgrCretDttm …</li>
 *   <li>요청정보: rqstDttm, rqsrIp, baseLang/Cnty/Crny … (emnb 등은 호출 후 setter 로 추가)</li>
 *   <li>가변(인자): itfcId, rcveSrvcId</li>
 * </ul>
 */
@Component
public class EsbHeaderFactory {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger HSNO = new AtomicInteger(1);

    /** ESB 서버 구분값. application.yml {@code esb.server-type}(환경변수 {@code ESB_SERVER_TYPE})에서 주입. */
    private final String serverType;

    public EsbHeaderFactory(@Value("${esb.server-type:}") String serverType) {
        this.serverType = serverType;
    }

    /**
     * @param itfcId     인터페이스 아이디
     * @param rcveSrvcId 수신 서비스 아이디 (호출 측에서 전달)
     * @return 요청용 ESB 공통 헤더
     */
    public EsbCommonHeader create(String itfcId, String rcveSrvcId) {
        String now = now();
        String hostIp = localIp();
        // ESB ipAddr: 10.20.30.40 → 010020030040 (길이 12)
        String ipAddr = toEsbIpAddr(hostIp);
        return EsbCommonHeader.builder()
                .itfcId(itfcId)
                .rcveSrvcId(rcveSrvcId)
                .trnmSysCode("BPM_CODE")
                .ipAddr(ipAddr)
                .tlgrCretDttm(now)
                .rqstDttm(now)
                .rndmNo(String.format("%04d", RANDOM.nextInt(10000)))
                .hsno(HSNO.getAndIncrement())
                .ogtsTrnnNo(UUID.randomUUID().toString())
                .prsnInfoIncsYn("N")
                .serverType(serverType)
                .rspnDvsnCode("S")
                .rqsrIp(hostIp)
                .baseLang("KOR")
                .baseCnty("KOR")
                .baseCrny("KRW")
                .build();
    }

    private String now() {
        return LocalDateTime.now().format(EsbCodes.DTTM);
    }

    private String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * ESB ipAddr 포맷: {@code 10.20.30.40} → {@code 010020030040}
     * (옥텟 3자리 zero-pad, 점 제거, 총 12자리)
     */
    static String toEsbIpAddr(String dottedIp) {
        if (dottedIp == null || dottedIp.isBlank()) {
            return "127000000001";
        }
        try {
            String[] parts = dottedIp.trim().split("\\.");
            if (parts.length != 4) {
                return "127000000001";
            }
            StringBuilder sb = new StringBuilder(12);
            for (String part : parts) {
                sb.append(String.format("%03d", Integer.parseInt(part)));
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            return "127000000001";
        }
    }
}
