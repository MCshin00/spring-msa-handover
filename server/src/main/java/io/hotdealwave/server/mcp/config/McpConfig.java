package io.hotdealwave.server.mcp.config;

import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.mcp.service.McpMessage;
import io.hotdealwave.server.mcp.service.McpTool;
import io.hotdealwave.server.mcp.service.McpToolService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.*;
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
                .tools(Arrays.stream(McpTool.values()).map(this::tool).toList())
                .build();
    }

    private SyncToolSpecification tool(McpTool tool) {
        String argument = tool.getArgument();
        var property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", tool.getArgumentDescription());
        var schema = new LinkedHashMap<String, Object>();
        schema.put("properties", Map.of(argument, property));
        schema.put("required", List.of(argument));
        schema.put("type", "object");
        var definition = Tool.builder().name(tool.getToolName()).description(tool.getDescription()).inputSchema(schema).build();
        return new SyncToolSpecification(definition, (context, request) -> {
            String text = mcpToolService.run(tool, String.valueOf(request.arguments().get(argument)));
            if (text == null) return CallToolResult.builder().addTextContent(McpMessage.NOT_FOUND.format(request.arguments().get(argument)))
                    .isError(true).build();
            return CallToolResult.builder().addTextContent(text).build();
        });
    }

}
