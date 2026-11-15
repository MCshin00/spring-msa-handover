package io.hotdealwave.server.page.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.Facts;
import io.hotdealwave.server.analysis.KnowledgeKind;
import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.common.exception.NotFoundException;
import io.hotdealwave.server.flow.StepKind;
import io.hotdealwave.server.flow.service.FlowService;
import io.hotdealwave.server.page.PageExceptionMessage;
import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.dto.EnvironmentVariableDto;
import io.hotdealwave.server.page.dto.ExternalCallDto;
import io.hotdealwave.server.page.dto.PageDto;
import io.hotdealwave.server.page.dto.PageSectionDto;
import io.hotdealwave.server.page.dto.ResponseRecordDto;
import io.hotdealwave.server.page.format.FactFormatter;
import io.hotdealwave.server.page.render.HtmlDecorator;
import io.hotdealwave.server.page.render.PageRenderer;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Endpoint, topic and service bodies. Pages show them as HTML; search and MCP read the same render as text. */
@Service
@RequiredArgsConstructor
public class PageService {
    /** Gathers what starting the services again needs. Search leaves it out because every part repeats another page. */
    public static final String REVIVAL = PageKind.REVIVAL.getPath();

    private final VerifierOutput verifierOutput;
    private final FlowService flowService;
    private final FactFormatter factFormatter;
    private final WarningService warningService;
    private final PageRenderer pageRenderer;
    private Map<String, List<PageSectionDto>> sections;

    /** The page at a path such as /endpoints/{id}, or null. */
    public PageDto page(String path) {
        var kind = PageKind.at(path);
        if (kind.isEmpty()) return null;
        return switch (kind.get()) {
            case REPOSITORY -> repository();
            case REVIVAL -> revival();
            case ENDPOINT -> endpoint(path.substring(kind.get().getPath().length()));
            case TOPIC -> topic(path.substring(kind.get().getPath().length()));
            case SERVICE -> service(path.substring(kind.get().getPath().length()));
        };
    }

    List<String> topics() {
        return Stream.concat(verifierOutput.list(OutputKey.MESSAGE_PUBLICATIONS).stream(),
                verifierOutput.list(OutputKey.MESSAGE_SUBSCRIPTIONS).stream())
                .map(FlowService::topicName).filter(Objects::nonNull).distinct().sorted().toList();
    }

    List<String> services() {
        return verifierOutput.list(OutputKey.SERVICES).stream().map(service -> Facts.valueOf(service.path("name"))).toList();
    }

    /** Sections of every page, rendered once. */
    public synchronized Map<String, List<PageSectionDto>> sections() {
        if (sections == null) {
            var paths = paths();
            var all = new LinkedHashMap<String, List<PageSectionDto>>();
            for (var path : paths) all.put(path, pageRenderer.renderSections(page(path)));
            sections = all;
        }
        return sections;
    }

    /** The whole body of a page as text, or null. */
    public String text(String path) {
        var page = sections().get(path);
        if (page == null) return null;
        return String.join("\n\n", page.stream().map(section -> section.heading().isEmpty() ? section.text() : section.heading() + "\n"
                + section.text()).toList());
    }

    /** The page as HTML for the browser, with display-only markup, or null. */
    String html(String path) {
        var page = page(path);
        if (page == null) return null;
        return pageRenderer.html(page);
    }

    /** Requires a browser page while keeping absent tool results optional. */
    public String requireHtml(String path) {
        String html = html(path);
        if (html == null) throw new NotFoundException(PageExceptionMessage.PAGE_NOT_FOUND);
        return html;
    }

    /** The whole body when the requested page exists. */
    public Optional<String> findText(String path) { return Optional.ofNullable(text(path)); }

    /** Every renderable path in its original indexing order. */
    List<String> paths() {
        return Stream.of(
                verifierOutput.list(OutputKey.ENDPOINTS).stream().map(endpoint -> PageKind.ENDPOINT.getPath() + endpoint.path("id").asText()),
                topics().stream().map(topic -> PageKind.TOPIC.getPath() + topic),
                services().stream().map(service -> PageKind.SERVICE.getPath() + service),
                Stream.of(PageKind.REPOSITORY.getPath()).filter(path -> page(path) != null), Stream.of(REVIVAL)).flatMap(paths -> paths).toList();
    }

    /** A recorded response file with numbered lines, which response evidence links to. */
    public ResponseRecordDto findResponseRecord(String file) {
        var responseEndpoint = verifierOutput.list(OutputKey.ENDPOINTS).stream()
                .filter(endpoint -> {
                    var matcher = HtmlDecorator.RESPONSE_FILE.matcher(endpoint.path("responseCheck").path("file").asText());
                    return matcher.matches() && matcher.group(1).equals(file);
                }).findFirst()
                .orElseThrow(() -> new NotFoundException(PageExceptionMessage.RESPONSE_NOT_FOUND));
        return new ResponseRecordDto(file, responseEndpoint.path("id").asText(),
                Facts.items(responseEndpoint.path("responseCheck").path("lines")).stream().map(line -> line.asText()).toList());
    }

    private PageDto endpoint(String id) {
        var endpoint = verifierOutput.find(OutputKey.ENDPOINTS, id);
        if (endpoint.isMissingNode()) return null;
        return new PageDto(PageKind.ENDPOINT.getPath() + id, PageKind.ENDPOINT.getTemplate(), Map.of("endpoint", endpoint, "flow",
                flowService.traceFlow(id),
                "items", flowService.knowledgeFor(List.of(id)), "descriptions", flowService.descriptionsFor(List.of(id)),
                "errors", flowService.errorsFor(id), "rejections", verifierOutput.list(OutputKey.GATEWAY_REJECTIONS)), PageKind.ENDPOINT,
                        Set.of(endpoint.path("service").asText()));
    }

