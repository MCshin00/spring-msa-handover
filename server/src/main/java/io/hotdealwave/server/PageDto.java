package io.hotdealwave.server;

import java.util.Map;
import java.util.Set;

/** A page model with its path, template, kind and owning services. */
public record PageDto(String path, String template, Map<String, Object> model, PageKind kind, Set<String> services) {}
