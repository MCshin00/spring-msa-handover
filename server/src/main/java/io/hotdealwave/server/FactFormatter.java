package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import static io.hotdealwave.server.Analysis.known;
import static io.hotdealwave.server.Analysis.value;

/** Text for page bodies: a confirmed value with its evidence, otherwise the status and reason in its place. */
@Component("facts")
public class FactFormatter {
    public String fact(JsonNode fact) {
        if (fact.isMissingNode()) return "";
        String shown = known(fact) ? value(fact) : fact.path("status").asText() + " — " + fact.path("reason").asText("");
        return shown + " " + evidence(fact);
    }

    public String status(JsonNode node) {
        if (known(node)) return node.path("status").asText();
        return node.path("status").asText() + (node.path("reason").isTextual() ? " — " + node.path("reason").asText() : "");
    }

    /** The value alone, for link text; the status when the value is not confirmed. */
    public String plain(JsonNode fact) {
        return known(fact) ? value(fact) : fact.path("status").asText();
    }

    public String evidence(JsonNode node) {
        return texts(node.path("evidence"));
    }

    public int count(JsonNode list) {
        return Analysis.listOf(list).size();
    }

    public String texts(JsonNode list) {
        return "[" + String.join(", ", Analysis.listOf(list).stream().map(JsonNode::asText).toList()) + "]";
    }

    /** Two evidence lists as one, so a line carries a single evidence list at its end. */
    public String texts(JsonNode first, JsonNode second) {
        var lines = new ArrayList<>(Analysis.listOf(first));
        lines.addAll(Analysis.listOf(second));
        return "[" + String.join(", ", lines.stream().map(JsonNode::asText).toList()) + "]";
    }

    /** Plain strings of a list, comma separated. */
    public String joined(JsonNode list) {
        return String.join(", ", Analysis.listOf(list).stream().map(JsonNode::asText).toList());
    }

    /** Values of a fact list, each its value or status. */
    public String plains(JsonNode facts) {
        return String.join(", ", Analysis.listOf(facts).stream().map(this::plain).toList());
    }
}
