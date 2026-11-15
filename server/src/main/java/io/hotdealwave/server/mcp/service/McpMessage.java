package io.hotdealwave.server.mcp.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Text returned by MCP when a tool finds no result. */
@Getter
@RequiredArgsConstructor
public enum McpMessage {
    NOT_FOUND("찾지 못함: %s");

    private final String text;

    public String format(Object value) { return text.formatted(value); }
}
