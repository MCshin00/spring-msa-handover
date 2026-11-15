package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** Text for flow steps and message topics. */
@Component("flows")
public class FlowFormatter {
    public String topic(JsonNode kafka) {
        return Queries.topicName(kafka);
    }

    public String step(Queries.Step step) {
        return step.from() + " → (" + step.kind() + ") " + target(step) + " " + stepEvidence(step);
    }

    /** Where a step goes, or its status and reason when the target is not confirmed. */
    public String target(Queries.Step step) {
        return step.to() != null ? step.to() : step.status() + (step.reason() != null ? " — " + step.reason() : "");
    }

    public String stepEvidence(Queries.Step step) {
        return "[" + String.join(", ", step.evidence()) + "]";
    }
}