    private PageDto topic(String name) {
        var topic = flowService.findSubscribers(name);
        if (topic.publications().isEmpty() && topic.subscriptions().isEmpty()) return null;
        var nodes = Stream.concat(topic.publications().stream(), topic.subscriptions().stream()).toList();
        var links = topic.publications().stream()
                .flatMap(publication -> flowService.traceFlow(publication.path("id").asText()).forward().stream().filter(step -> step.from()
                        .equals(publication.path("id").asText())))
                .filter(step -> step.kind().equals(StepKind.SUBSCRIPTION.getText())).toList();
        var ids = nodes.stream().map(item -> item.path("id").asText()).toList();
        return new PageDto(PageKind.TOPIC.getPath() + name, PageKind.TOPIC.getTemplate(), Map.of("topic", topic, "links", links,
                "unpublished", topic.subscriptions().stream().map(flowService::warning).filter(Objects::nonNull).toList(),
                "items", flowService.knowledgeFor(ids), "descriptions", flowService.descriptionsFor(ids)),
                PageKind.TOPIC, new TreeSet<>(nodes.stream().map(item -> item.path("service").asText()).toList()));
    }

    private PageDto service(String name) {
        var service = verifierOutput.list(OutputKey.SERVICES).stream()
                .filter(serviceEntry -> name.equals(Facts.valueOf(serviceEntry.path("name")))).findFirst().orElse(null);
        if (service == null) return null;
        var model = new HashMap<String, Object>(Map.of("service", service, "name", name));
        var ids = new ArrayList<String>();
        for (var key : List.of(OutputKey.ENDPOINTS, OutputKey.HTTP_CALLS, OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS,
                OutputKey.ENVIRONMENT_VARIABLES, OutputKey.EXTERNAL_RESOURCES,
                OutputKey.ERROR_CODES, OutputKey.EXCEPTION_HANDLERS, OutputKey.GATEWAY_REJECTIONS, OutputKey.PAGE_SERIALIZATION,
                        OutputKey.UNSUPPORTED)) {
            var owned = verifierOutput.list(key).stream().filter(item -> item.path("service").asText().equals(name)).toList();
            model.put(key.getText(), owned);
            owned.forEach(item -> {
                if (item.has("id")) ids.add(item.path("id").asText());
            });
        }
        model.put("items", flowService.knowledgeFor(ids));
        return new PageDto(PageKind.SERVICE.getPath() + name, PageKind.SERVICE.getTemplate(), model, PageKind.SERVICE, Set.of(name));
    }

    private PageDto repository() {
        var repository = verifierOutput.repositoryFacts();
        if (repository.isMissingNode()) return null;
        return new PageDto(PageKind.REPOSITORY.getPath(), PageKind.REPOSITORY.getTemplate(), Map.of("repository", repository),
                PageKind.REPOSITORY, Set.of());
    }

    private PageDto revival() {
        var variables = new LinkedHashMap<String, List<JsonNode>>();
        verifierOutput.list(OutputKey.ENVIRONMENT_VARIABLES).stream().filter(variable -> !Facts.isConfirmed(variable.path("value")))
                .forEach(variable -> variables.computeIfAbsent(variable.path("name").asText(), item -> new ArrayList<>()).add(variable));
        var warnings = warningService.groups();
        var repository = verifierOutput.repositoryFacts();
        return new PageDto(REVIVAL, PageKind.REVIVAL.getTemplate(), Map.of(
                "resources", verifierOutput.list(OutputKey.EXTERNAL_RESOURCES),
                "externalCalls", externalCalls(),
                "variables", variables.entrySet().stream().map(entry -> variable(entry.getKey(), entry.getValue())).toList(),
                "secrets", Facts.items(repository.path("pipelines")).stream().flatMap(pipeline -> Facts.items(pipeline.path("secrets"))
                        .stream()).toList(),
                "containers", Facts.items(repository.path("containers")),
                "warnings", warnings,
                "traps", verifierOutput.list(OutputKey.KNOWLEDGE).stream().filter(item -> item.path("kind").asText()
                        .equals(KnowledgeKind.TRAP.getText())).toList(),
                "unsupported", verifierOutput.list(OutputKey.UNSUPPORTED)), PageKind.REVIVAL, Set.of());
    }

    private List<ExternalCallDto> externalCalls() {
        return verifierOutput.list(OutputKey.HTTP_CALLS).stream().filter(httpCall -> httpCall.path("external").asBoolean()).map(httpCall -> {
            var client = verifierOutput.find(OutputKey.HTTP_CLIENTS, httpCall.path("clientId").asText());
            var address = client.isMissingNode() ? httpCall.path("target") : client.path("url");
            return new ExternalCallDto(httpCall.path("service").asText(), httpCall.path("id").asText(), httpCall.path("kind").asText(),
                    factFormatter.valueWithEvidence(address), factFormatter.valueWithEvidence(httpCall.path("path")));
        }).toList();
    }

    private EnvironmentVariableDto variable(String name, List<JsonNode> uses) {
        return new EnvironmentVariableDto(name, distinct(uses, variable -> factFormatter.statusWithReason(variable.path("value"))),
                distinct(uses, variable -> variable.path("service").asText()),
                distinct(uses, variable -> variable.path("profile").asText()), distinct(uses, variable -> variable.path("location").asText()),
                "[" + distinct(uses.stream().flatMap(variable -> Facts.items(variable.path("value").path("evidence")).stream()).toList(),
                        JsonNode::asText) + "]");
    }

    private static String distinct(List<JsonNode> nodes, java.util.function.Function<JsonNode, String> part) {
        return String.join(", ", nodes.stream().map(part).distinct().toList());
    }

}
