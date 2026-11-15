package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** Text and display classes for interpreted knowledge. */
@Component("knowledge")
public class KnowledgeFormatter {
    /** Kind and source; the confirmation status only once the developer has set it. */
    public String item(JsonNode item) {
        return itemTag(item) + " " + item.path("title").asText();
    }

    public String itemTag(JsonNode item) {
        String status = item.path("confirmationStatus").asText();
        return "[" + item.path("kind").asText() + " · " + item.path("source").asText() + (ConfirmationStatus.UNCONFIRMED.text().equals(status) ? "" : " · " + status) + "]";
    }

    /** Style class for an item's kind tag. */
    public String kindClass(JsonNode item) {
        if (KnowledgeKind.TRAP.text().equals(item.path("kind").asText())) return KnowledgeKind.TRAP.kindClass();
        if (KnowledgeKind.RULE.text().equals(item.path("kind").asText())) return KnowledgeKind.RULE.kindClass();
        return KnowledgeKind.ABOUT.kindClass();
    }
}
