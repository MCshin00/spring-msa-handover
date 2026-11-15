package io.hotdealwave.server.page;

import io.hotdealwave.server.analysis.OutputKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Page labels, paths, templates and the arrays explained by each legend. */
@Getter
@RequiredArgsConstructor
public enum PageKind {
    ENDPOINT("엔드포인트", "/endpoints/", "endpoint", List.of(OutputKey.ENDPOINTS, OutputKey.FIELDS, OutputKey.EXTERNAL_PATHS,
            OutputKey.TOKEN_REQUIRED, OutputKey.HTTP_CALLS, OutputKey.CALLS, OutputKey.THROWS, OutputKey.ERRORS)),
    TOPIC("토픽", "/topics/", "topic", List.of(OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS, OutputKey.MESSAGE_LINKS,
            OutputKey.CALLS)),
    SERVICE("서비스", "/services/", "service", List.of(OutputKey.SERVICES, OutputKey.ENDPOINTS, OutputKey.HTTP_CALLS,
            OutputKey.MESSAGE_PUBLICATIONS, OutputKey.MESSAGE_SUBSCRIPTIONS, OutputKey.ENVIRONMENT_VARIABLES, OutputKey.ERRORS,
            OutputKey.UNSUPPORTED)),
    REPOSITORY("배포·모니터링", "/repository", "repository", List.of(OutputKey.REPOSITORY)),
    REVIVAL("되살리기", "/revival", "revival", List.of(OutputKey.ENVIRONMENT_VARIABLES, OutputKey.REPOSITORY, OutputKey.UNSUPPORTED));

    private final String text;
    private final String path;
    private final String template;
    private final List<OutputKey> arrays;

    public static Optional<PageKind> at(String path) {
        return Arrays.stream(values()).filter(kind -> kind.path.endsWith("/") ? path.startsWith(kind.path) : path.equals(kind.path)).findFirst();
    }
}
