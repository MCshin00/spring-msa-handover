package io.hotdealwave.server.page.render;

import io.hotdealwave.server.analysis.OutputKey;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.page.PageKind;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** What "확인됨" checked for the values a page or tool result shows, from the verifier output. */
@Component
@RequiredArgsConstructor
public class ConfirmedLegend {
    public static final List<OutputKey> FLOW = List.of(OutputKey.CALLS, OutputKey.HTTP_CALLS, OutputKey.MESSAGE_LINKS);

    private final VerifierOutput verifierOutput;

    List<String> lines(PageKind... kinds) {
        return lines(Arrays.stream(kinds).flatMap(kind -> kind.getArrays().stream()).toList());
    }

    private List<String> lines(List<OutputKey> kinds) {
        var confirmed = verifierOutput.get(OutputKey.CONFIRMED);
        return kinds.stream().distinct().map(key -> confirmed.path(key.getText()).asText("")).filter(line -> !line.isEmpty()).toList();
    }

    /** Head of a tool result; a result over several kinds of page gets the arrays of each once. */
    public String text(PageKind... kinds) {
         return text(Arrays.stream(kinds).flatMap(kind -> kind.getArrays().stream()).toList());
    }

    public String text(List<OutputKey> kinds) {
        var lines = lines(kinds);
        return lines.isEmpty() ? "" : "확인됨의 뜻\n" + String.join("\n", lines.stream().map(line -> "- " + line).toList()) + "\n\n";
    }

    /** Folded legend under a page title. Pages do not mark confirmed values, so the legend says so. */
    String html(PageKind kind) {
        var lines = lines(kind);
        if (lines.isEmpty()) return "";
        return "<details class=\"legend\"><summary>확인됨의 뜻</summary><p>상태가 붙지 않은 값은 확인됨이다. 확인됨은 다음만 확인했다.</p><ul>"
                + String.join("", lines.stream().map(line -> "<li>" + HtmlUtils.htmlEscape(line) + "</li>").toList()) + "</ul></details>";
    }
}
