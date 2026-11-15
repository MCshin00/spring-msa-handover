package io.hotdealwave.server.page;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Displayed scope of endpoint verification. */
@Getter
@RequiredArgsConstructor
public enum VerificationScope {
    CODE_ONLY("코드만"),
    RESPONSE("코드 + 실제 응답"),
    ERROR_RESPONSE("코드 + 실제 응답(오류)");

    private final String text;

    public String withCode(int code) { return text.substring(0, text.length() - 1) + " " + code + ")"; }
}
