package io.hotdealwave.server.flow.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.FactStatus;
import io.hotdealwave.server.analysis.Facts;
import io.hotdealwave.server.analysis.KnowledgeKind;
import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.analysis.WarningCheck;
import io.hotdealwave.server.flow.StepKind;
import io.hotdealwave.server.flow.dto.FlowDto;
import io.hotdealwave.server.flow.dto.FlowStepDto;
import io.hotdealwave.server.flow.dto.ThrownErrorDto;
import io.hotdealwave.server.flow.dto.TopicMessagesDto;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static io.hotdealwave.server.analysis.Facts.isConfirmed;
import static io.hotdealwave.server.analysis.Facts.valueOf;

/** Relation lookups shared by pages, APIs and MCP. Never calls a model. */
@Component
@RequiredArgsConstructor
public class FlowService {
    private final VerifierOutput verifierOutput;

    public List<JsonNode> findEndpoint(String query) {
        String normalizedQuery = query.trim();
        return verifierOutput.list(OutputKey.ENDPOINTS).stream().filter(endpoint -> endpoint.path("id").asText().equals(normalizedQuery)
                || normalizedQuery.equalsIgnoreCase(valueOf(endpoint.path("httpMethod")) + " " + valueOf(endpoint.path("path")))
                || normalizedQuery.equals(valueOf(endpoint.path("path"))) || normalizedQuery.equals(endpoint.path("handler").asText())).toList();
    }

