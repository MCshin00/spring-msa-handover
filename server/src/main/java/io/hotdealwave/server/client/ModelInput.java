package io.hotdealwave.server.client;

/** One item of a tool-using call's input: the first text, a tool call the model made, or the result sent back for it. */
public sealed interface ModelInput {
    record Text(String text) implements ModelInput {}

    /** Arguments are the JSON text the model wrote. */
    record ToolCall(String callId, String tool, String arguments) implements ModelInput {}

    record ToolResult(String callId, String output) implements ModelInput {}
}
