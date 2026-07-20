package org.uengine.hwlife.esbclient.factory;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;

/**
 * ESB 요청용 {@link EsbCommonHeader} 생성 팩토리.
 *
 * <p>호출자(업무 코드)가 매번 공통 헤더 필드를 채우지 않도록,
 * {@code itfcId}, {@code rcveSrvcId}만 넘기면 요청에 필요한
 * <b>고정값·자동생성값</b>이 세팅된 헤더를 만들어 준다.</p>
 *
 * <ul>
 *   <li>설정: serverType ({@code esb.server-type}) — application.yml/환경변수 값</li>
 *   <li>고정: trnmSysCode, prsnInfoIncsYn, rspnDvsnCode, baseLang/Cnty/Crny …</li>
 *   <li>자동: tlgrCretDttm, rqstDttm, rndmNo, hsno, ipAddr, ogtsTrnnNo …</li>
 *   <li>가변(인자): itfcId, rcveSrvcId — 그 외 emnb 등은 호출 후 setter로 추가</li>
 * </ul>
 */
@Component
public class EsbHeaderFactory {

    private static final DateTimeFormatter DTTM =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
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
        return LocalDateTime.now().format(DTTM);
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
