package io.hotdealwave.server.page.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.hotdealwave.server.analysis.ValueSource;
import io.hotdealwave.server.flow.dto.ThrownErrorDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static io.hotdealwave.server.analysis.Facts.isConfirmed;
import static io.hotdealwave.server.analysis.Facts.valueOf;

/** Text for errors, exception handlers and gateway rejections. */
@Component("errorsFormatter")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ErrorFormatter {
    private final FactFormatter factFormatter;

    /** An error code constant: code, HTTP status and message as declared. */
    public String errorCode(JsonNode code) {
        return code.path("name").asText() + " (" + code.path("enumType").asText() + ") 코드 " + valueOrStatus(code.path("code"))
                + " · 상태 " + valueOrStatus(code.path("httpStatus")) + " · 메시지 " + valueOrStatus(code.path("message")) + " "
                        + factFormatter.statusWithReason(code) + " " + factFormatter.evidence(code);
    }

    /** An exception handler: what it catches and where status, error code and message come from. */
    public String handler(JsonNode handler) {
        return handler.path("id").asText() + ": " + factFormatter.valueOrStatus(handler.path("exception")) + " → 상태 "
                + handlerValue(handler.path("httpStatus"))
                + " · 본문 " + handler.path("body").path("type").asText() + " 오류 코드 " + handlerValue(handler.path("errorCode"))
                + " · 메시지 " + handlerValue(handler.path("message")) + " " + factFormatter.statusWithReason(handler) + " "
                        + factFormatter.evidence(handler);
    }

    /** An exception an endpoint can raise, then the error code it carries and the handler that answers it. */
    public String thrown(ThrownErrorDto thrown) {
        var link = thrown.link();
        String head = link.path("exception").asText() + (link.path("errorCode").asText().isEmpty() ? "" : " " + link.path("errorCode").asText())
                + (link.path("message").asText().isEmpty() ? "" : " \"" + link.path("message").asText() + "\"");
        String code = thrown.errorCode().isMissingNode() ? "" : " · 오류 코드 " + valueOrStatus(thrown.errorCode().path("code"))
                + ", 상태 " + valueOrStatus(thrown.errorCode().path("httpStatus")) + ", 메시지 " + valueOrStatus(thrown.errorCode().path("message"));
        var handlerNode = thrown.handler();
        String handler = handlerNode.isMissingNode() ? "" : " · 처리기 " + handlerNode.path("id").asText() + ": 상태 "
                + handlerValue(handlerNode.path("httpStatus"))
                + ", 본문 오류 코드 " + handlerValue(handlerNode.path("errorCode")) + ", 메시지 " + handlerValue(handlerNode.path("message"));
        return head + code + handler + " · " + factFormatter.statusWithReason(link) + " " + factFormatter.evidence(link);
    }

    /** A gateway rejection: condition, status and body. */
    public String rejection(JsonNode rejection) {
        return rejection.path("filter").asText() + ": 조건 " + valueOrStatus(rejection.path("condition")) + " → 상태 "
                + valueOrStatus(rejection.path("httpStatus"))
                + " · 본문 " + (isConfirmed(rejection.path("body")) && valueOf(rejection.path("body")).isEmpty()
                        ? "없음" : valueOrStatus(rejection.path("body")))
                + " " + factFormatter.statusWithReason(rejection) + " " + factFormatter.evidence(rejection);
    }

    /** A value without its evidence: empty when the declaration has none, the status and reason when not confirmed. */
    private String valueOrStatus(JsonNode fact) {
        if (!isConfirmed(fact)) return factFormatter.statusWithReason(fact);
        String value = valueOf(fact);
        return value == null || value.isEmpty() ? "없음" : value;
    }

    /** A handler value by where it comes from. */
    private String handlerValue(JsonNode fact) {
        if (ValueSource.FIXED.getText().equals(fact.path("source").asText())) return valueOrStatus(fact);
        if (ValueSource.THROWN_ERROR_CODE.getText().equals(fact.path("source").asText()))
            return isConfirmed(fact) ? ValueSource.THROWN_ERROR_CODE.getText() + "의 값" : factFormatter.statusWithReason(fact);
        return factFormatter.statusWithReason(fact);
    }
}
