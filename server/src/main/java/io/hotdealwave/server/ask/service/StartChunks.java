package io.hotdealwave.server.ask.service;

import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.service.PageService;
import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.service.SearchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The numbered chunks both answer paths start from, and the first input text built from them. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class StartChunks {
    /** What the model reads under one number: an endpoint or topic page whole, otherwise the found section. */
    record Chunk(String page, String title, String section, String text) {}

    record Start(Map<String, Chunk> chunks, String input) {}

    private final SearchService searchService;
    private final PageService pageService;

    /** Endpoint and topic pages go whole so fields and connection states come along with whichever section matched. */
    static boolean whole(String page) { return page.startsWith(PageKind.ENDPOINT.getPath()) || page.startsWith(PageKind.TOPIC.getPath()); }

    /** C1 onward from the search results, then the question. With no results the input is the question alone. */
    Start build(List<SearchResultDto> results, String question) {
        var chunks = new LinkedHashMap<String, Chunk>();
        var input = new StringBuilder(results.isEmpty() ? "" : "조각:\n");
        for (var result : results) {
            String id = "C" + (chunks.size() + 1);
            var chunk = whole(result.page())
                    ? new Chunk(result.page(), result.title(), "", pageService.text(result.page()))
                    : new Chunk(result.page(), result.title(), result.section(), searchService.sectionText(result));
            chunks.put(id, chunk);
            input.append('[').append(id).append("] ").append(chunk.title()).append(chunk.section().isEmpty() ? "" : " — " + chunk.section())
                    .append(" (").append(chunk.page()).append(")\n").append(chunk.text()).append("\n\n");
        }
        input.append("질문: ").append(question);
        return new Start(chunks, input.toString());
    }
}
