package io.hotdealwave.server;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Page labels, paths, templates and the arrays explained by each legend. */
enum PageKind {
    ENDPOINT("엔드포인트", "/endpoints/", "endpoint", List.of(OutputKey.ENDPOINTS, OutputKey.FIELDS, OutputKey.EXTERNAL_PATHS, OutputKey.TOKEN_REQUIRED, OutputKey.HTTP_CALLS, OutputKey.CALLS, OutputKey.THROWS, OutputKey.ERRORS)),
    TOPIC("토픽", "/topics/", "topic", List.of(OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS, OutputKey.MESSAGE_LINKS, OutputKey.CALLS)),
    SERVICE("서비스", "/services/", "service", List.of(OutputKey.SERVICES, OutputKey.ENDPOINTS, OutputKey.HTTP_CALLS, OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS, OutputKey.ENVIRONMENT_VARIABLES, OutputKey.ERRORS, OutputKey.UNSUPPORTED)),
    REPOSITORY("배포·모니터링", "/repository", "repository", List.of(OutputKey.REPOSITORY)),
    REVIVAL("되살리기", "/revival", "revival", List.of(OutputKey.ENVIRONMENT_VARIABLES, OutputKey.REPOSITORY, OutputKey.UNSUPPORTED));

    private final String text;
    private final String path;
    private final String template;
    private final List<OutputKey> arrays;

    PageKind(String text, String path, String template, List<OutputKey> arrays) {
        this.text = text; this.path = path; this.template = template; this.arrays = arrays;
    }

    public String text() { return text; }
    public String path() { return path; }
    public String template() { return template; }
    public List<OutputKey> arrays() { return arrays; }

    static Optional<PageKind> at(String path) {
        return Arrays.stream(values()).filter(kind -> kind.path.endsWith("/") ? path.startsWith(kind.path) : path.equals(kind.path)).findFirst();
    }
}