    public FlowDto traceFlow(String id) {
        var forward = new ArrayList<FlowStepDto>();
        var queue = new ArrayDeque<>(List.of(id));
        var seen = new HashSet<>(queue);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            var next = new ArrayList<FlowStepDto>();
            for (var call : verifierOutput.list(OutputKey.CALLS))
                if (call.path("fromId").asText().equals(current)) next.add(step(current, call.path("toId").asText(), StepKind.CALL.getText(), call));
            var httpCall = verifierOutput.find(OutputKey.HTTP_CALLS, current);
            if (!httpCall.isMissingNode()) next.add(step(current, valueOf(httpCall.path("target")), StepKind.target(httpCall.path("kind")
                    .asText()), httpCall.path("target")));
            var publication = verifierOutput.find(OutputKey.MESSAGE_PUBLICATIONS, current);
            if (!publication.isMissingNode()) next.addAll(subscribers(publication));
            for (var step : next) {
                forward.add(step);
                if (step.to() != null && confirmed(step) && seen.add(step.to())) queue.add(step.to());
            }
        }
        return new FlowDto(forward, callers(id));
    }

    public TopicMessagesDto findSubscribers(String topic) {
        var publications = verifierOutput.list(OutputKey.MESSAGE_PUBLICATIONS);
        var subscriptions = verifierOutput.list(OutputKey.MESSAGE_SUBSCRIPTIONS);
        var matched = Stream.concat(publications.stream(), subscriptions.stream()).filter(message -> topic.equals(topicName(message)))
                .map(message -> message.path("id").asText()).collect(java.util.stream.Collectors.toSet());
        var related = new HashSet<>(matched);
        for (var link : verifierOutput.list(OutputKey.MESSAGE_LINKS))
            if (isConfirmed(link) && (matched.contains(link.path("publicationId").asText()) || matched.contains(link.path("subscriptionId")
                    .asText()))) {
                related.add(link.path("publicationId").asText());
                related.add(link.path("subscriptionId").asText());
            }
        return new TopicMessagesDto(topic, publications.stream().filter(message -> related.contains(message.path("id").asText())).toList(),
                subscriptions.stream().filter(message -> related.contains(message.path("id").asText())).toList());
    }

    /** Environment variable name, or the literal topic for the string DLQ topics. */
    public static String topicName(JsonNode message) {
        String environment = isConfirmed(message.path("environment")) ? valueOf(message.path("environment")) : null;
        if (environment != null && !environment.isEmpty()) return environment;
        return isConfirmed(message.path("topicName")) ? valueOf(message.path("topicName")) : null;
    }

    /** Rules and traps whose affected IDs include any of the given IDs, traps first. */
    public List<JsonNode> knowledgeFor(Collection<String> ids) {
        return affecting(ids).filter(item -> !KnowledgeKind.ABOUT.getText().equals(item.path("kind").asText()))
                .sorted(Comparator.comparing(item -> !KnowledgeKind.TRAP.getText().equals(item.path("kind").asText()))).toList();
    }

    /** Descriptions of the given IDs. */
    public List<JsonNode> descriptionsFor(Collection<String> ids) {
        return affecting(ids).filter(item -> KnowledgeKind.ABOUT.getText().equals(item.path("kind").asText())).toList();
    }

    private Stream<JsonNode> affecting(Collection<String> ids) {
        return verifierOutput.list(OutputKey.KNOWLEDGE).stream()
                .filter(item -> Facts.items(item.path("affectedIds")).stream().anyMatch(affectedId -> ids.contains(affectedId.asText())));
    }

    /** message-destinations warning reason for an unlinked publication or subscription, if any. */
    public String warning(JsonNode message) {
        return verifierOutput.list(OutputKey.WARNINGS).stream()
                .filter(warning -> warning.path("check").asText().equals(WarningCheck.MESSAGE_DESTINATIONS.getKey())
                        && warning.path("evidence").equals(message.path("evidence")))
                .map(warning -> warning.path("reason").asText()).findFirst().orElse(null);
    }

    private List<FlowStepDto> subscribers(JsonNode publication) {
        String id = publication.path("id").asText();
        var steps = verifierOutput.list(OutputKey.MESSAGE_LINKS).stream().filter(link -> link.path("publicationId").asText().equals(id))
                .map(link -> step(id, link.path("subscriptionId").asText(), StepKind.SUBSCRIPTION.getText(), link)).toList();
        if (steps.stream().anyMatch(this::confirmed)) return steps;
        var unlinked = new ArrayList<>(steps);
        unlinked.add(new FlowStepDto(id, null, StepKind.SUBSCRIPTION.getText(), FactStatus.UNCONFIRMED.getText(), warning(publication),
                texts(publication.path("evidence"))));
        return unlinked;
    }

    private List<FlowStepDto> callers(String id) {
        var steps = new ArrayList<FlowStepDto>();
        for (var httpCall : verifierOutput.list(OutputKey.HTTP_CALLS))
            if (id.equals(valueOf(httpCall.path("target")))) steps.add(step(httpCall.path("id").asText(), id,
                    StepKind.target(httpCall.path("kind").asText()), httpCall.path("target")));
        for (var link : verifierOutput.list(OutputKey.MESSAGE_LINKS))
            if (link.path("subscriptionId").asText().equals(id)) steps.add(step(link.path("publicationId").asText(), id,
                    StepKind.SUBSCRIPTION.getText(), link));
        var targets = steps.stream().map(FlowStepDto::from).toList();
        for (var call : verifierOutput.list(OutputKey.CALLS))
            if (targets.contains(call.path("toId").asText()))
                steps.add(step(call.path("fromId").asText(), call.path("toId").asText(), StepKind.CALL.getText(), call));
        return steps;
    }

    /** Exceptions the endpoint's handler reaches, in extraction order. */
    public List<ThrownErrorDto> errorsFor(String endpointId) {
        String service = verifierOutput.find(OutputKey.ENDPOINTS, endpointId).path("service").asText();
        return verifierOutput.list(OutputKey.THROWS).stream().filter(thrown -> thrown.path("fromId").asText().equals(endpointId))
                .map(thrown -> new ThrownErrorDto(thrown,
                verifierOutput.list(OutputKey.ERROR_CODES).stream().filter(errorCode -> errorCode.path("service").asText().equals(service)
                        && errorCode.path("name").asText().equals(thrown.path("errorCode").asText())).findFirst()
                                .orElse(com.fasterxml.jackson.databind.node.MissingNode.getInstance()),
                verifierOutput.find(OutputKey.EXCEPTION_HANDLERS, thrown.path("handlerId").asText()))).toList();
    }

    private boolean confirmed(FlowStepDto step) { return FactStatus.CONFIRMED.getText().equals(step.status()); }

    private static FlowStepDto step(String from, String to, String kind, JsonNode checked) {
        String reason = checked.path("reason").isTextual() ? checked.path("reason").asText() : null;
        return new FlowStepDto(from, isConfirmed(checked) ? to : null, kind, checked.path("status").asText(), reason,
                texts(checked.path("evidence")));
    }

    private static List<String> texts(JsonNode array) {
        return Facts.items(array).stream().map(JsonNode::asText).toList();
    }
}
