package io.hotdealwave.server;

/** Developer confirmation labels for interpreted knowledge. */
enum ConfirmationStatus {
    UNCONFIRMED("미확인"),
    CONFIRMED("확인함");

    private final String text;

    ConfirmationStatus(String text) { this.text = text; }

    public String text() { return text; }
}
