package io.hotdealwave.server;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AskTest {
    static class FakeModel implements AskModel {
        @Override public String name() { return OpenAiModel.MODEL; }

        Reply next;
        RuntimeException failure;
        String lastInput;
        int calls;

        @Override public Reply reply(String instructions, String input) {
            calls++;
            lastInput = input;
            if (failure != null) throw failure;
            return next;
        }

        RuntimeException embedFailure;
        int embedded;

        @Override public List<float[]> embed(List<String> texts) {
            if (embedFailure != null) throw embedFailure;
            embedded += texts.size();
            return texts.stream().map(FakeModel::vector).toList();
        }

        /** Word counts on hashed axes, with "캐시" counted as "redis", as a real embedding places words of one meaning close together. */
        static float[] vector(String text) {
            var vector = new float[512];
            for (var token : Tokenizer.tokens(text)) vector[Math.floorMod((token.equals("캐시") ? "redis" : token).hashCode(), vector.length)]++;
            return vector;
        }
    }

    @TestConfiguration
    static class Fake {
        @Bean @Primary FakeModel fakeModel() { return new FakeModel(); }
    }

    @Autowired Ask ask;
    @Autowired Search search;
    @Autowired FakeModel model;
    @Autowired MockMvc mvc;

    @BeforeEach void reset() { model.next = null; model.failure = null; model.embedFailure = null; model.lastInput = null; model.calls = 0; }

    private static AskModel.Reply reply(String answer, boolean abstain, String... citations) {
        return new AskModel.Reply(answer, List.of(citations), abstain, 100, 20);
    }

    @Test void citedFileLinesInCitedChunksMakeAnAnswer() {
        model.next = reply("주문 생성은 OrderController.java:40에서 받는다.", false, "C1");
        var answer = ask.ask("createOrder");
        assertEquals("답", answer.status().text());
        assertEquals("/endpoints/order-service.OrderController.createOrder", answer.citations().get(0).page());
        assertTrue(model.lastInput.startsWith("조각:\n[C1] order-service.OrderController.createOrder"));
    }

    @Test void citationCheckTurnsBadCitationsIntoAbstain() {
        model.next = reply("주문 생성은 OrderController.java:40에서 받는다.", false, "C9");
        assertEquals("인용 검사: 없는 조각 C9", ask.ask("createOrder").reason());

        model.next = reply("주문 생성은 OrderController.java:41에서 받는다.", false, "C1");
        var answer = ask.ask("createOrder");
        assertEquals("보류", answer.status().text());
        assertEquals("인용 검사: 인용한 조각에 없는 OrderController.java:41", answer.reason());
        assertNull(answer.answer());

        model.next = reply("주문 생성 API다.", false);
        assertEquals("인용 검사: 인용 없음", ask.ask("createOrder").reason());
    }

    @Test void hybridSearchPassesSectionsBm25Misses() {
        // The documents say Redis, never 캐시: BM25 (/api/search, search_docs) finds no Redis section, the embedding does.
        String question = "캐시 연결 주소는 어떻게 정해지나요?";
        assertTrue(search.search(question).stream().noneMatch(r -> search.text(r).toLowerCase().contains("redis")));
        model.next = reply("조각에 없다.", true);
        ask.ask(question);
        assertTrue(model.lastInput.contains(" — 환경변수 (/services/order-service)\n"));
    }

    @Test void askPassesEightChunksWhileSearchKeepsFive() {
        assertEquals(5, search.search("주문 취소").size());
        model.next = reply("조각에 없다.", true);
        ask.ask("주문 취소");
        assertTrue(model.lastInput.contains("\n[C8] "));
        assertFalse(model.lastInput.contains("[C9]"));
    }

    @Test void onlyNewOrChangedTextsAreEmbeddedAgain(@TempDir Path folder) {
        var fake = new FakeModel();
        String file = folder.resolve("embeddings.json").toString();
        new Embeddings(fake, "", file).of(List.of("Redis 연결", "주문 생성"));
        assertEquals(2, fake.embedded);
        var vectors = new Embeddings(fake, "", file).of(List.of("Redis 연결", "주문 취소"));
        assertEquals(3, fake.embedded);
        assertArrayEquals(FakeModel.vector("Redis 연결"), vectors.get(0));
    }

    @Test void modelAbstainAndFailuresAreKeptApart() {
        model.next = reply("조각에 없다.", true);
        assertEquals("모델 보류", ask.ask("createOrder").reason());

        model.embedFailure = new IllegalStateException("OPENAI_API_KEY가 없다");
        var noEmbedding = ask.ask("createOrder");
        assertEquals("오류", noEmbedding.status().text());
        assertTrue(noEmbedding.reason().startsWith("임베딩 호출 실패: OPENAI_API_KEY"));
        assertEquals(1, model.calls);
        model.embedFailure = null;

        model.failure = new IllegalStateException("OPENAI_API_KEY가 없다");
        var failed = ask.ask("createOrder");
        assertEquals("오류", failed.status().text());
        assertTrue(failed.reason().contains("OPENAI_API_KEY"));
    }

    @Test void fieldQuestionPassesTheRequestSection() {
        model.next = reply("조각에 없다.", true);
        ask.ask("createOrder 요청 필드");
        assertTrue(model.lastInput.contains("basketList  List<UUID>  @NotNull"));
    }

    @Test void topicQuestionPassesTheConnectionState() {
        model.next = reply("조각에 없다.", true);
        ask.ask("주문 생성이 보내는 상품 요청 Kafka 메시지는 누가 받나?");
        assertTrue(model.lastInput.contains("order-service.requestProduct → (구독) 확인 안 됨 — 구독자 확인 안 됨: 같은 환경변수(KAFKA_REQUEST_PRODUCT_TOPIC)를 쓰는 구독을 찾지 못함. 토픽 실제 값은 저장소 밖 환경에서 정해짐"));
    }

    @Test void apiAskReturnsStatusAnswerCitationsAndUsage() throws Exception {
        model.next = reply("주문 생성은 OrderController.java:40에서 받는다.", false, "C1");
        mvc.perform(post("/api/ask").contentType("application/json").content("{\"question\":\"createOrder\"}"))
                .andExpect(jsonPath("$.status").value("답"))
                .andExpect(jsonPath("$.citations[0].page").value("/endpoints/order-service.OrderController.createOrder"))
                .andExpect(jsonPath("$.inputTokens").value(100));
    }

    @Test void searchPageShowsResultsAndAnswerWithCitedLinks() throws Exception {
        String results = mvc.perform(get("/search").param("q", "createOrder")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(results.contains("href=\"/endpoints/order-service.OrderController.createOrder\""));

        model.next = reply("조각에 없다.", true);
        String abstained = mvc.perform(post("/search").param("question", "createOrder")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(abstained.contains("보류·오류 이유: 모델 보류"));

        model.next = reply("주문 생성은 OrderController.java:40에서 받는다.", false, "C1");
        String answered = mvc.perform(post("/search").param("question", "createOrder")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(answered.contains("주문 생성은 OrderController.java:40에서 받는다."));
        assertTrue(answered.contains("<a href=\"/endpoints/order-service.OrderController.createOrder\">"));
    }

    @Test void missingKeyFailsOnlyTheCall() {
        var error = assertThrows(IllegalStateException.class, () -> new OpenAiModel(null).reply("규칙", "질문"));
        assertTrue(error.getMessage().contains("OPENAI_API_KEY"));
        error = assertThrows(IllegalStateException.class, () -> new OpenAiModel(null).embed(List.of("질문")));
        assertTrue(error.getMessage().contains("OPENAI_API_KEY"));
    }
}
