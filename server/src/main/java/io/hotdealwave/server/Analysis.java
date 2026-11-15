package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The verifier output, the only input of the server. */
@Component
class Analysis {
    static final String CONFIRMED = FactStatus.CONFIRMED.text();
    private final JsonNode root;

    Analysis(@Value("${analysis.path}") String path) throws IOException {
        root = new ObjectMapper().readTree(Path.of(path).toFile());
    }

    List<JsonNode> list(OutputKey key) { return listOf(root.path(key.text())); }

    static List<JsonNode> listOf(JsonNode array) { return StreamSupport.stream(array.spliterator(), false).toList(); }

    JsonNode find(OutputKey key, String id) {
        return list(key).stream().filter(n -> n.path("id").asText().equals(id)).findFirst()
                .orElse(MissingNode.getInstance());
    }

    JsonNode checks() { return root.path(OutputKey.CHECKS.text()); }

    /** Any other top-level part of the output, such as the warning codes and what "확인됨" means. */
    JsonNode get(OutputKey key) { return root.path(key.text()); }

    /** The analysed repository and its fixed commit, from the extraction's target file. */
    record Target(String name, String repository, String commit) {
        public String shortCommit() { return commit.substring(0, 7); }
        /** Where an evidence path of the target opens at the fixed commit. */
        public String source() { return repository + "/blob/" + commit + "/"; }
    }

    Target target() {
        var target = root.path(OutputKey.TARGET.text());
        return new Target(target.path("name").asText(), target.path("repository").asText(), target.path("commit").asText());
    }

    /** Repository-level facts (deployment, monitoring); missing when the extraction has none. */
    JsonNode repository() { return root.path(OutputKey.REPOSITORY.text()); }

    static boolean known(JsonNode node) { return CONFIRMED.equals(node.path("status").asText()); }

    static String value(JsonNode fact) { return fact.path("value").isValueNode() ? fact.path("value").asText() : null; }
}
