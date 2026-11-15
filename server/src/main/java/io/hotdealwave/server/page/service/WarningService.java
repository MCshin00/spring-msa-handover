package io.hotdealwave.server.page.service;

import io.hotdealwave.server.analysis.FactStatus;
import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.analysis.WarningCheck;
import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.dto.WarningDto;
import io.hotdealwave.server.page.dto.WarningGroupDto;
import io.hotdealwave.server.page.format.FactFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Groups warnings in warning-code order and locates the related page. */
@Service
@RequiredArgsConstructor
public class WarningService {
    private static final Pattern TOPIC = Pattern.compile("같은 (?:환경변수|토픽 이름)\\(([^)]+)\\)");
    private static final Pattern CALLER = Pattern.compile(Pattern.quote(FactStatus.UNCONFIRMED.getText()) + ": ([^.\\s]+)\\.");

    private final VerifierOutput verifierOutput;
    private final FactFormatter factFormatter;

    /** Warnings of one reason code as lines, with what to do about them. */
    List<WarningGroupDto> groups() {
        var gaps = new ArrayList<WarningGroupDto>();
        verifierOutput.get(OutputKey.WARNING_CODES).properties().forEach(code -> gaps.add(new WarningGroupDto(code.getKey(), code.getValue()
                .path("title").asText(),
                code.getValue().path("action").asText(), verifierOutput.list(OutputKey.WARNINGS).stream()
                .filter(warning -> warning.path("code").asText().equals(code.getKey())).map(this::toWarning).toList())));
        return gaps;
    }

    private WarningDto toWarning(com.fasterxml.jackson.databind.JsonNode warning) {
        String reason = warning.path("reason").asText(), check = warning.path("check").asText();
        var topic = TOPIC.matcher(reason);
        var caller = CALLER.matcher(reason);
        String kind = WarningCheck.text(check);
        if (topic.find()) return new WarningDto(kind, reason, factFormatter.evidence(warning), PageKind.TOPIC.getPath() + topic.group(1),
                topic.group(1));
        if (caller.find()) return new WarningDto(kind, reason, factFormatter.evidence(warning), PageKind.SERVICE.getPath()
                + caller.group(1), caller.group(1));
        return new WarningDto(kind, reason, factFormatter.evidence(warning), null, null);
    }

}
