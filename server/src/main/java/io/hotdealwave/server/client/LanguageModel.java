package io.hotdealwave.server.client;

import java.util.List;

/**
 * One model call that returns an answer, the cited chunk numbers and whether it abstained; the same with tools, where a call may return tool
 * calls instead; and text embeddings. Tests use a fake.
 */
public interface LanguageModel {
    String name();

    ModelReply reply(String instructions, String input);

    /** One call with the whole input so far; tools are always sent, and allowTools false turns tool choice off. */
    ModelTurn replyWithTools(String instructions, List<ToolDefinition> tools, List<ModelInput> input, boolean allowTools);

    /** One vector per text, in the same order. */
    List<float[]> embed(List<String> texts);
}
