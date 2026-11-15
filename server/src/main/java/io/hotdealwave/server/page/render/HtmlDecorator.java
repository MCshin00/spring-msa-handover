package io.hotdealwave.server.page.render;

import io.hotdealwave.server.analysis.ConfirmationStatus;
import io.hotdealwave.server.analysis.FactStatus;
import io.hotdealwave.server.analysis.Target;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Display-only markup over a rendered body: evidence lists become folded links, statuses become badges,
 * a leading "key: " becomes a label, and lines get a status class.
 * Search, MCP and the model read the body before this, so their text does not change.
 */
public final class HtmlDecorator {
    /** A recorded response file of any target; its lines open on the response page. */
    public static final Pattern RESPONSE_FILE = Pattern.compile("responses/(.+)");

    private static final Pattern LINE = Pattern.compile("<p class=\"((?:line|meta)[^\"]*)\">(.*?)</p>", Pattern.DOTALL);
    private static final Pattern EVIDENCE = Pattern.compile("\\[([^\\[\\]<>]+?:\\d+(?:, [^\\[\\]<>]+?:\\d+)*)]");
    private static final Pattern KEY = Pattern.compile("^([가-힣A-Za-z()· ]{1,16}): ");
    /** A status standing on its own, not part of a sentence such as "구독자 확인 안 됨:". */
    private static final Pattern STATUS = Pattern.compile("(?<=^|[\\s>(])(" + String.join("|", Arrays.stream(FactStatus.values())
            .map(status -> Pattern.quote(status.getText())).toList()) + ")(?=$|[\\s<\\[])");
    /** A status the page does not mark: the value standing alone is confirmed, and the legend says so. */
    private static final Pattern CONFIRMED = Pattern.compile("(?:\\s·)?\\s?(?<=^|[\\s>(])" + Pattern.quote(FactStatus.CONFIRMED.getText())
            + "(?=$|[\\s<\\[])");
    /** The reason after an unconfirmed status, up to its evidence or the end of the line. */
    private static final Pattern WHY = Pattern.compile("(" + Pattern.quote(FactStatus.UNCONFIRMED.getText()) + "|"
            + Pattern.quote(FactStatus.NO_REPOSITORY_EVIDENCE.getText()) + ") — (.+?)(?=\\s\\[|\\s·\\s|<|$)");
    private static final Pattern METHOD = Pattern.compile("(?<=^|<span class=\"k\">HTTP 메서드:</span> |>)(GET|POST|PUT|PATCH|DELETE)(?= |<|$)");
    /** The reason after a response check in a service page row. The endpoint page shows it whole. */
    private static final Pattern REASON = Pattern.compile("(응답 확인: [^(<]*?) \\(([^<]+)\\)(?=</span>)");
    private static final Pattern TARGET_LINE = Pattern.compile("target/[^/\\s;:]+/([^\\s;:]+):(\\d+)");
    /** An item tag "[kind · source (· status)]" split into a kind tag and a source tag. */
    private static final Pattern TAG = Pattern.compile("<span class=\"tag (\\w+)\">\\[([^·\\]]+) · ([^\\]]+)]</span>");

    private HtmlDecorator() {}

    static String html(String html, Target target) {
        String lines = LINE.matcher(html).replaceAll(matcher -> {
            String inner = matcher.group(2).contains("\n") ? matcher.group(2).strip().replaceAll("\\s*\\n\\s*", " ") : matcher.group(2);
            return Matcher.quoteReplacement("<div class=\"" + matcher.group(1) + status(inner) + "\">" + mark(inner, target.source()) + "</div>");
        });
        return TAG.matcher(lines).replaceAll(matcher -> Matcher.quoteReplacement("<span class=\"tag " + matcher.group(1) + "\">" + matcher.group(2)
                + "</span> <span class=\"tag " + (matcher.group(3).endsWith(ConfirmationStatus.CONFIRMED.getText()) ? "checked" : "ai")
                        + "\">" + matcher.group(3) + "</span>"));
    }

    /** The weakest status on the line decides its stripe. */
    private static String status(String inner) {
        String text = inner.replaceAll("<[^>]+>", "");
        if (text.contains(FactStatus.NO_REPOSITORY_EVIDENCE.getText())) return " none";
        if (text.contains(FactStatus.UNCONFIRMED.getText())) return " unknown";
        return "";
    }

    private static String mark(String inner, String source) {
        String marked = REASON.matcher(inner).replaceFirst(matcher -> Matcher.quoteReplacement(matcher.group(1)
                + " <details class=\"why\"><summary>이유</summary>(" + TARGET_LINE.matcher(matcher.group(2))
                        .replaceAll(targetMatcher -> Matcher.quoteReplacement(
                        "<a href=\"" + source + targetMatcher.group(1) + "#L" + targetMatcher.group(2) + "\">" + targetMatcher.group()
                                + "</a>")) + ")</details>"));
        marked = KEY.matcher(marked).replaceFirst(matcher -> Matcher.quoteReplacement("<span class=\"k\">" + matcher.group(1) + ":</span> "));
        marked = CONFIRMED.matcher(marked).replaceAll("");
        marked = WHY.matcher(marked).replaceAll(matcher -> Matcher.quoteReplacement(matcher.group(1)
                + " <details class=\"why\"><summary>이유</summary>" + matcher.group(2) + "</details>"));
        marked = STATUS.matcher(marked).replaceAll(matcher -> "<span class=\"pill " + (matcher.group(1)
                .equals(FactStatus.UNCONFIRMED.getText()) ? "unknown" : "none")
                + "\">" + matcher.group(1) + "</span>");
        marked = METHOD.matcher(marked).replaceAll(matcher -> "<span class=\"method " + matcher.group(1).toLowerCase() + "\">"
                + matcher.group(1) + "</span>");
        return evidence(marked, source);
    }

    private static String evidence(String inner, String source) {
        return EVIDENCE.matcher(inner).replaceAll(matcher -> {
            var parts = matcher.group(1).split(", ");
            String links = Arrays.stream(parts).map(part -> link(part, source)).collect(Collectors.joining(", "));
            return Matcher.quoteReplacement("<details class=\"ev\"><summary>근거 " + parts.length + "개</summary>["+ links + "]</details>");
        });
    }

    private static String link(String evidence, String source) {
        int colon = evidence.lastIndexOf(':');
        String file = evidence.substring(0, colon), line = evidence.substring(colon + 1);
        var response = RESPONSE_FILE.matcher(file);
        String href = response.matches() ? "/responses/" + response.group(1) : source + file;
        return "<a href=\"" + href + "#L" + line + "\">" + evidence + "</a>";
    }
}
