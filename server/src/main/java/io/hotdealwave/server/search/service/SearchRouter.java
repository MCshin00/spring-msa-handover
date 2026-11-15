package io.hotdealwave.server.search.service;

import io.hotdealwave.server.analysis.Facts;
import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.search.dto.SearchRouteDto;
import java.util.*;
import org.springframework.stereotype.Component;

/** Selects a service and page kind from the query before ranking. */
@Component
class SearchRouter {
    private static final Map<String, PageKind> KIND_WORDS = new LinkedHashMap<>();
    static {
        for (var word : List.of("api", PageKind.ENDPOINT.getText())) KIND_WORDS.put(word, PageKind.ENDPOINT);
        for (var word : List.of(PageKind.TOPIC.getText(), "kafka")) KIND_WORDS.put(word, PageKind.TOPIC);
        for (var word : List.of(PageKind.SERVICE.getText(), "설정", "환경변수")) KIND_WORDS.put(word, PageKind.SERVICE);
    }
    private final Map<String, String> serviceNames = new HashMap<>();

    SearchRouter(VerifierOutput verifierOutput) {
        for (var service : verifierOutput.list(OutputKey.SERVICES)) {
            String name = Facts.valueOf(service.path("name"));
            serviceNames.put(name.toLowerCase(), name);
            String directory = service.path("directory").asText();
            serviceNames.put(directory.substring(directory.lastIndexOf('/') + 1).toLowerCase(), name);
        }
    }
    /** A service named as in the data (order-service, order) and a kind word, when the question has them. */
    SearchRouteDto route(String query) {
        var words = new HashSet<String>();
        var matcher = Tokenizer.WORD.matcher(query.toLowerCase());
        while (matcher.find()) words.add(matcher.group());
        String service = words.stream().map(serviceNames::get).filter(Objects::nonNull).findFirst().orElse(null);
        PageKind kind = KIND_WORDS.entrySet().stream().filter(entry -> words.contains(entry.getKey()) || (entry.getKey().charAt(0) >= '가'
                && query.contains(entry.getKey())))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        return new SearchRouteDto(service, kind);
    }

}
