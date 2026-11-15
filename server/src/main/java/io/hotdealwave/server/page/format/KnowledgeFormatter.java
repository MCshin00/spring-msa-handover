package io.hotdealwave.server.page.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.ConfirmationStatus;
import io.hotdealwave.server.analysis.KnowledgeKind;
import org.springframework.stereotype.Component;

/** Text and display classes for interpreted knowledge. */
@Component("knowledge")
public class KnowledgeFormatter {
    /** Kind and source; the confirmation status only once the developer has set it. */
    String itemSummary(JsonNode item) {
        return itemTag(item) + " " + item.path("title").asText();
    }

    public String itemTag(JsonNode item) {
        String status = item.path("confirmationStatus").asText();
        return "[" + item.path("kind").asText() + " · " + item.path("source").asText() + (ConfirmationStatus.UNCONFIRMED.getText()
                .equals(status) ? "" : " · " + status) + "]";
    }

    /** Style class for an item's kind tag. */
    public String kindClass(JsonNode item) {
        if (KnowledgeKind.TRAP.getText().equals(item.path("kind").asText())) return KnowledgeKind.TRAP.getKindClass();
        if (KnowledgeKind.RULE.getText().equals(item.path("kind").asText())) return KnowledgeKind.RULE.getKindClass();
        return KnowledgeKind.ABOUT.getKindClass();
    }
}
