package io.hotdealwave.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** An output without the repository-level file, rules and traps, external resources or HTTP calls. */
@SpringBootTest
class EmptyRevivalTest {
    @DynamicPropertySource static void analysis(DynamicPropertyRegistry registry) throws Exception {
        var json = new ObjectMapper();
        var root = (ObjectNode) json.readTree(Path.of("examples/hotdealwave/out/analysis.json").toFile());
        root.remove("repository");
        root.remove("knowledge");
        root.remove("externalResources");
        root.remove("httpCalls");
        Path file = Files.createTempFile("analysis", ".json");
        file.toFile().deleteOnExit();
        json.writeValue(file.toFile(), root);
        registry.add("analysis.path", file::toString);
    }

    @Autowired PageService pages;

    @Test void missingPartsSayNone() {
        String text = pages.text("/revival");
        assertTrue(text.contains("저장소 밖 비밀값\n없음"));
        assertTrue(text.contains("컨테이너\n없음"));
        assertTrue(text.contains("운영 함정\n없음"));
        assertTrue(text.contains("외부 자원\n없음"));
    }
}
