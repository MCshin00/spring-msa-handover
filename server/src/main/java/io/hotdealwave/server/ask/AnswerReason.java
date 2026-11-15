package io.hotdealwave.server.ask;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Reasons for abstaining or failing without changing answer JSON. */
@Getter
@RequiredArgsConstructor
public enum AnswerReason {
    NO_RESULTS("검색 결과 없음"),
    MODEL_ABSTAIN("모델 보류"),
    NO_CITATIONS("인용 검사: 인용 없음"),
    MISSING_CHUNK("인용 검사: 없는 조각 %s"),
    MISSING_EVIDENCE("인용 검사: 인용한 조각에 없는 %s"),
    EMBEDDING_FAILURE("임베딩 호출 실패: %s"),
    MODEL_FAILURE("모델 호출 실패: %s");

    private final String text;

    public String format(String value) { return text.formatted(value); }
}
