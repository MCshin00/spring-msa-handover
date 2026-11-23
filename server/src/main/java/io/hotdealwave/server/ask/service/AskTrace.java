package io.hotdealwave.server.ask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hotdealwave.server.ask.AnswerStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One JSON line per deep request in ask-trace.jsonl next to the analysis output, including the model's last output the check blocked. */
@Component
class AskTrace {
    record ModelCall(long inputTokens, long cachedInputTokens, long outputTokens, long elapsedMillis) {}

    record ToolCall(String tool, String argument, String mark, int chars) {}

    record Output(String answer, List<String> citations, boolean abstain) {}

    record Line(String time, String question, AnswerStatus status, String reason, boolean limitReached, long elapsedMillis,
            List<ModelCall> modelCalls, List<ToolCall> toolCalls, Output lastOutput) {}

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    AskTrace(@Value("${analysis.path}") String analysisPath, @Value("${ask-trace.path:}") String path) {
        this.file = path.isBlank() ? Path.of(analysisPath).resolveSibling("ask-trace.jsonl") : Path.of(path);
    }

    synchronized void append(Line line) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, json.writeValueAsString(line) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
