package io.hotdealwave.server.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Sources of exception handler values. */
@Getter
@RequiredArgsConstructor
public enum ValueSource {
    FIXED("고정"),
    THROWN_ERROR_CODE("던진 오류 코드");

    private final String text;

}
