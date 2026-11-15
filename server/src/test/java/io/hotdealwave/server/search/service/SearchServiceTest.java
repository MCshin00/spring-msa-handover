package io.hotdealwave.server.search.service;

import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.dto.SearchRouteDto;
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
class SearchServiceTest {
    @Autowired SearchService searchService;
    @Autowired SearchRouter searchRouter;
    @Autowired MockMvc mvc;

    @Test void tokensKeepIdentifiersSplitCaseAndPairKorean() {
        assertEquals(List.of("createorder", "create", "order"), Tokenizer.tokens("createOrder"));
        assertEquals(List.of("reduce_quantity", "reduce", "quantity"), Tokenizer.tokens("reduce_quantity"));
        assertEquals(List.of("재고", "고부", "부족"), Tokenizer.tokens("재고부족"));
    }

    @Test void searchFindsIdentifiersCasePartsAndKorean() {
        var createOrder = searchService.search("createOrder");
        assertEquals("/endpoints/order-service.OrderController.createOrder", createOrder.get(0).page());
        assertTrue(searchService.search("reduce quantity").stream().anyMatch(result -> result.page().equals("/topics/KAFKA_REDUCE_QUANTITY_TOPIC")));
        assertTrue(searchService.search("쿠폰 복구").stream().anyMatch(result -> searchService.sectionText(result)
                .contains("쿠폰을 먼저 사용한 뒤 주문이 실패해도 쿠폰을 복구하지 않는다")));
    }

    @Test void resultsKeepOnePerPageUpToFive() {
        var results = searchService.search("주문 취소");
        assertEquals(5, results.size());
        assertEquals(5, results.stream().map(SearchResultDto::page).distinct().count());
    }

    @Test void filtersByDataServiceNamesAndKindWordsThenRelaxes() {
        assertEquals(new SearchRouteDto("payment-service", null), searchRouter.route("payment-service 취소"));
        assertEquals("order-service", searchRouter.route("order 토픽").service());
        assertEquals("토픽", searchRouter.route("order 토픽").kind().getText());
        assertEquals(new SearchRouteDto(null, null), searchRouter.route("주문 createOrder"));
        assertTrue(searchService.search("payment-service 취소").stream().allMatch(result -> result.services().contains("payment-service")));
        // eureka-server has no topic, so the kind filter is dropped and the service page remains.
        assertEquals("/services/eureka-server", searchService.search("eureka-server 토픽").get(0).page());
    }

    @Test void apiSearchReturnsPageSectionScoreAndPreview() throws Exception {
        mvc.perform(get("/api/search").param("q", "createOrder"))
                .andExpect(jsonPath("$[0].page").value("/endpoints/order-service.OrderController.createOrder"))
                .andExpect(jsonPath("$[0].section").isString())
                .andExpect(jsonPath("$[0].score").isNumber())
                .andExpect(jsonPath("$[0].preview").isString());
    }
}
