package io.hotdealwave.server.page;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Messages for missing pages and recorded responses. */
@Getter
@RequiredArgsConstructor
public enum PageExceptionMessage {
    PAGE_NOT_FOUND("페이지 없음"),
    RESPONSE_NOT_FOUND("응답 파일 없음");

    private final String text;

}
