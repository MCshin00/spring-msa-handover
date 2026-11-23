package io.hotdealwave.server.ask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hotdealwave.server.client.ModelInput;
import io.hotdealwave.server.client.ModelReply;
import io.hotdealwave.server.client.ModelTurn;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class DeepAskServiceTest {
    @TestConfiguration
    static class Fake {
        @Bean @Primary AskServiceTest.FakeModel fakeModel() { return new AskServiceTest.FakeModel(); }
    }

    @Autowired DeepAskService deepAskService;
    @Autowired AskService askService;
    @Autowired AskServiceTest.FakeModel model;
    @Autowired MockMvc mvc;

    @BeforeEach void reset() {
        model.turns.clear();
        model.inputs.clear();
        model.toolChoices.clear();
    }

    private static ModelTurn calls(String tool, String argument, String value, int count) {
        var calls = new ArrayList<ModelInput.ToolCall>();
        for (int i = 0; i < count; i++) calls.add(new ModelInput.ToolCall("call_" + i, tool, "{\"" + argument + "\":\"" + value + "\"}"));
        return new ModelTurn(calls, null, 100, 0, 10);
    }

    private static ModelTurn answer(String answer, String... citations) {
        return new ModelTurn(List.of(), new ModelReply(answer, List.of(citations), false, 100, 20), 100, 0, 20);
    }

    private ModelInput.ToolResult result(int call, int item) { return (ModelInput.ToolResult) model.inputs.get(call).get(item); }

    @Test void toolResultGetsTheNextNumberAndTheAnswerCitesIt() throws Exception {
        model.next = new ModelReply("조각에 없다.", List.of(), true, 0, 0);
        askService.ask("createOrder");
        String askInput = model.lastInput;
        model.turns.add(calls("get_page", "page", "/services/payment-service", 1));
        model.turns.add(answer("결제 서비스 이름은 payment/src/main/resources/application.yml:3에 있다.", "C9"));
        mvc.perform(post("/api/ask/deep").contentType("application/json").content("{\"question\":\"createOrder\"}"))
                .andExpect(jsonPath("$.status").value("답"))
                .andExpect(jsonPath("$.citations[0].id").value("C9"))
                .andExpect(jsonPath("$.citations[0].tool").value("get_page"))
                .andExpect(jsonPath("$.citations[0].page").value("/services/payment-service"))
                .andExpect(jsonPath("$.steps[0].mark").value("C9"))
                .andExpect(jsonPath("$.modelCalls").value(2))
                .andExpect(jsonPath("$.toolCalls").value(1));
        assertEquals(askInput, ((ModelInput.Text) model.inputs.get(0).get(0)).text());
        assertTrue(result(1, 2).output().startsWith("[C9] get_page(/services/payment-service)\n확인됨의 뜻"));
    }

    @Test void pageWholeInTheStartIsNotReadAgain() {
        model.turns.add(calls("get_page", "page", "/endpoints/order-service.OrderController.createOrder", 1));
        model.turns.add(answer("주문 생성은 OrderController.java:40에서 받는다.", "C1"));
        var answer = deepAskService.ask("createOrder");
        assertEquals("답", answer.status().getText());
        assertEquals("이미 C1", answer.steps().get(0).mark());
        assertTrue(result(1, 2).output().contains("C1"));
        assertFalse(result(1, 2).output().contains("OrderController.java:40"));
    }

    @Test void reachingTheLimitTurnsToolsOffForTheFinalAnswer() throws Exception {
        model.turns.add(calls("get_page", "page", "/repository", 16));
        model.turns.add(answer("주문 생성은 OrderController.java:40에서 받는다.", "C1"));
        String page = mvc.perform(post("/search/deep").param("question", "createOrder")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals(List.of(true, false), model.toolChoices);
        var marks = new ArrayList<>(List.of("C9"));
        marks.addAll(Collections.nCopies(14, "이미 C9"));
        marks.add("상한");
        for (int i = 0; i < 16; i++) assertTrue(page.contains("get_page(/repository) → " + marks.get(i)));
        assertTrue(page.contains("답 · 상한에 닿음"));
        assertTrue(page.contains("자세히 찾기"));
    }

    @Test void citationCheckBlocksTheAnswerAndTheTraceKeepsIt() throws Exception {
        model.turns.add(answer("주문 생성은 OrderController.java:41에서 받는다.", "C1"));
        var answer = deepAskService.ask("createOrder");
        assertEquals("보류", answer.status().getText());
        assertEquals("인용 검사: 인용한 조각에 없는 OrderController.java:41", answer.reason());
        assertNull(answer.answer());
        var lines = Files.readAllLines(Path.of("build/server-test/ask-trace.jsonl"), StandardCharsets.UTF_8);
        var last = new ObjectMapper().readTree(lines.get(lines.size() - 1));
        assertEquals("인용 검사: 인용한 조각에 없는 OrderController.java:41", last.path("reason").asText());
        assertEquals("주문 생성은 OrderController.java:41에서 받는다.", last.path("lastOutput").path("answer").asText());
    }
}
