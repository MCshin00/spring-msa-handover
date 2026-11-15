package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import static io.hotdealwave.server.Analysis.known;

/** Text for endpoint fields and live response verification. */
@Component("endpointsFormatter")
public class EndpointFormatter {
    private final FactFormatter view;

    EndpointFormatter(FactFormatter view) { this.view = view; }

    /** File name of a recorded response, or null when none was recorded. */
    public String responseFile(JsonNode check) {
        String file = check.path("file").asText(null);
        return file == null ? null : file.substring(file.lastIndexOf('/') + 1);
    }

    /** How far an endpoint was checked: code alone, or code plus a recorded live response. */
    public String verification(JsonNode endpoint) {
        var check = endpoint.path("responseCheck");
        if (!ResponseCheckStatus.RECORDED.text().equals(check.path("status").asText())) return VerificationScope.CODE_ONLY.text();
        int code = check.path("statusCode").asInt();
        return code / 100 == 2 ? VerificationScope.RESPONSE.text() : VerificationScope.ERROR_RESPONSE.withCode(code);
    }

    /** Endpoint count per verification scope, error codes merged. */
    public String verificationSummary(List<JsonNode> endpoints) {
        if (endpoints.isEmpty()) return VerificationScope.CODE_ONLY.text() + "(엔드포인트 없음)";
        var counts = new LinkedHashMap<String, Integer>();
        for (var scope : List.of(VerificationScope.RESPONSE.text(), VerificationScope.ERROR_RESPONSE.text(), VerificationScope.CODE_ONLY.text())) counts.put(scope, 0);
        for (var endpoint : endpoints) counts.merge(verification(endpoint).replaceFirst(" \\d+\\)", ")"), 1, Integer::sum);
        var parts = counts.entrySet().stream().filter(e -> e.getValue() > 0).map(e -> e.getKey() + " " + e.getValue() + "개").toList();
        return "엔드포인트 " + endpoints.size() + "개 중 " + String.join(", ", parts);
    }

    /** How the observed keys compare with the code: the differences, "차이 없음", or nothing when no response was observed. */
    public List<String> comparison(JsonNode check) {
        if (!ResponseCheckStatus.RECORDED.text().equals(check.path("status").asText())) return List.of();
        var differences = Analysis.listOf(check.path("comparison")).stream().map(JsonNode::asText).toList();
        return differences.isEmpty() ? List.of("차이 없음") : differences;
    }

    public String responseCheck(JsonNode check) {
        String status = check.path("status").asText();
        if (check.path("statusCode").isNumber()) status += " " + check.path("statusCode").asInt();
        if (check.path("reason").isTextual()) status += " (" + check.path("reason").asText() + ")";
        return status;
    }

    /** Field tree lines of a request or response type, one field per line. */
    public List<String> fields(JsonNode schema) {
        var lines = new ArrayList<String>();
        fields(schema, "", lines);
        return lines;
    }

    private void fields(JsonNode schema, String indent, List<String> lines) {
        for (var field : Analysis.listOf(schema.path("fields"))) {
            var annotations = Analysis.listOf(field.path("annotations")).stream().map(a -> "@" + a.path("name").asText()).toList();
            lines.add(indent + view.fact(field.path("jsonName")).replaceFirst(" \\[.*", "") + "  " + field.path("schema").path("type").asText()
                    + "  " + (annotations.isEmpty() ? "어노테이션 없음" : String.join(" ", annotations)) + "  " + view.status(field) + " " + view.evidence(field)
                    + (known(field.path("schema")) ? "" : "  타입 구조: " + view.status(field.path("schema"))));
            if (!field.path("schema").path("recursive").asBoolean()) nested(field.path("schema"), indent + "    ", lines);
        }
    }

    private void nested(JsonNode schema, String indent, List<String> lines) {
        fields(schema, indent, lines);
        for (var argument : Analysis.listOf(schema.path("arguments"))) nested(argument, indent, lines);
    }
}
