package io.hotdealwave.server;

/** Verification status text shared by page bodies and decoration. */
enum FactStatus {
    CONFIRMED("확인됨"),
    UNCONFIRMED("확인 안 됨"),
    NO_REPOSITORY_EVIDENCE("저장소에 근거 없음");

    private final String text;

    FactStatus(String text) { this.text = text; }

    public String text() { return text; }
}
