package io.hotdealwave.server.page.service;

import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.ResponseCheckStatus;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.flow.service.FlowService;
import io.hotdealwave.server.page.dto.ServiceCardDto;
import io.hotdealwave.server.page.dto.TopicCardDto;
import io.hotdealwave.server.page.render.PageRenderer;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Builds the home page cards, counts and shared warning groups. */
@Service
@RequiredArgsConstructor
public class HomeService {
    private final VerifierOutput verifierOutput;
    private final PageService pageService;
    private final FlowService flowService;
    private final WarningService warningService;
    private final PageRenderer pageRenderer;

    public String index() {
        var cards = pageService.services().stream().map(name -> new ServiceCardDto(name,
                count(OutputKey.ENDPOINTS, name), count(OutputKey.HTTP_CALLS, name), count(OutputKey.MESSAGE_PUBLICATIONS, name),
                        count(OutputKey.MESSAGE_SUBSCRIPTIONS, name),
                verifierOutput.list(OutputKey.ENDPOINTS).stream().filter(endpoint -> endpoint.path("service").asText().equals(name))
                        .filter(endpoint -> ResponseCheckStatus.RECORDED.getText().equals(endpoint.path("responseCheck").path("status")
                                .asText())).count())).toList();
        var topics = pageService.topics().stream().map(name -> {
            var topic = flowService.findSubscribers(name);
            return new TopicCardDto(name, topic.publications().size(), topic.subscriptions().size());
        }).toList();
        var gaps = warningService.groups();
        return pageRenderer.html("index", Map.of("cards", cards, "topics", topics, "gaps", gaps, "gapCount",
                verifierOutput.list(OutputKey.WARNINGS).size(),
                "endpointCount", verifierOutput.list(OutputKey.ENDPOINTS).size(), "responded", cards.stream()
                        .mapToLong(ServiceCardDto::responded).sum()));
    }

    private long count(OutputKey key, String service) {
        return verifierOutput.list(key).stream().filter(item -> item.path("service").asText().equals(service)).count();
    }

}
