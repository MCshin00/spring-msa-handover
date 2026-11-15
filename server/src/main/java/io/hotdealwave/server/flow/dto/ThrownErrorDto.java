package io.hotdealwave.server.flow.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** An exception an endpoint can raise, with its error code and receiving handler when they were extracted. */
public record ThrownErrorDto(JsonNode link, JsonNode errorCode, JsonNode handler) {}
