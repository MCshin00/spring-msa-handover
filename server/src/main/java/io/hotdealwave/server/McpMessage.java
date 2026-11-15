package io.hotdealwave.server;

/** Text returned by MCP when a tool finds no result. */
enum McpMessage {
    NOT_FOUND("찾지 못함: %s");

    private final String text;

    McpMessage(String text) { this.text = text; }

    public String text() { return text; }

    String format(Object value) { return text.formatted(value); }
}
