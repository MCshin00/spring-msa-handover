package io.hotdealwave.server.ask;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What a tool call in the deep path came to, besides the number a numbered result gets. */
@Getter
@RequiredArgsConstructor
public enum StepMark {
    SEARCH_RESULT("검색 결과"),
    NOT_FOUND("찾지 못함"),
    ALREADY("이미 %s"),
    LIMIT("상한");

    private final String text;

    public String format(String value) { return text.formatted(value); }
}
