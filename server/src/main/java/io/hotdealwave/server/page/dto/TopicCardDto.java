package io.hotdealwave.server.page.dto;

/** Publication and subscription counts for a topic card. */
public record TopicCardDto(String name, int publications, int subscriptions) {}
