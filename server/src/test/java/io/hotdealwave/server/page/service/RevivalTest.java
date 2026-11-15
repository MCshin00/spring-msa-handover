package io.hotdealwave.server.page.service;

import io.hotdealwave.server.search.service.SearchService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The revival page gathers what starting the services again needs from the example output. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RevivalTest {
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired PageService pageService;
    @Autowired SearchService searchService;

    private String html(String path) throws Exception {
        var response = mvc.perform(get(path)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), path);
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString();
    }

    @Test void pageGathersResourcesVariablesSecretsContainersWarningsTrapsAndUnsupported() throws Exception {
        String text = pageService.text("/revival");
        assertTrue(text.contains("payment-service · KakaoPay"));
        assertTrue(text.contains("payment-service · 외부 API payment-service.KakaoPayClient.ready (feign) · 주소 "
                + "https://open-api.kakaopay.com/online/v1/payment "
                        + "[payment/src/main/java/com/sparta/hotdeal/payment/infrastructure/client/KakaoPayClient.java:14] · 경로 /ready"));
        assertTrue(text.contains("LOCAL_DB_ENDPOINT: 저장소에 근거 없음 — 실제 값은 저장소 밖의 환경변수에서 정해짐 · 서비스 company-service, coupon-service, "
                + "order-service, payment-service, product-service, user-service · 프로필 local · 위치 spring.datasource.url"));
        assertTrue(text.contains("AWS_ACCESS_KEY_ID(secrets.AWS_ACCESS_KEY_ID) 값: 저장소에 근거 없음 — 실제 값은 저장소 밖에 등록된 비밀값에서 정해짐 "
                + "[.github/workflows/actions.yml:22, .github/workflows/actions.yml:79]"));
        assertTrue(text.contains("company-service: 이미지 ${ECR_REGISTRY}/${ECR_REPOSITORY_PREFIX}:company-latest"));
        assertTrue(text.contains("저장소 밖 값 9개"));
        assertTrue(text.contains("할 일: 값이 배포 환경의 환경변수에서 정해져"));
        assertTrue(text.contains("[함정 · AI 해석] 업체 생성 상태는 REQUESTED로 고정하지 않고 요청값을 저장한다"));
        assertFalse(text.contains("[업무 규칙 · AI 해석]"));
        assertTrue(text.contains("도구가 다루지 않은 위치\n없음"));

        String page = html("/revival");
        assertTrue(page.contains("/payment/src/main/java/com/sparta/hotdeal/payment/infrastructure/adapter/KakaoPayClientAdapter.java#L29\">"));
        assertTrue(text.contains("company-service · DB 확인됨 · 설정 14줄 · 코드 4줄 [company/src/main/resources/application-local.yml:3, "));
        assertTrue(page.contains("company-service · DB · 설정 14줄 · 코드 4줄 <details class=\"ev\"><summary>근거 18개</summary>"));
        assertTrue(html("/").contains("<a href=\"/revival\">"));
        assertTrue(html("/repository").contains("<a href=\"/revival\">"));
    }

    @Test void mcpReadsThePageButSearchDoesNotIndexIt() {
        assertTrue(searchService.search("KakaoPay").stream().noneMatch(result -> result.page().equals("/revival")));
        assertTrue(searchService.search("AWS_ACCESS_KEY_ID 비밀값").stream().noneMatch(result -> result.page().equals("/revival")));

        var client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build()).build();
        client.initialize();
        try {
            var result = client.callTool(new McpSchema.CallToolRequest("get_page", Map.of("page", "/revival")));
            assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("LOCAL_DB_ENDPOINT"));
        } finally {
            client.closeGracefully();
        }
    }
}
