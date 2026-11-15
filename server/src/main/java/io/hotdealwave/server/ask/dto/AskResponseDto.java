package io.hotdealwave.server.ask.dto;

import io.hotdealwave.server.ask.AnswerStatus;
import java.util.List;

public record AskResponseDto(AnswerStatus status, String answer, List<CitedChunkDto> citations,
        String reason, long inputTokens, long outputTokens) {}
