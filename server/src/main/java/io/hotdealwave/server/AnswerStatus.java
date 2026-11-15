package io.hotdealwave.server;

import com.fasterxml.jackson.annotation.JsonValue;

/** Answer status with the existing JSON and display values. */
enum AnswerStatus {
    ANSWER("답"),
    ABSTAIN("보류"),
    ERROR("오류");

    private final String text;

    AnswerStatus(String text) { this.text = text; }

    @JsonValue
    public String text() { return text; }

    @Override public String toString() { return text; }
}
