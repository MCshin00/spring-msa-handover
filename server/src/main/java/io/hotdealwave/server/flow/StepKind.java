package io.hotdealwave.server.flow;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Fixed flow step kinds and the label for an HTTP target. */
@Getter
@RequiredArgsConstructor
public enum StepKind {
    CALL("호출"),
    SUBSCRIPTION("구독");

    private final String text;

    public static String target(String kind) { return kind + " 대상"; }
}
