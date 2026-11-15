package io.hotdealwave.server.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Status of a recorded live response. */
@Getter
@RequiredArgsConstructor
public enum ResponseCheckStatus {
    RECORDED("응답 기록 있음");

    private final String text;

}
