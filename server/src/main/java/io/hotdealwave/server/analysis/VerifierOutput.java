package io.hotdealwave.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The verifier output, the only input of the server. */
@Component
public class VerifierOutput {
    private final JsonNode root;

    public VerifierOutput(@Value("${analysis.path}") String path) throws IOException {
        root = new ObjectMapper().readTree(Path.of(path).toFile());
    }

    public List<JsonNode> list(OutputKey key) { return Facts.items(root.path(key.getText())); }

    public JsonNode find(OutputKey key, String id) {
        return list(key).stream().filter(item -> item.path("id").asText().equals(id)).findFirst()
                .orElse(MissingNode.getInstance());
    }

    JsonNode checks() { return root.path(OutputKey.CHECKS.getText()); }

    /** Any other top-level part of the output, such as the warning codes and what "확인됨" means. */
    public JsonNode get(OutputKey key) { return root.path(key.getText()); }

    public Target target() {
        var target = root.path(OutputKey.TARGET.getText());
        return new Target(target.path("name").asText(), target.path("repository").asText(), target.path("commit").asText());
    }

    /** Repository-level facts (deployment, monitoring); missing when the extraction has none. */
    public JsonNode repositoryFacts() { return root.path(OutputKey.REPOSITORY.getText()); }

}
