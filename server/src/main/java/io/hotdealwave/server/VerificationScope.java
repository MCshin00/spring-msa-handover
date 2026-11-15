package io.hotdealwave.server;

/** Displayed scope of endpoint verification. */
enum VerificationScope {
    CODE_ONLY("코드만"),
    RESPONSE("코드 + 실제 응답"),
    ERROR_RESPONSE("코드 + 실제 응답(오류)");

    private final String text;

    VerificationScope(String text) { this.text = text; }

    public String text() { return text; }

    String withCode(int code) { return text.substring(0, text.length() - 1) + " " + code + ")"; }
}
