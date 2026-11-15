package io.hotdealwave.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
class GeneralCommunicationTest {
    @TempDir Path temporary;
    @Autowired SpringTemplateEngine engine;
    @Autowired ApplicationContext context;
    @Autowired FactFormatter view;
    Analysis analysis;
    Queries queries;
    PageService pages;
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode fact(String value) {
        var node = json.createObjectNode().put("value", value).put("status", "확인됨");
        node.putArray("evidence").add("Calls.java:1");
        return node;
    }
    @BeforeEach void data() throws Exception {
        var root = json.createObjectNode();
        root.putObject("target").put("name", "shop").put("repository", "https://github.com/acme/shop").put("commit", "abcdef123456");
        for (String key : new String[]{"messagePublications", "messageSubscriptions"}) {
            var node = root.putArray(key).addObject().put("id", key).put("kind", "rabbit").put("service", "shop").put("status", "확인됨");
            node.putArray("evidence").add("Calls.java:1");
            node.set("topicKey", fact(""));
            node.set("environment", fact(""));
            node.set("topicName", fact(key.equals("messagePublications") ? "events/created" : "orders"));
        }
        root.putArray("messageLinks").addObject().put("publicationId", "messagePublications").put("subscriptionId", "messageSubscriptions")
                .put("status", "확인됨").putArray("evidence").add("Calls.java:1");
        var http = root.putArray("httpCalls").addObject().put("id", "shop.Calls.get").put("kind", "restClient");
        http.set("target", fact("stock.Stock.get"));
        root.putArray("calls").addObject().put("fromId", "shop.Controller.get").put("toId", "shop.Calls.get").put("status", "확인됨");
        Path file = temporary.resolve("analysis.json");
        json.writeValue(file.toFile(), root);
        analysis = new Analysis(file.toString());
        queries = new Queries(analysis);
        pages = new PageService(analysis, queries, view, new WarningService(analysis, view),
                new PageRenderer(analysis, engine, context, new Legend(analysis)));
    }
    @Test void queriesUseKindsAndFollowLinksBetweenDifferentDestinationNames() {
        assertEquals("restClient 대상", queries.traceFlow("shop.Controller.get").forward().get(1).kind());
        assertEquals(1, queries.findSubscribers("events/created").subscriptions().size());
        assertEquals(1, queries.findSubscribers("orders").publications().size());
    }
    @Test void messagePageAcceptsExchangeAndRoutingKeyInItsPath() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PageController(pages, new HomeService(analysis, pages, queries, new WarningService(analysis, view),
                new PageRenderer(analysis, engine, context, new Legend(analysis))))).build();
        var response = mvc.perform(get("/topics/events/created")).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("messageSubscriptions"));
    }
}
