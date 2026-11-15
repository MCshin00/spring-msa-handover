package io.hotdealwave.server;

/** Failure messages from the language model client. */
enum LanguageModelExceptionMessage {
    MISSING_KEY("OPENAI_API_KEY 환경변수가 없어 모델을 부를 수 없다"),
    MISSING_ANSWER("모델 응답에 정해진 형식의 답이 없다");

    private final String text;

    LanguageModelExceptionMessage(String text) { this.text = text; }

    public String text() { return text; }
}
