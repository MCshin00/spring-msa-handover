package io.hotdealwave.server.client;

import java.util.List;

/** One model call that returns an answer, the cited chunk numbers and whether it abstained, and text embeddings. Tests use a fake. */
public interface LanguageModel {
    String name();

    ModelReply reply(String instructions, String input);

    /** One vector per text, in the same order. */
    List<float[]> embed(List<String> texts);
}
