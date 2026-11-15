package io.hotdealwave.server;

import java.util.Map;
import org.springframework.stereotype.Service;

/** Builds the home page cards, counts and shared warning groups. */
@Service
class HomeService {
    private final Analysis analysis;
    private final PageService pages;
    private final Queries queries;
    private final WarningService warningService;
    private final PageRenderer renderer;

    HomeService(Analysis analysis, PageService pages, Queries queries, WarningService warningService, PageRenderer renderer) {
        this.analysis = analysis; this.pages = pages; this.queries = queries; this.warningService = warningService; this.renderer = renderer;
    }

    String index() {
        var cards = pages.services().stream().map(name -> new ServiceCardDto(name,
                count(OutputKey.ENDPOINTS, name), count(OutputKey.HTTP_CALLS, name), count(OutputKey.MESSAGE_PUBLICATIONS, name), count(OutputKey.MESSAGE_SUBSCRIPTIONS, name),
                analysis.list(OutputKey.ENDPOINTS).stream().filter(e -> e.path("service").asText().equals(name))
                        .filter(e -> ResponseCheckStatus.RECORDED.text().equals(e.path("responseCheck").path("status").asText())).count())).toList();
        var topics = pages.topics().stream().map(name -> {
            var topic = queries.findSubscribers(name);
            return new TopicCardDto(name, topic.publications().size(), topic.subscriptions().size());
        }).toList();
        var gaps = warningService.groups();
        return renderer.html("index", Map.of("cards", cards, "topics", topics, "gaps", gaps, "gapCount", analysis.list(OutputKey.WARNINGS).size(),
                "endpointCount", analysis.list(OutputKey.ENDPOINTS).size(), "responded", cards.stream().mapToLong(ServiceCardDto::responded).sum()));
    }

    private long count(OutputKey key, String service) {
        return analysis.list(key).stream().filter(n -> n.path("service").asText().equals(service)).count();
    }

}
