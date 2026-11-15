package io.hotdealwave.server.page.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.Facts;
import io.hotdealwave.server.analysis.ResponseCheckStatus;
import io.hotdealwave.server.page.VerificationScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static io.hotdealwave.server.analysis.Facts.isConfirmed;

/** Text for endpoint fields and live response verification. */
@Component("endpointsFormatter")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class EndpointFormatter {
    private final FactFormatter factFormatter;

    /** File name of a recorded response, or null when none was recorded. */
    public String responseFile(JsonNode check) {
        String file = check.path("file").asText(null);
        return file == null ? null : file.substring(file.lastIndexOf('/') + 1);
    }

    /** How far an endpoint was checked: code alone, or code plus a recorded live response. */
    public String verificationScope(JsonNode endpoint) {
        var check = endpoint.path("responseCheck");
        if (!ResponseCheckStatus.RECORDED.getText().equals(check.path("status").asText())) return VerificationScope.CODE_ONLY.getText();
        int code = check.path("statusCode").asInt();
        return code / 100 == 2 ? VerificationScope.RESPONSE.getText() : VerificationScope.ERROR_RESPONSE.withCode(code);
    }

    /** Endpoint count per verification scope, error codes merged. */
    public String verificationSummary(List<JsonNode> endpoints) {
        if (endpoints.isEmpty()) return VerificationScope.CODE_ONLY.getText() + "(엔드포인트 없음)";
        var counts = new LinkedHashMap<String, Integer>();
        for (var scope : List.of(VerificationScope.RESPONSE.getText(), VerificationScope.ERROR_RESPONSE.getText(),
                VerificationScope.CODE_ONLY.getText())) counts.put(scope, 0);
        for (var endpoint : endpoints) counts.merge(verificationScope(endpoint).replaceFirst(" \\d+\\)", ")"), 1, Integer::sum);
        var parts = counts.entrySet().stream().filter(entry -> entry.getValue() > 0).map(entry -> entry.getKey() + " " + entry.getValue()
                + "개").toList();
        return "엔드포인트 " + endpoints.size() + "개 중 " + String.join(", ", parts);
    }

    /** How the observed keys compare with the code: the differences, "차이 없음", or nothing when no response was observed. */
    public List<String> responseDifferences(JsonNode check) {
        if (!ResponseCheckStatus.RECORDED.getText().equals(check.path("status").asText())) return List.of();
        var differences = Facts.items(check.path("comparison")).stream().map(JsonNode::asText).toList();
        return differences.isEmpty() ? List.of("차이 없음") : differences;
    }

    public String responseCheck(JsonNode check) {
        String status = check.path("status").asText();
        if (check.path("statusCode").isNumber()) status += " " + check.path("statusCode").asInt();
        if (check.path("reason").isTextual()) status += " (" + check.path("reason").asText() + ")";
        return status;
    }

    /** Field tree lines of a request or response type, one field per line. */
    public List<String> fieldLines(JsonNode schema) {
        var lines = new ArrayList<String>();
        fieldLines(schema, "", lines);
        return lines;
    }

    private void fieldLines(JsonNode schema, String indent, List<String> lines) {
        for (var field : Facts.items(schema.path("fields"))) {
            var annotations = Facts.items(field.path("annotations")).stream().map(annotation -> "@" + annotation.path("name").asText()).toList();
            lines.add(indent + factFormatter.valueWithEvidence(field.path("jsonName")).replaceFirst(" \\[.*", "") + "  "
                    + field.path("schema").path("type").asText()
                    + "  " + (annotations.isEmpty() ? "어노테이션 없음" : String.join(" ", annotations)) + "  "
                            + factFormatter.statusWithReason(field) + " " + factFormatter.evidence(field)
                    + (isConfirmed(field.path("schema")) ? "" : "  타입 구조: " + factFormatter.statusWithReason(field.path("schema"))));
            if (!field.path("schema").path("recursive").asBoolean()) nestedFieldLines(field.path("schema"), indent + "    ", lines);
        }
    }

    private void nestedFieldLines(JsonNode schema, String indent, List<String> lines) {
        fieldLines(schema, indent, lines);
        for (var argument : Facts.items(schema.path("arguments"))) nestedFieldLines(argument, indent, lines);
    }
}
