package io.hotdealwave.server.mcp.config;

import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.mcp.service.McpMessage;
import io.hotdealwave.server.mcp.service.McpToolService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.*;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The query layer as MCP tools at /mcp. Results are the page body text, so status and evidence travel with each value. */
@Configuration
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class McpConfig {
    private final VerifierOutput verifierOutput;
    private final McpToolService mcpToolService;

    @Bean HttpServletStatelessServerTransport mcpTransport() {
        return HttpServletStatelessServerTransport.builder().messageEndpoint("/mcp").build();
    }

    @Bean ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(HttpServletStatelessServerTransport transport) {
        return new ServletRegistrationBean<>(transport, "/mcp");
    }

    @Bean(destroyMethod = "close") McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport) {
        var target = verifierOutput.target();
        return McpServer.sync(transport).serverInfo("hotdealwave-handover", "1")
                .instructions(target.name() + " 저장소의 고정 커밋 " + target.shortCommit() + "을 분석한 문서다. "
                        + "도구 결과의 값마다 확인 상태(확인됨, 확인 안 됨, 저장소에 근거 없음)와 이유, 근거 파일:줄이 붙는다. "
                        + "결과 머리의 \"확인됨의 뜻\"은 확인됨이 무엇을 확인했는지 밝힌다. "
                        + "답할 때 확인되지 않은 값은 그 상태와 이유를 함께 밝힌다.")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(
                        tool("find_endpoint", "경로(\"POST /api/v1/orders\"), ID, 핸들러 이름으로 엔드포인트를 찾아 본문(요청·응답 필드, 호출, 검증 범위, 업무 규칙·함정)을 낸다.",
                                "query", "엔드포인트 ID, \"메서드 경로\", 경로 또는 핸들러 이름", mcpToolService::findEndpoint),
                        tool("trace_flow", "엔드포인트·메시지 발행·구독 ID에서 HTTP 호출과 메시지 발행·구독을 따라간 단계와 부르는 곳을 낸다. 이어지지 않는 곳은 상태와 이유에서 멈춘다.",
                                "id", "엔드포인트, 메시지 발행 또는 구독 ID", mcpToolService::traceFlow),
                        tool("find_subscribers", "토픽의 발행·구독과 그 연결을 담은 토픽 본문을 낸다.",
                                "topic", "토픽 환경변수 이름 또는 코드에 적힌 토픽 이름", mcpToolService::findSubscribers),
                        tool("search_docs", "엔드포인트·토픽·서비스·배포·모니터링 본문을 키워드로 찾아 상위 5개 절(페이지 주소, 절 제목, 점수, 미리보기)을 낸다. 본문 전체는 get_page로 읽는다.",
                                "query", "찾을 말. 서비스 이름(order-service 등)이나 API·토픽·설정 같은 말이 있으면 그 범위를 먼저 찾는다", mcpToolService::searchDocs),
                        tool("get_page",
                                "페이지 주소(/endpoints/ID, /topics/이름, /services/이름, /repository, /revival)로 본문 전체를 낸다. /revival은 띄우는 "
                                        + "데 필요한 것(외부 자원, 채워야 할 환경변수, 저장소 밖 비밀값, 컨테이너, 확인할 연결, 운영 함정)을 모은 페이지다.",
                                "page", "search_docs가 낸 페이지 주소", mcpToolService::page))
                .build();
    }

    private SyncToolSpecification tool(String name, String description, String argument, String argumentDescription, Function<String, String> body) {
        var property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", argumentDescription);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("properties", Map.of(argument, property));
        schema.put("required", List.of(argument));
        schema.put("type", "object");
        return new SyncToolSpecification(Tool.builder().name(name).description(description).inputSchema(schema).build(), (context, request) -> {
            String text = body.apply(String.valueOf(request.arguments().get(argument)));
            if (text == null) return CallToolResult.builder().addTextContent(McpMessage.NOT_FOUND.format(request.arguments().get(argument)))
                    .isError(true).build();
            return CallToolResult.builder().addTextContent(text).build();
        });
    }

}
