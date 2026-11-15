package io.hotdealwave.server;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SearchTest {
    @Autowired Search search;
    @Autowired SearchRouter router;
    @Autowired MockMvc mvc;

    @Test void tokensKeepIdentifiersSplitCaseAndPairKorean() {
        assertEquals(List.of("createorder", "create", "order"), Tokenizer.tokens("createOrder"));
        assertEquals(List.of("reduce_quantity", "reduce", "quantity"), Tokenizer.tokens("reduce_quantity"));
        assertEquals(List.of("재고", "고부", "부족"), Tokenizer.tokens("재고부족"));
    }

    @Test void searchFindsIdentifiersCasePartsAndKorean() {
        var createOrder = search.search("createOrder");
        assertEquals("/endpoints/order-service.OrderController.createOrder", createOrder.get(0).page());
        assertTrue(search.search("reduce quantity").stream().anyMatch(r -> r.page().equals("/topics/KAFKA_REDUCE_QUANTITY_TOPIC")));
        assertTrue(search.search("쿠폰 복구").stream().anyMatch(r -> search.text(r).contains("쿠폰을 먼저 사용한 뒤 주문이 실패해도 쿠폰을 복구하지 않는다")));
    }

    @Test void resultsKeepOnePerPageUpToFive() {
        var results = search.search("주문 취소");
        assertEquals(5, results.size());
        assertEquals(5, results.stream().map(Search.Result::page).distinct().count());
    }

    @Test void filtersByDataServiceNamesAndKindWordsThenRelaxes() {
        assertEquals(new SearchRouter.Route("payment-service", null), router.route("payment-service 취소"));
        assertEquals("order-service", router.route("order 토픽").service());
        assertEquals("토픽", router.route("order 토픽").kind().text());
        assertEquals(new SearchRouter.Route(null, null), router.route("주문 createOrder"));
        assertTrue(search.search("payment-service 취소").stream().allMatch(r -> r.services().contains("payment-service")));
        // eureka-server has no topic, so the kind filter is dropped and the service page remains.
        assertEquals("/services/eureka-server", search.search("eureka-server 토픽").get(0).page());
    }

    @Test void apiSearchReturnsPageSectionScoreAndPreview() throws Exception {
        mvc.perform(get("/api/search").param("q", "createOrder"))
                .andExpect(jsonPath("$[0].page").value("/endpoints/order-service.OrderController.createOrder"))
                .andExpect(jsonPath("$[0].section").isString())
                .andExpect(jsonPath("$[0].score").isNumber())
                .andExpect(jsonPath("$[0].preview").isString());
    }
}
