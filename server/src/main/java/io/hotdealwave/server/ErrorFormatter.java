package io.hotdealwave.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static io.hotdealwave.server.Analysis.known;
import static io.hotdealwave.server.Analysis.value;

/** Text for errors, exception handlers and gateway rejections. */
@Component("errorsFormatter")
public class ErrorFormatter {
    private final FactFormatter view;

    ErrorFormatter(FactFormatter view) { this.view = view; }

    /** An error code constant: code, HTTP status and message as declared. */
    public String errorCode(JsonNode code) {
        return code.path("name").asText() + " (" + code.path("enumType").asText() + ") 코드 " + shown(code.path("code"))
                + " · 상태 " + shown(code.path("httpStatus")) + " · 메시지 " + shown(code.path("message")) + " " + view.status(code) + " " + view.evidence(code);
    }

    /** An exception handler: what it catches and where status, error code and message come from. */
    public String handler(JsonNode handler) {
        return handler.path("id").asText() + ": " + view.plain(handler.path("exception")) + " → 상태 " + sourced(handler.path("httpStatus"))
                + " · 본문 " + handler.path("body").path("type").asText() + " 오류 코드 " + sourced(handler.path("errorCode"))
                + " · 메시지 " + sourced(handler.path("message")) + " " + view.status(handler) + " " + view.evidence(handler);
    }

    /** An exception an endpoint can raise, then the error code it carries and the handler that answers it. */
    public String thrown(Queries.Thrown thrown) {
        var link = thrown.link();
        String head = link.path("exception").asText() + (link.path("errorCode").asText().isEmpty() ? "" : " " + link.path("errorCode").asText())
                + (link.path("message").asText().isEmpty() ? "" : " \"" + link.path("message").asText() + "\"");
        String code = thrown.errorCode().isMissingNode() ? "" : " · 오류 코드 " + shown(thrown.errorCode().path("code"))
                + ", 상태 " + shown(thrown.errorCode().path("httpStatus")) + ", 메시지 " + shown(thrown.errorCode().path("message"));
        var h = thrown.handler();
        String handler = h.isMissingNode() ? "" : " · 처리기 " + h.path("id").asText() + ": 상태 " + sourced(h.path("httpStatus"))
                + ", 본문 오류 코드 " + sourced(h.path("errorCode")) + ", 메시지 " + sourced(h.path("message"));
        return head + code + handler + " · " + view.status(link) + " " + view.evidence(link);
    }

    /** A gateway rejection: condition, status and body. */
    public String rejection(JsonNode rejection) {
        return rejection.path("filter").asText() + ": 조건 " + shown(rejection.path("condition")) + " → 상태 " + shown(rejection.path("httpStatus"))
                + " · 본문 " + (known(rejection.path("body")) && value(rejection.path("body")).isEmpty() ? "없음" : shown(rejection.path("body")))
                + " " + view.status(rejection) + " " + view.evidence(rejection);
    }

    /** A value without its evidence: empty when the declaration has none, the status and reason when not confirmed. */
    private String shown(JsonNode fact) {
        if (!known(fact)) return view.status(fact);
        String value = value(fact);
        return value == null || value.isEmpty() ? "없음" : value;
    }

    /** A handler value by where it comes from. */
    private String sourced(JsonNode fact) {
        if (ValueSource.FIXED.text().equals(fact.path("source").asText())) return shown(fact);
        if (ValueSource.THROWN_ERROR_CODE.text().equals(fact.path("source").asText()))
            return known(fact) ? ValueSource.THROWN_ERROR_CODE.text() + "의 값" : view.status(fact);
        return view.status(fact);
    }
}
