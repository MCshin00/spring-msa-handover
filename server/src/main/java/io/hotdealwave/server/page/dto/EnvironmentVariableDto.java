package io.hotdealwave.server.page.dto;

/** An environment variable name with every place that reads it, when its value is not confirmed. */
public record EnvironmentVariableDto(String name, String status, String services, String profiles, String locations, String evidence) {}
