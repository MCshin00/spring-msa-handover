package io.hotdealwave.server.page.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.Facts;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

import static io.hotdealwave.server.analysis.Facts.isConfirmed;
import static io.hotdealwave.server.analysis.Facts.valueOf;

/** Text for page bodies: a confirmed value with its evidence, otherwise the status and reason in its place. */
@Component("facts")
public class FactFormatter {
    public String valueWithEvidence(JsonNode fact) {
        if (fact.isMissingNode()) return "";
        String shown = isConfirmed(fact) ? valueOf(fact) : fact.path("status").asText() + " — " + fact.path("reason").asText("");
        return shown + " " + evidence(fact);
    }

    public String statusWithReason(JsonNode node) {
        if (isConfirmed(node)) return node.path("status").asText();
        return node.path("status").asText() + (node.path("reason").isTextual() ? " — " + node.path("reason").asText() : "");
    }

    /** The value alone, for link text; the status when the value is not confirmed. */
    public String valueOrStatus(JsonNode fact) {
        return isConfirmed(fact) ? valueOf(fact) : fact.path("status").asText();
    }

    public String evidence(JsonNode node) {
        return bracketedTexts(node.path("evidence"));
    }

    public int count(JsonNode list) {
        return Facts.items(list).size();
    }

    public String bracketedTexts(JsonNode list) {
        return "[" + String.join(", ", Facts.items(list).stream().map(JsonNode::asText).toList()) + "]";
    }

    /** Two evidence lists as one, so a line carries a single evidence list at its end. */
    public String bracketedTexts(JsonNode first, JsonNode second) {
        var lines = new ArrayList<>(Facts.items(first));
        lines.addAll(Facts.items(second));
        return "[" + String.join(", ", lines.stream().map(JsonNode::asText).toList()) + "]";
    }

    /** Plain strings of a list, comma separated. */
    public String commaSeparatedTexts(JsonNode list) {
        return String.join(", ", Facts.items(list).stream().map(JsonNode::asText).toList());
    }

    /** Values of a fact list, each its value or status. */
    public String valuesOrStatuses(JsonNode facts) {
        return String.join(", ", Facts.items(facts).stream().map(this::valueOrStatus).toList());
    }
}
