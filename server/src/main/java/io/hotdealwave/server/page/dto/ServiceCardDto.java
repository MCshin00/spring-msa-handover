package io.hotdealwave.server.page.dto;

/** Counts for a service card on the home page. */
public record ServiceCardDto(String name, long endpoints, long httpCalls, long publications, long subscriptions, long responded) {}
