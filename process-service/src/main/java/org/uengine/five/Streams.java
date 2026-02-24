package org.uengine.five;

/** 바인딩 이름 상수 (StreamBridge, BpmStreamFunctions 등에서 사용). Spring Cloud Stream 4에서는 함수형 바인딩 사용. */
public final class Streams {
    public static final String INPUT = "bpm-in-0"; // 입력: process-service가 BPM 이벤트를 받는 채널
    public static final String OUTPUT = "bpm-out"; // 출력: process-service가 BPM 이벤트를 보내는 채널
    public static final String OUTPUT_BRODCAST = "bpm-brodcast"; // 브로드캐스트: process-service가 BPM 이벤트를 브로드캐스트하는 채널

    private Streams() {}
}
