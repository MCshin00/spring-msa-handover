package io.hotdealwave.server;

/** Knowledge labels and their display classes. */
enum KnowledgeKind {
    TRAP("함정", "trap"), RULE("업무 규칙", "rule"), ABOUT("설명", "about");

    private final String text;
    private final String kindClass;

    KnowledgeKind(String text, String kindClass) { this.text = text; this.kindClass = kindClass; }

    public String text() { return text; }
    public String kindClass() { return kindClass; }
}
