package io.hotdealwave.server.ask;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Results sent back to the model for tool calls the deep path does not run. */
@Getter
@RequiredArgsConstructor
public enum ToolResultMessage {
    ALREADY_NUMBERED("이미 %s에 있는 결과라 실행하지 않았다. 그 번호를 인용한다."),
    ALREADY_SEARCHED("이미 같은 인자로 찾아봤다."),
    UNREADABLE("인자를 읽지 못해 실행하지 않았다: %s"),
    LIMIT("도구 호출 상한 %s번에 닿아 실행하지 않았다.");

    private final String text;

    public String format(Object value) { return text.formatted(value); }
}
