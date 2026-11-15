package io.hotdealwave.server;

import java.util.List;

/** A recorded response with its endpoint and original lines. */
public record ResponseRecordDto(String file, String endpoint, List<String> lines) {}
