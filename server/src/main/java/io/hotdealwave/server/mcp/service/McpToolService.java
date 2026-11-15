package io.hotdealwave.server.mcp.service;

import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.flow.service.FlowService;
import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.format.FactFormatter;
import io.hotdealwave.server.page.format.FlowFormatter;
import io.hotdealwave.server.page.render.ConfirmedLegend;
import io.hotdealwave.server.page.service.PageService;
import io.hotdealwave.server.search.service.SearchService;
import java.util.*;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** MCP tool bodies over the shared page text and query results. */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class McpToolService {
    private final VerifierOutput verifierOutput;
    private final FlowService flowService;
    private final PageService pageService;
    private final SearchService searchService;
    private final FactFormatter factFormatter;
    private final FlowFormatter flowFormatter;
    private final ConfirmedLegend confirmedLegend;

    public String findSubscribers(String name) { return page(PageKind.TOPIC.getPath() + name); }

    public String page(String path) {
        var text = pageService.findText(path);
        var page = pageService.page(path);
        return text.map(body -> confirmedLegend.text(page.kind()) + body).orElse(null);
    }

    public String findEndpoint(String query) {
        var found = flowService.findEndpoint(query);
        return found.isEmpty() ? null : confirmedLegend.text(PageKind.ENDPOINT)
                + String.join("\n\n---\n\n", found.stream().map(endpoint -> pageService.text(PageKind.ENDPOINT.getPath()
                        + endpoint.path("id").asText())).toList());
    }

    public String traceFlow(String id) {
        if (Stream.of(OutputKey.ENDPOINTS, OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS)
                .allMatch(key -> verifierOutput.find(key, id).isMissingNode())) return null;
        var flow = flowService.traceFlow(id);
        var lines = new ArrayList<String>();
        lines.add("나가는 호출과 발행");
        lines.addAll(flow.forward().isEmpty() ? List.of("확인된 호출 없음") : flow.forward().stream().map(flowFormatter::stepSummary).toList());
        lines.add("부르는 곳");
        lines.addAll(flow.callers().isEmpty() ? List.of("확인된 호출 없음") : flow.callers().stream().map(flowFormatter::stepSummary).toList());
        var services = new TreeSet<String>();
        for (var step : Stream.concat(flow.forward().stream(), flow.callers().stream()).toList())
            for (var node : Arrays.asList(step.from(), step.to())) if (node != null && node.contains(".")) services.add(node.substring(0,
                    node.indexOf('.')));
        if (services.isEmpty()) services.add(id.substring(0, Math.max(0, id.indexOf('.'))));
        var unsupported = verifierOutput.list(OutputKey.UNSUPPORTED).stream()
                .filter(unsupportedItem -> services.contains(unsupportedItem.path("service").asText())).toList();
        if (!unsupported.isEmpty()) lines.add("도구가 다루지 않는 방식 " + unsupported.size() + "곳: " + String.join(", ", unsupported.stream()
                .map(unsupportedItem -> unsupportedItem.path("service").asText() + " " + unsupportedItem.path("mechanism").asText() + " "
                        + factFormatter.evidence(unsupportedItem)).toList())
                + ". 이 흐름에는 그 호출이 빠져 있을 수 있다.");
        return confirmedLegend.text(ConfirmedLegend.FLOW) + String.join("\n", lines);
    }

    public String searchDocs(String query) {
        var results = searchService.search(query);
        if (results.isEmpty()) return null;
        var text = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            text.append(i + 1).append(". ").append(result.title()).append(result.section().isEmpty() ? "" : " — " + result.section()).append('\n')
                    .append("페이지: ").append(result.page()).append(" · 점수 ").append(result.score()).append('\n')
                    .append(result.preview()).append("\n\n");
        }
        PageKind[] kinds = results.stream().map(result -> pageService.page(result.page()).kind()).distinct().toArray(PageKind[]::new);
        return confirmedLegend.text(kinds) + text.toString().strip();
    }
}
