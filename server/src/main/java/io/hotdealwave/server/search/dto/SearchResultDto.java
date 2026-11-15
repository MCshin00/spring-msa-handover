package io.hotdealwave.server.search.dto;

import java.util.Set;

public record SearchResultDto(String page, String title, String section, double score, String preview, Set<String> services) {}
