package io.hotdealwave.server.ask.dto;

/** One tool call the model made and what it came to: a number, or one of the step marks. */
public record ToolStepDto(String tool, String argument, String mark) {}
