package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Endpoint, topic and service bodies. Pages show them as HTML; search and MCP read the same render as text. */
@Service
class PageService {
    /** Gathers what starting the services again needs. Search leaves it out because every part repeats another page. */
    static final String REVIVAL = PageKind.REVIVAL.path();

    private final Analysis analysis;
    private final Queries queries;
    private final FactFormatter view;
    private final WarningService warningService;
    private final PageRenderer renderer;
    private Map<String, List<PageSectionDto>> sections;

    PageService(Analysis analysis, Queries queries, FactFormatter view, WarningService warningService, PageRenderer renderer) {
        this.analysis = analysis; this.queries = queries; this.view = view; this.warningService = warningService; this.renderer = renderer;
    }

    /** The page at a path such as /endpoints/{id}, or null. */
    PageDto page(String path) {
        var kind = PageKind.at(path);
        if (kind.isEmpty()) return null;
        return switch (kind.get()) {
            case REPOSITORY -> repository();
            case REVIVAL -> revival();
            case ENDPOINT -> endpoint(path.substring(kind.get().path().length()));
            case TOPIC -> topic(path.substring(kind.get().path().length()));
            case SERVICE -> service(path.substring(kind.get().path().length()));
        };
    }

    List<String> topics() {
        return Stream.concat(analysis.list(OutputKey.MESSAGE_PUBLICATIONS).stream(), analysis.list(OutputKey.MESSAGE_SUBSCRIPTIONS).stream())
                .map(Queries::topicName).filter(Objects::nonNull).distinct().sorted().toList();
    }

    List<String> services() {
        return analysis.list(OutputKey.SERVICES).stream().map(s -> Analysis.value(s.path("name"))).toList();
    }

    /** Sections of every page, rendered once. */
    synchronized Map<String, List<PageSectionDto>> sections() {
        if (sections == null) {
            var paths = paths();
            var all = new LinkedHashMap<String, List<PageSectionDto>>();
            for (var path : paths) all.put(path, renderer.render(page(path)));
            sections = all;
        }
        return sections;
    }

    /** The whole body of a page as text, or null. */
    String text(String path) {
        var page = sections().get(path);
        if (page == null) return null;
        return String.join("\n\n", page.stream().map(s -> s.heading().isEmpty() ? s.text() : s.heading() + "\n" + s.text()).toList());
    }

    /** The page as HTML for the browser, with display-only markup, or null. */
    String html(String path) {
        var page = page(path);
        if (page == null) return null;
        return renderer.html(page);
    }

    /** Requires a browser page while keeping absent tool results optional. */
    String requireHtml(String path) {
        String html = html(path);
        if (html == null) throw new NotFoundException(PageExceptionMessage.PAGE_NOT_FOUND);
        return html;
    }

    /** The whole body when the requested page exists. */
    Optional<String> findText(String path) { return Optional.ofNullable(text(path)); }

    /** Every renderable path in its original indexing order. */
    List<String> paths() {
        return Stream.of(
                analysis.list(OutputKey.ENDPOINTS).stream().map(e -> PageKind.ENDPOINT.path() + e.path("id").asText()),
                topics().stream().map(t -> PageKind.TOPIC.path() + t),
                services().stream().map(s -> PageKind.SERVICE.path() + s),
                Stream.of(PageKind.REPOSITORY.path()).filter(p -> page(p) != null), Stream.of(REVIVAL)).flatMap(s -> s).toList();
    }

    /** A recorded response file with numbered lines, which response evidence links to. */
    ResponseRecordDto findResponseRecord(String file) {
        var endpoint = analysis.list(OutputKey.ENDPOINTS).stream()
                .filter(e -> { var m = Decor.RESPONSE_FILE.matcher(e.path("responseCheck").path("file").asText()); return m.matches() && m.group(1).equals(file); }).findFirst()
                .orElseThrow(() -> new NotFoundException(PageExceptionMessage.RESPONSE_NOT_FOUND));
        return new ResponseRecordDto(file, endpoint.path("id").asText(),
                Analysis.listOf(endpoint.path("responseCheck").path("lines")).stream().map(l -> l.asText()).toList());
    }

    private PageDto endpoint(String id) {
        var endpoint = analysis.find(OutputKey.ENDPOINTS, id);
        if (endpoint.isMissingNode()) return null;
        return new PageDto(PageKind.ENDPOINT.path() + id, PageKind.ENDPOINT.template(), Map.of("endpoint", endpoint, "flow", queries.traceFlow(id),
                "items", queries.knowledgeFor(List.of(id)), "descriptions", queries.descriptionsFor(List.of(id)),
                "errors", queries.errorsFor(id), "rejections", analysis.list(OutputKey.GATEWAY_REJECTIONS)), PageKind.ENDPOINT, Set.of(endpoint.path("service").asText()));
    }

