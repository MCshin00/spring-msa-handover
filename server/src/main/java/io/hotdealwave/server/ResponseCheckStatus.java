package io.hotdealwave.server;

/** Status of a recorded live response. */
enum ResponseCheckStatus {
    RECORDED("응답 기록 있음");

    private final String text;

    ResponseCheckStatus(String text) { this.text = text; }

    public String text() { return text; }
}
