package io.hotdealwave.server.flow.dto;

import java.util.List;

public record FlowDto(List<FlowStepDto> forward, List<FlowStepDto> callers) {}