    private PageDto topic(String name) {
        var topic = queries.findSubscribers(name);
        if (topic.publications().isEmpty() && topic.subscriptions().isEmpty()) return null;
        var nodes = Stream.concat(topic.publications().stream(), topic.subscriptions().stream()).toList();
        var links = topic.publications().stream()
                .flatMap(p -> queries.traceFlow(p.path("id").asText()).forward().stream().filter(s -> s.from().equals(p.path("id").asText())))
                .filter(s -> s.kind().equals(StepKind.SUBSCRIPTION.text())).toList();
        var ids = nodes.stream().map(n -> n.path("id").asText()).toList();
        return new PageDto(PageKind.TOPIC.path() + name, PageKind.TOPIC.template(), Map.of("topic", topic, "links", links,
                "unpublished", topic.subscriptions().stream().map(queries::warning).filter(Objects::nonNull).toList(),
                "items", queries.knowledgeFor(ids), "descriptions", queries.descriptionsFor(ids)),
                PageKind.TOPIC, new TreeSet<>(nodes.stream().map(n -> n.path("service").asText()).toList()));
    }

    private PageDto service(String name) {
        var service = analysis.list(OutputKey.SERVICES).stream().filter(s -> name.equals(Analysis.value(s.path("name")))).findFirst().orElse(null);
        if (service == null) return null;
        var model = new HashMap<String, Object>(Map.of("service", service, "name", name));
        var ids = new ArrayList<String>();
        for (var key : List.of(OutputKey.ENDPOINTS, OutputKey.HTTP_CALLS, OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS, OutputKey.ENVIRONMENT_VARIABLES, OutputKey.EXTERNAL_RESOURCES,
                OutputKey.ERROR_CODES, OutputKey.EXCEPTION_HANDLERS, OutputKey.GATEWAY_REJECTIONS, OutputKey.PAGE_SERIALIZATION, OutputKey.UNSUPPORTED)) {
            var owned = analysis.list(key).stream().filter(n -> n.path("service").asText().equals(name)).toList();
            model.put(key.text(), owned);
            owned.forEach(n -> { if (n.has("id")) ids.add(n.path("id").asText()); });
        }
        model.put("items", queries.knowledgeFor(ids));
        return new PageDto(PageKind.SERVICE.path() + name, PageKind.SERVICE.template(), model, PageKind.SERVICE, Set.of(name));
    }

    private PageDto repository() {
        var repository = analysis.repository();
        if (repository.isMissingNode()) return null;
        return new PageDto(PageKind.REPOSITORY.path(), PageKind.REPOSITORY.template(), Map.of("repository", repository), PageKind.REPOSITORY, Set.of());
    }

    private PageDto revival() {
        var variables = new LinkedHashMap<String, List<JsonNode>>();
        analysis.list(OutputKey.ENVIRONMENT_VARIABLES).stream().filter(v -> !Analysis.known(v.path("value")))
                .forEach(v -> variables.computeIfAbsent(v.path("name").asText(), k -> new ArrayList<>()).add(v));
        var warnings = warningService.groups();
        var repository = analysis.repository();
        return new PageDto(REVIVAL, PageKind.REVIVAL.template(), Map.of(
                "resources", analysis.list(OutputKey.EXTERNAL_RESOURCES),
                "externalCalls", externalCalls(),
                "variables", variables.entrySet().stream().map(e -> variable(e.getKey(), e.getValue())).toList(),
                "secrets", Analysis.listOf(repository.path("pipelines")).stream().flatMap(p -> Analysis.listOf(p.path("secrets")).stream()).toList(),
                "containers", Analysis.listOf(repository.path("containers")),
                "warnings", warnings,
                "traps", analysis.list(OutputKey.KNOWLEDGE).stream().filter(k -> k.path("kind").asText().equals(KnowledgeKind.TRAP.text())).toList(),
                "unsupported", analysis.list(OutputKey.UNSUPPORTED)), PageKind.REVIVAL, Set.of());
    }

    private List<ExternalCallDto> externalCalls() {
        return analysis.list(OutputKey.HTTP_CALLS).stream().filter(c -> c.path("external").asBoolean()).map(c -> {
            var client = analysis.find(OutputKey.HTTP_CLIENTS, c.path("clientId").asText());
            var address = client.isMissingNode() ? c.path("target") : client.path("url");
            return new ExternalCallDto(c.path("service").asText(), c.path("id").asText(), c.path("kind").asText(), view.fact(address), view.fact(c.path("path")));
        }).toList();
    }

    private EnvironmentVariableDto variable(String name, List<JsonNode> uses) {
        return new EnvironmentVariableDto(name, distinct(uses, v -> view.status(v.path("value"))), distinct(uses, v -> v.path("service").asText()),
                distinct(uses, v -> v.path("profile").asText()), distinct(uses, v -> v.path("location").asText()),
                "[" + distinct(uses.stream().flatMap(v -> Analysis.listOf(v.path("value").path("evidence")).stream()).toList(), JsonNode::asText) + "]");
    }

    private static String distinct(List<JsonNode> nodes, java.util.function.Function<JsonNode, String> part) {
        return String.join(", ", nodes.stream().map(part).distinct().toList());
    }

}
