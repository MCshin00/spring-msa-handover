package io.hotdealwave.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.StreamSupport;

/** Values and confirmation states in the verifier output. */
public final class Facts {
    private Facts() {}

    public static boolean isConfirmed(JsonNode node) {
        return FactStatus.CONFIRMED.getText().equals(node.path("status").asText());
    }

    public static String valueOf(JsonNode fact) {
        return fact.path("value").isValueNode() ? fact.path("value").asText() : null;
    }

    public static List<JsonNode> items(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }
}
