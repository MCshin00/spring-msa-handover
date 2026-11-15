package io.hotdealwave.server.client;

import java.util.List;

public record ModelReply(String answer, List<String> citations, boolean abstain, long inputTokens, long outputTokens) {}
