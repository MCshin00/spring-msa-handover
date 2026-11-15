package io.hotdealwave.server.page.dto;

import io.hotdealwave.server.page.PageKind;
import java.util.Set;

/** One h2 section of a body as text. The part above the first h2 has an empty heading. */
public record PageSectionDto(String page, String title, String heading, String text, PageKind kind, Set<String> services) {}
