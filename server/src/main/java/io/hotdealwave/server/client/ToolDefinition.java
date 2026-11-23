package io.hotdealwave.server.client;

/** A tool the model may call, with one string argument. */
public record ToolDefinition(String name, String description, String argument, String argumentDescription) {}
