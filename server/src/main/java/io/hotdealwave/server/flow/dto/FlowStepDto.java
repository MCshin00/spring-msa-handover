package io.hotdealwave.server.flow.dto;

import java.util.List;

/** One hop. {@code to} is null when the next hop could not be confirmed; then status and reason say why. */
public record FlowStepDto(String from, String to, String kind, String status, String reason, List<String> evidence) {}
