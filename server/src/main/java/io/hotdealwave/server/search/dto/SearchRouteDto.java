package io.hotdealwave.server.search.dto;

import io.hotdealwave.server.page.PageKind;

public record SearchRouteDto(String service, PageKind kind) {}
