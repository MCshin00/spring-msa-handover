package io.hotdealwave.server.client;

import java.util.List;

/** One tool-using call: the tool calls to run, or the final reply when there are none. */
public record ModelTurn(List<ModelInput.ToolCall> toolCalls, ModelReply reply, long inputTokens, long cachedInputTokens, long outputTokens) {}
