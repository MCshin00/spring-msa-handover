package io.hotdealwave.server;

/** Sources of exception handler values. */
enum ValueSource {
    FIXED("고정"),
    THROWN_ERROR_CODE("던진 오류 코드");

    private final String text;

    ValueSource(String text) { this.text = text; }

    public String text() { return text; }
}
