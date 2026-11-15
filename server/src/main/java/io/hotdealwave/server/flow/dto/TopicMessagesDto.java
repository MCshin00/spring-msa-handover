package io.hotdealwave.server.flow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record TopicMessagesDto(String name, List<JsonNode> publications, List<JsonNode> subscriptions) {}
