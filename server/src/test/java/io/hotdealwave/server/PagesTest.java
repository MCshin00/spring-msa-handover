package io.hotdealwave.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class PagesTest {
    @Autowired MockMvc mvc;

    private String page(String path) throws Exception {
        var response = mvc.perform(get(path)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), path);
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString();
    }

    @Autowired PageService pages;
    @Autowired Search search;

    @Test void homeSummarizesServicesAndListsUnconfirmedConnections() throws Exception {
        String home = page("/");
        assertTrue(home.contains("엔드포인트 11 · HTTP 8 · 발행 5 · 구독 3"));
        assertTrue(home.contains("<h2>확인 안 된 연결 13개</h2>"));
        assertTrue(home.contains("<b>13</b><span>확인 안 된 연결</span>"));
        assertTrue(home.contains("<h3>저장소 밖 값 9개</h3>"));
        assertTrue(home.contains("<h3>저장소 안에서 잇지 못함 4개</h3>"));
        assertTrue(home.contains("할 일: 값이 배포 환경의 환경변수에서 정해져"));
        assertTrue(home.contains("<a href=\"/topics/KAFKA_REQUEST_PRODUCT_TOPIC\">KAFKA_REQUEST_PRODUCT_TOPIC</a>"));
        assertFalse(home.contains("evidence-lines"));
    }

    @Test void messageWordingDoesNotNameOneBroker() throws Exception {
        String home = page("/");
        assertTrue(home.contains("서비스·API·메시지 흐름 문서"));
        assertTrue(home.contains("<h2>메시지 목적지</h2>"));
        String order = pages.text("/services/order-service");
        assertTrue(order.contains("메시지 발행\n"));
        assertTrue(order.contains("메시지 구독\n"));
        assertTrue(order.contains("order-service.cancelOrder (kafka) "));
        assertFalse(order.contains("Kafka 발행"));
    }

    @Test void responseCheckReasonIsFolded() throws Exception {
        String order = page("/services/order-service");
        assertTrue(order.contains("응답 기록 있음 400 <details class=\"why\"><summary>이유</summary>(400: PENDING 주문은"));
        assertTrue(order.contains("<a href=\"https://github.com/sparta-final-12th-9700/HotDealWave/blob/cc43016b907331a3ce8471475cbf44412e8a6954/order/src/main/java/com/sparta/hotdeal/order/application/service/order/OrderService.java#L295\">"));
    }

    @Test void sectionsFold() throws Exception {
        assertTrue(page("/endpoints/order-service.OrderController.createOrder").contains("<script src=\"/app.js\"></script>"));
        assertTrue(page("/app.js").contains("heading.classList.toggle(\"closed\")"));
    }

    @Test void evidenceLinksToFixedCommitAndResponseLines() throws Exception {
        String createOrder = page("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.contains("<a href=\"https://github.com/sparta-final-12th-9700/HotDealWave/blob/cc43016b907331a3ce8471475cbf44412e8a6954/"
                + "order/src/main/java/com/sparta/hotdeal/order/presentation/controller/OrderController.java#L40\">"));
        assertTrue(createOrder.contains("<a href=\"/responses/order-service.OrderController.createOrder.json#L10\">"));
        String response = page("/responses/order-service.OrderController.createOrder.json");
        assertTrue(response.contains("id=\"L10\""));
        assertTrue(response.contains("/api/v1/orders"));
    }

    @Test void evidenceListsAreFolded() throws Exception {
        assertTrue(page("/topics/KAFKA_REQUEST_PRODUCT_TOPIC").contains("<summary>근거 7개</summary>"));
    }

    @Test void flowStepsAreBoxesUnknownStepsToo() throws Exception {
        String createOrder = page("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.contains("<span class=\"node\">order-service.requestProduct</span>"));
        assertTrue(createOrder.contains("<span class=\"node unknown\"><span class=\"pill unknown\">확인 안 됨</span> <details class=\"why\"><summary>이유</summary>구독자 확인 안 됨"));
    }

    @Test void onlyUnconfirmedValuesCarryAStatusAndTheLegendSaysWhatConfirmedMeans() throws Exception {
        String createOrder = page("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.contains("<div class=\"line\"><span class=\"k\">확인 상태:</span> <details class=\"ev\">"));
        assertFalse(createOrder.contains("pill ok"));
        assertTrue(createOrder.contains("<details class=\"legend\"><summary>확인됨의 뜻</summary>"));
        assertTrue(createOrder.contains("중간 줄이 실제로 이어지는지는 보지 않는다"));
        assertTrue(page("/services/order-service").contains("createOrder · 응답 확인:"));
        assertTrue(createOrder.contains("<span class=\"tag trap\">함정</span> <span class=\"tag ai\">AI 해석</span>"));
        assertTrue(page("/topics/payment-dlq").contains("<span class=\"tag trap\">함정</span> <span class=\"tag checked\">판정 · 확인함</span>"));
        String topic = page("/topics/KAFKA_REQUEST_PRODUCT_TOPIC");
        assertTrue(topic.contains("<div class=\"line none\"><span class=\"k\">토픽 실제 값:</span> <span class=\"pill none\">저장소에 근거 없음</span> "
                + "<details class=\"why\"><summary>이유</summary>실제 값은 저장소 밖의 환경변수에서 정해짐</details>"));
        assertTrue(topic.contains("<div class=\"line unknown\">order-service.requestProduct → (구독) <span class=\"pill unknown\">확인 안 됨</span>"));
    }

    @Test void endpointShowsHowTheObservedResponseComparesWithTheCode() throws Exception {
        assertTrue(pages.text("/endpoints/coupon-service.CouponController.getUserCoupons").contains("코드와 실제 응답: 차이 없음"));
        assertTrue(pages.text("/endpoints/product-service.ReviewController.getReviewList").contains("코드와 실제 응답: 비교 보류: HTTP 500"));
        assertFalse(pages.text("/endpoints/payment-service.PaymentController.readyPayment").contains("코드와 실제 응답"));
    }

    @Test void bodyTextKeepsEvidenceAsPlainText() {
        String topic = pages.text("/topics/KAFKA_REQUEST_PRODUCT_TOPIC");
        assertTrue(topic.contains("토픽 실제 값: 저장소에 근거 없음 — 실제 값은 저장소 밖의 환경변수에서 정해짐 [order/src/main/resources/application.yml:15, docker-compose.yml:156]"));
        assertFalse(topic.contains("근거 7개"));
    }

    @Test void endpointPageShowsResponseCheckAndItemsWithTrapsFirst() throws Exception {
        assertTrue(page("/endpoints/product-service.ReviewController.getReviewList").contains("응답 기록 있음 500"));

        String createOrder = pages.text("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.contains("업무 규칙·함정 10개"));
        assertTrue(createOrder.indexOf("[함정 · AI 해석]") < createOrder.indexOf("[업무 규칙 · AI 해석]"));
        assertFalse(createOrder.contains("미확인"));
        assertTrue(createOrder.contains("order/src/main/java/com/sparta/hotdeal/order/presentation/controller/OrderController.java:40"));
        assertTrue(createOrder.contains("gateway/src/main/resources/application.yml:45"));
    }

    @Test void pagesShowVerificationScope() {
        assertTrue(pages.text("/endpoints/order-service.OrderController.createOrder").contains("검증 범위: 코드 + 실제 응답\n"));
        assertTrue(pages.text("/endpoints/product-service.ReviewController.getReviewList").contains("검증 범위: 코드 + 실제 응답(오류 500)"));
        assertTrue(pages.text("/endpoints/payment-service.PaymentController.readyPayment").contains("검증 범위: 코드만"));
        assertTrue(pages.text("/services/payment-service").contains("검증 범위: 엔드포인트 9개 중 코드 + 실제 응답 1개, 코드 + 실제 응답(오류) 1개, 코드만 7개"));
        assertTrue(pages.text("/services/gateway-service").contains("검증 범위: 코드만(엔드포인트 없음)"));
        assertTrue(pages.text("/topics/KAFKA_DLQ_TOPIC").contains("검증 범위: 코드만"));
    }

    @Test void fieldShowsUnconfirmedTypeStructure() {
        assertTrue(pages.text("/endpoints/order-service.OrderController.getOrders").contains("타입 구조: 확인 안 됨 — 외부 타입의 구조를 근거 줄만으로 확인할 수 없음"));
    }

    @Test void descriptionsComeFirstApartFromItems() throws Exception {
        String createOrder = page("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.indexOf("<h2>설명</h2>") > 0 && createOrder.indexOf("<h2>설명</h2>") < createOrder.indexOf("<h2>요청</h2>"));
        assertTrue(pages.text("/endpoints/order-service.OrderController.createOrder").contains("[설명 · AI 해석]"));
        assertTrue(page("/topics/KAFKA_REQUEST_PRODUCT_TOPIC").contains("<h2>설명</h2>"));
    }

    @Test void fieldWithoutAnnotationsSaysSo() {
        String createOrder = pages.text("/endpoints/order-service.OrderController.createOrder");
        assertTrue(createOrder.contains("addressId  UUID  @NotNull  확인됨"));
        assertTrue(createOrder.contains("couponId  UUID  어노테이션 없음  확인됨"));
    }

    @Test void serviceAndTopicPagesShowAttachedItems() throws Exception {
        String product = page("/services/product-service");
        assertTrue(product.contains("리뷰 GET은 인증 제외 분기 때문에 JWT가 있어도 사용자 헤더를 생성하지 않는다"));
        assertTrue(product.contains("href=\"/topics/KAFKA_REDUCE_QUANTITY_TOPIC\""));
        String literal = pages.text("/topics/payment-dlq");
        assertTrue(literal.contains("문자열로 지정한 DLQ 토픽 두 개를 받는 쪽이 없다"));
        assertTrue(literal.contains("[함정 · 판정 · 확인함]"));
        assertTrue(page("/services/order-service").contains("order/src/main/java/com/sparta/hotdeal/order/event/producer/OrderEventProducer.java:37"));
    }

    @Test void unresolvedValuesShowStatusAndReason() throws Exception {
        assertTrue(page("/topics/KAFKA_DLQ_TOPIC").contains("저장소에 근거 없음"));
        String unlinked = page("/topics/KAFKA_REQUEST_PRODUCT_TOPIC");
        assertTrue(unlinked.contains("확인 안 됨"));
        assertTrue(unlinked.contains("구독자 확인 안 됨: 같은 환경변수(KAFKA_REQUEST_PRODUCT_TOPIC)를 쓰는 구독을 찾지 못함. 토픽 실제 값은 저장소 밖 환경에서 정해짐 (order-service, spring.kafka.topics.request-product)"));
    }

    @Test void errorResponsesGatewayRejectionAndPageSettingAreInBodies() {
        String validate = pages.text("/endpoints/coupon-service.CouponController.validateCoupon");
        assertTrue(validate.contains("CustomException COUPON_EXPIRED · 오류 코드 CO008, 상태 BAD_REQUEST, 메시지 쿠폰이 만료되었습니다."
                + " · 처리기 coupon-service.GlobalExceptionHandler.handleCustomException: 상태 던진 오류 코드의 값"), validate);
        String address = pages.text("/endpoints/user-service.AddressController.updateAddress");
        assertTrue(address.contains("IllegalArgumentException NOT_ALLOWED_CONTENT · 오류 코드 없음, 상태 없음, 메시지 접근 권한이 없습니다."), address);
        assertTrue(address.contains("메시지 확인 안 됨 — 예외 메시지를 그대로 씀, 값은 던지는 곳에서 정해짐"), address);
        assertTrue(address.contains("토큰이 필요한 경로의 Gateway 거부: AuthenticationFilter: 조건 token == null || !jwtUtil.validateToken(token) → 상태 UNAUTHORIZED · 본문 없음"), address);
        assertTrue(pages.text("/services/coupon-service").contains("@EnableSpringDataWebSupport pageSerializationMode: VIA_DTO"));
        assertTrue(pages.text("/services/coupon-service").contains("NOT_FOUND_COUPON_INFO (ErrorCode) 코드 CO001 · 상태 NOT_FOUND · 메시지 해당 쿠폰 정보 ID가 존재하지 않습니다. 확인됨"));
        assertTrue(search.search("VIA_DTO").stream().anyMatch(r -> r.page().equals("/services/coupon-service")));
    }

    @Test void deployAndMonitoringPageShowsValuesAndAbsentOnes() {
        String page = pages.text("/repository");
        assertNotNull(page);
        assertTrue(page.contains("docker-compose up -d 확인됨"), page);
        assertTrue(page.contains("EC2_PRIVATE_KEY(secrets.EC2_PRIVATE_KEY) 값: 저장소에 근거 없음"), page);
        assertTrue(page.contains("Prometheus · gateway-service: gateway-service:19091 /actuator/prometheus 주기 15s 확인됨"), page);
        assertTrue(page.contains("Grafana 실행 정의: 저장소에 근거 없음"), page);
        assertTrue(search.search("ECR").stream().anyMatch(r -> r.page().equals("/repository")));
    }
}
