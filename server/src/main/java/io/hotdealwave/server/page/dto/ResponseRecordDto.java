package io.hotdealwave.server.page.dto;

import java.util.List;

/** A recorded response with its endpoint and original lines. */
public record ResponseRecordDto(String file, String endpoint, List<String> lines) {}
