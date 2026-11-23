package io.hotdealwave.server.mcp.service;

import io.hotdealwave.server.client.ToolDefinition;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The query tools, read by the MCP registration and by the deep answer path, so both offer the same names and descriptions. */
@Getter
@RequiredArgsConstructor
public enum McpTool {
    FIND_ENDPOINT("find_endpoint", "경로(\"POST /api/v1/orders\"), ID, 핸들러 이름으로 엔드포인트를 찾아 본문(요청·응답 필드, 호출, 검증 범위, 업무 규칙·함정)을 낸다.",
            "query", "엔드포인트 ID, \"메서드 경로\", 경로 또는 핸들러 이름"),
    TRACE_FLOW("trace_flow", "엔드포인트·메시지 발행·구독 ID에서 HTTP 호출과 메시지 발행·구독을 따라간 단계와 부르는 곳을 낸다. 이어지지 않는 곳은 상태와 이유에서 멈춘다.",
            "id", "엔드포인트, 메시지 발행 또는 구독 ID"),
    FIND_SUBSCRIBERS("find_subscribers", "토픽의 발행·구독과 그 연결을 담은 토픽 본문을 낸다.",
            "topic", "토픽 환경변수 이름 또는 코드에 적힌 토픽 이름"),
    SEARCH_DOCS("search_docs", "엔드포인트·토픽·서비스·배포·모니터링 본문을 키워드로 찾아 상위 5개 절(페이지 주소, 절 제목, 점수, 미리보기)을 낸다. 본문 전체는 get_page로 읽는다.",
            "query", "찾을 말. 서비스 이름(order-service 등)이나 API·토픽·설정 같은 말이 있으면 그 범위를 먼저 찾는다"),
    GET_PAGE("get_page", "페이지 주소(/endpoints/ID, /topics/이름, /services/이름, /repository, /revival)로 본문 전체를 낸다. /revival은 띄우는 "
            + "데 필요한 것(외부 자원, 채워야 할 환경변수, 저장소 밖 비밀값, 컨테이너, 확인할 연결, 운영 함정)을 모은 페이지다.",
            "page", "search_docs가 낸 페이지 주소");

    private final String toolName;
    private final String description;
    private final String argument;
    private final String argumentDescription;

    public ToolDefinition definition() { return new ToolDefinition(toolName, description, argument, argumentDescription); }

    public static Optional<McpTool> named(String name) { return Arrays.stream(values()).filter(tool -> tool.toolName.equals(name)).findFirst(); }
}
