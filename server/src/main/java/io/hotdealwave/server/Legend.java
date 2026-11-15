package io.hotdealwave.server;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** What "확인됨" checked for the values a page or tool result shows, from the verifier output. */
@Component
class Legend {
    static final List<OutputKey> FLOW = List.of(OutputKey.CALLS, OutputKey.HTTP_CALLS, OutputKey.MESSAGE_LINKS);

    private final Analysis analysis;

    Legend(Analysis analysis) { this.analysis = analysis; }

    List<String> lines(PageKind... kinds) {
        return lines(Arrays.stream(kinds).flatMap(kind -> kind.arrays().stream()).toList());
    }

    private List<String> lines(List<OutputKey> kinds) {
        var confirmed = analysis.get(OutputKey.CONFIRMED);
        return kinds.stream().distinct().map(k -> confirmed.path(k.text()).asText("")).filter(l -> !l.isEmpty()).toList();
    }

    /** Head of a tool result; a result over several kinds of page gets the arrays of each once. */
    String text(PageKind... kinds) {
        return text(Arrays.stream(kinds).flatMap(kind -> kind.arrays().stream()).toList());
    }

    String text(List<OutputKey> kinds) {
        var lines = lines(kinds);
        return lines.isEmpty() ? "" : "확인됨의 뜻\n" + String.join("\n", lines.stream().map(l -> "- " + l).toList()) + "\n\n";
    }

    /** Folded legend under a page title. Pages do not mark confirmed values, so the legend says so. */
    String html(PageKind kind) {
        var lines = lines(kind);
        if (lines.isEmpty()) return "";
        return "<details class=\"legend\"><summary>확인됨의 뜻</summary><p>상태가 붙지 않은 값은 확인됨이다. 확인됨은 다음만 확인했다.</p><ul>"
                + String.join("", lines.stream().map(l -> "<li>" + HtmlUtils.htmlEscape(l) + "</li>").toList()) + "</ul></details>";
    }
}
