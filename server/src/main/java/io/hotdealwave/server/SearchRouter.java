package io.hotdealwave.server;

import java.util.*;
import org.springframework.stereotype.Component;

/** Selects a service and page kind from the query before ranking. */
@Component
class SearchRouter {
    record Route(String service, PageKind kind) {}

    private static final Map<String, PageKind> KIND_WORDS = new LinkedHashMap<>();
    static {
        for (var w : List.of("api", PageKind.ENDPOINT.text())) KIND_WORDS.put(w, PageKind.ENDPOINT);
        for (var w : List.of(PageKind.TOPIC.text(), "kafka")) KIND_WORDS.put(w, PageKind.TOPIC);
        for (var w : List.of(PageKind.SERVICE.text(), "설정", "환경변수")) KIND_WORDS.put(w, PageKind.SERVICE);
    }
    private final Map<String, String> serviceNames = new HashMap<>();

    SearchRouter(Analysis analysis) {
        for (var service : analysis.list(OutputKey.SERVICES)) {
            String name = Analysis.value(service.path("name"));
            serviceNames.put(name.toLowerCase(), name);
            String directory = service.path("directory").asText();
            serviceNames.put(directory.substring(directory.lastIndexOf('/') + 1).toLowerCase(), name);
        }
    }
    /** A service named as in the data (order-service, order) and a kind word, when the question has them. */
    Route route(String query) {
        var words = new HashSet<String>();
        var m = Tokenizer.WORD.matcher(query.toLowerCase());
        while (m.find()) words.add(m.group());
        String service = words.stream().map(serviceNames::get).filter(Objects::nonNull).findFirst().orElse(null);
        PageKind kind = KIND_WORDS.entrySet().stream().filter(e -> words.contains(e.getKey()) || (e.getKey().charAt(0) >= '가' && query.contains(e.getKey())))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        return new Route(service, kind);
    }

}
