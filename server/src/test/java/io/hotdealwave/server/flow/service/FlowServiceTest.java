package io.hotdealwave.server.flow.service;

import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.flow.dto.FlowStepDto;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowServiceTest {
    private static FlowService flowService;

    @BeforeAll static void load() throws Exception {
        flowService = new FlowService(new VerifierOutput("examples/hotdealwave/out/analysis.json"));
    }

    private static boolean has(List<FlowStepDto> steps, String from, String to, String status) {
        return steps.stream().anyMatch(step -> step.from().equals(from) && java.util.Objects.equals(step.to(), to) && step.status().equals(status));
    }

    @Test void findEndpointByMethodAndPath() {
        var found = flowService.findEndpoint("POST /api/v1/orders");
        assertEquals(1, found.size());
        var endpoint = found.get(0);
        assertEquals("order-service.OrderController.createOrder", endpoint.path("id").asText());
        assertEquals("basketList", endpoint.at("/parameters/0/schema/fields/0/name").asText());
        assertEquals("ResponseDto<ResPostOrderDto>", endpoint.at("/response/type").asText());
    }

    @Test void traceFlowFollowsCallsTargetsAndStopsAtUnlinkedPublication() {
        String createOrder = "order-service.OrderController.createOrder";
        var flow = flowService.traceFlow(createOrder);
        assertTrue(has(flow.forward(), createOrder, "order-service.UserClient.getAddress", "확인됨"));
        assertTrue(has(flow.forward(), "order-service.UserClient.getAddress", "user-service.AddressController.getAddress", "확인됨"));
        assertTrue(has(flow.forward(), "order-service.CouponClient.useCoupon", "coupon-service.CouponController.useCoupon", "확인됨"));
        assertTrue(has(flow.forward(), createOrder, "order-service.requestProduct", "확인됨"));
        var stop = flow.forward().stream().filter(step -> step.from().equals("order-service.requestProduct")).findFirst().orElseThrow();
        assertNull(stop.to());
        assertEquals("확인 안 됨", stop.status());
        assertTrue(stop.reason().contains("구독자 확인 안 됨"));

        var cancel = flowService.traceFlow("order-service.OrderController.cancelOrder");
        assertTrue(has(cancel.forward(), "order-service.cancelPayment", "payment-service.cancel", "확인됨"));

        var callers = flowService.traceFlow("user-service.AddressController.getAddress").callers();
        assertTrue(has(callers, createOrder, "order-service.UserClient.getAddress", "확인됨"));
    }

    @Test void findSubscribersByEnvironmentName() {
        var topic = flowService.findSubscribers("KAFKA_DLQ_TOPIC");
        assertEquals(List.of("order-service.publishDlq", "order-service.recoverDlq", "payment-service.publishDlq"),
                topic.publications().stream().map(publication -> publication.path("id").asText()).toList());
        assertEquals(List.of("order-service.dlq", "payment-service.dlq"),
                topic.subscriptions().stream().map(step -> step.path("id").asText()).toList());
    }
}
