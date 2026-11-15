package io.hotdealwave.server.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Verification status text shared by page bodies and decoration. */
@Getter
@RequiredArgsConstructor
public enum FactStatus {
    CONFIRMED("확인됨"),
    UNCONFIRMED("확인 안 됨"),
    NO_REPOSITORY_EVIDENCE("저장소에 근거 없음");

    private final String text;

}
