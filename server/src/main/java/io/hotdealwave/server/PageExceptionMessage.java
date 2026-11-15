package io.hotdealwave.server;

/** Messages for missing pages and recorded responses. */
enum PageExceptionMessage {
    PAGE_NOT_FOUND("페이지 없음"),
    RESPONSE_NOT_FOUND("응답 파일 없음");

    private final String text;

    PageExceptionMessage(String text) { this.text = text; }

    public String text() { return text; }
}
