package io.hotdealwave.server.ask.dto;

/** A starting chunk has page, title and section; a tool result has tool and argument, and page and title when it read one page. */
public record DeepCitationDto(String id, String page, String title, String section, String tool, String argument) {}
