package io.hotdealwave.server.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Knowledge labels and their display classes. */
@Getter
@RequiredArgsConstructor
public enum KnowledgeKind {
    TRAP("함정", "trap"), RULE("업무 규칙", "rule"), ABOUT("설명", "about");

    private final String text;
    private final String kindClass;

}
