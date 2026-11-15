package io.hotdealwave.server;

import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** MCP tool bodies over the shared page text and query results. */
@Service
class McpToolService {
    private final Analysis analysis;
    private final Queries queries;
    private final PageService pages;
    private final Search search;
    private final FactFormatter view;
    private final FlowFormatter flows;
    private final Legend legend;

    McpToolService(Analysis analysis, Queries queries, PageService pages, Search search, FactFormatter view, FlowFormatter flows, Legend legend) {
        this.analysis = analysis; this.queries = queries; this.pages = pages; this.search = search; this.view = view; this.flows = flows; this.legend = legend;
    }

    String findSubscribers(String name) { return page(PageKind.TOPIC.path() + name); }

    String page(String path) {
        var text = pages.findText(path);
        var page = pages.page(path);
        return text.map(body -> legend.text(page.kind()) + body).orElse(null);
    }

    String findEndpoint(String query) {
        var found = queries.findEndpoint(query);
        return found.isEmpty() ? null : legend.text(PageKind.ENDPOINT)
                + String.join("\n\n---\n\n", found.stream().map(e -> pages.text(PageKind.ENDPOINT.path() + e.path("id").asText())).toList());
    }

    String traceFlow(String id) {
        if (Stream.of(OutputKey.ENDPOINTS, OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS).allMatch(key -> analysis.find(key, id).isMissingNode())) return null;
        var flow = queries.traceFlow(id);
        var lines = new ArrayList<String>();
        lines.add("나가는 호출과 발행");
        lines.addAll(flow.forward().isEmpty() ? List.of("확인된 호출 없음") : flow.forward().stream().map(flows::step).toList());
        lines.add("부르는 곳");
        lines.addAll(flow.callers().isEmpty() ? List.of("확인된 호출 없음") : flow.callers().stream().map(flows::step).toList());
        var services = new TreeSet<String>();
        for (var step : Stream.concat(flow.forward().stream(), flow.callers().stream()).toList())
            for (var node : Arrays.asList(step.from(), step.to())) if (node != null && node.contains(".")) services.add(node.substring(0, node.indexOf('.')));
        if (services.isEmpty()) services.add(id.substring(0, Math.max(0, id.indexOf('.'))));
        var unsupported = analysis.list(OutputKey.UNSUPPORTED).stream().filter(u -> services.contains(u.path("service").asText())).toList();
        if (!unsupported.isEmpty()) lines.add("도구가 다루지 않는 방식 " + unsupported.size() + "곳: " + String.join(", ", unsupported.stream()
                .map(u -> u.path("service").asText() + " " + u.path("mechanism").asText() + " " + view.evidence(u)).toList())
                + ". 이 흐름에는 그 호출이 빠져 있을 수 있다.");
        return legend.text(Legend.FLOW) + String.join("\n", lines);
    }

    String searchDocs(String query) {
        var results = search.search(query);
        if (results.isEmpty()) return null;
        var text = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            text.append(i + 1).append(". ").append(r.title()).append(r.section().isEmpty() ? "" : " — " + r.section()).append('\n')
                    .append("페이지: ").append(r.page()).append(" · 점수 ").append(r.score()).append('\n')
                    .append(r.preview()).append("\n\n");
        }
        PageKind[] kinds = results.stream().map(r -> pages.page(r.page()).kind()).distinct().toArray(PageKind[]::new);
        return legend.text(kinds) + text.toString().strip();
    }
}
