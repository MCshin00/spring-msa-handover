package io.hotdealwave.server.page.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** The example output with one mechanism the tool does not follow, in order-service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnsupportedTest {
    @DynamicPropertySource static void verifierOutput(DynamicPropertyRegistry registry) throws Exception {
        var json = new ObjectMapper();
        var root = (ObjectNode) json.readTree(Path.of("examples/hotdealwave/out/analysis.json").toFile());
        var item = root.withArray("unsupported").addObject().put("service", "order-service").put("mechanism", "RestTemplate")
                .put("description", "재고를 HTTP로 조회한다").put("status", "확인됨").putNull("reason");
        item.putArray("evidence").add("order/src/main/java/com/sparta/hotdeal/order/presentation/controller/OrderController.java:40");
        Path file = Files.createTempFile("analysis", ".json");
        file.toFile().deleteOnExit();
        json.writeValue(file.toFile(), root);
        registry.add("analysis.path", file::toString);
    }

    @LocalServerPort int port;
    @Autowired PageService pageService;

    @Test void servicePageAndFlowsThroughItSayWhatTheToolDoesNotFollow() {
        String service = pageService.text("/services/order-service");
        assertTrue(service.contains("도구가 다루지 않는 방식 1곳"));
        assertTrue(service.contains("RestTemplate: 재고를 HTTP로 조회한다"));
        assertFalse(pageService.text("/services/coupon-service").contains("도구가 다루지 않는 방식"));

        var client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build()).build();
        client.initialize();
        try {
            var result = client.callTool(new McpSchema.CallToolRequest("trace_flow", Map.of("id", "order-service.OrderController.createOrder")));
            assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("도구가 다루지 않는 방식 1곳: order-service RestTemplate"));
        } finally {
            client.closeGracefully();
        }
    }
}
