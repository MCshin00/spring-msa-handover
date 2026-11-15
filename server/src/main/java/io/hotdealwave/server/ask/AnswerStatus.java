package io.hotdealwave.server.ask;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Answer status with the existing JSON and display values. */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum AnswerStatus {
    ANSWER("답"),
    ABSTAIN("보류"),
    ERROR("오류");

    private final String text;

    @Override public String toString() { return text; }
}
