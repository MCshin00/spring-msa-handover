package io.hotdealwave.server.analysis;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Warning check identifiers and the labels shown on the home page. */
@Getter
@RequiredArgsConstructor
public enum WarningCheck {
    MESSAGE_DESTINATIONS("message-destinations", "메시지 연결"), HANDLER_CALLS("handler-calls", "HTTP 호출");

    private final String key;
    private final String text;

    public static String text(String key) {
        return Arrays.stream(values()).filter(check -> check.key.equals(key)).map(WarningCheck::getText).findFirst().orElse(key);
    }
}
