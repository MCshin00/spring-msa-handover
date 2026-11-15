package io.hotdealwave.server;

/** Fixed flow step kinds and the label for an HTTP target. */
enum StepKind {
    CALL("호출"),
    SUBSCRIPTION("구독");

    private final String text;

    StepKind(String text) { this.text = text; }

    public String text() { return text; }

    static String target(String kind) { return kind + " 대상"; }
}
