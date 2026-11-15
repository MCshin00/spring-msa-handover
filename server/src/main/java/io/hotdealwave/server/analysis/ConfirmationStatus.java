package io.hotdealwave.server.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Developer confirmation labels for interpreted knowledge. */
@Getter
@RequiredArgsConstructor
public enum ConfirmationStatus {
    UNCONFIRMED("미확인"),
    CONFIRMED("확인함");

    private final String text;

}
