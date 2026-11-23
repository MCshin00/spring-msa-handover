package io.hotdealwave.server.ask.dto;

import io.hotdealwave.server.ask.AnswerStatus;
import java.util.List;

/** The deep path's answer with the steps it took. Tokens are summed over all model calls. */
public record DeepAskResponseDto(AnswerStatus status, String answer, List<DeepCitationDto> citations, String reason, List<ToolStepDto> steps,
        int modelCalls, int toolCalls, boolean limitReached, long inputTokens, long cachedInputTokens, long outputTokens, long elapsedMillis) {}
