package io.hotdealwave.server;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Groups warnings in warning-code order and locates the related page. */
@Service
class WarningService {
    private static final Pattern TOPIC = Pattern.compile("같은 (?:환경변수|토픽 이름)\\(([^)]+)\\)");
    private static final Pattern CALLER = Pattern.compile(Pattern.quote(FactStatus.UNCONFIRMED.text()) + ": ([^.\\s]+)\\.");

    private final Analysis analysis;
    private final FactFormatter view;

    WarningService(Analysis analysis, FactFormatter view) { this.analysis = analysis; this.view = view; }

    /** Warnings of one reason code as lines, with what to do about them. */
    List<WarningGroupDto> groups() {
        var gaps = new ArrayList<WarningGroupDto>();
        analysis.get(OutputKey.WARNING_CODES).properties().forEach(code -> gaps.add(new WarningGroupDto(code.getKey(), code.getValue().path("title").asText(),
                code.getValue().path("action").asText(), analysis.list(OutputKey.WARNINGS).stream()
                .filter(w -> w.path("code").asText().equals(code.getKey())).map(this::gap).toList())));
        return gaps;
    }

    private WarningDto gap(com.fasterxml.jackson.databind.JsonNode warning) {
        String reason = warning.path("reason").asText(), check = warning.path("check").asText();
        var topic = TOPIC.matcher(reason);
        var caller = CALLER.matcher(reason);
        String kind = WarningCheck.text(check);
        if (topic.find()) return new WarningDto(kind, reason, view.evidence(warning), PageKind.TOPIC.path() + topic.group(1), topic.group(1));
        if (caller.find()) return new WarningDto(kind, reason, view.evidence(warning), PageKind.SERVICE.path() + caller.group(1), caller.group(1));
        return new WarningDto(kind, reason, view.evidence(warning), null, null);
    }

}
