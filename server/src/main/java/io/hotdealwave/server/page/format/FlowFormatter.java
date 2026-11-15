package io.hotdealwave.server.page.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.flow.dto.FlowStepDto;
import io.hotdealwave.server.flow.service.FlowService;
import org.springframework.stereotype.Component;

/** Text for flow steps and message topics. */
@Component("flows")
public class FlowFormatter {
    public String topicName(JsonNode message) {
        return FlowService.topicName(message);
    }

    public String stepSummary(FlowStepDto step) {
        return step.from() + " → (" + step.kind() + ") " + targetOrStatus(step) + " " + stepEvidence(step);
    }

    /** Where a step goes, or its status and reason when the target is not confirmed. */
    public String targetOrStatus(FlowStepDto step) {
        return step.to() != null ? step.to() : step.status() + (step.reason() != null ? " — " + step.reason() : "");
    }

    public String stepEvidence(FlowStepDto step) {
        return "[" + String.join(", ", step.evidence()) + "]";
    }
}
