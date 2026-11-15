package io.hotdealwave.server;

import java.util.Arrays;

/** Warning check identifiers and the labels shown on the home page. */
enum WarningCheck {
    MESSAGE_DESTINATIONS("message-destinations", "메시지 연결"), HANDLER_CALLS("handler-calls", "HTTP 호출");

    private final String key;
    private final String text;

    WarningCheck(String key, String text) { this.key = key; this.text = text; }

    public String key() { return key; }
    public String text() { return text; }

    static String text(String key) {
        return Arrays.stream(values()).filter(check -> check.key.equals(key)).map(WarningCheck::text).findFirst().orElse(key);
    }
}
