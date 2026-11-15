package io.hotdealwave.server;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Checks cited chunk identifiers and file lines before an answer is shown. */
@Component
class CitationChecker {
    private static final Pattern FILE_LINES = Pattern.compile("([A-Za-z0-9_.\\-/]+\\.[A-Za-z0-9]+):(\\d+(?:\\s*,\\s*\\d+)*)");

    /** The abstain reason, or null when the answer passes. */
    String check(AskModel.Reply reply, Map<String, String> chunks) {
        if (reply.citations().isEmpty()) return AnswerReason.NO_CITATIONS.text();
        for (var id : reply.citations()) if (!chunks.containsKey(id)) return AnswerReason.MISSING_CHUNK.format(id);
        String cited = String.join("\n", reply.citations().stream().map(id -> chunks.get(id)).toList());
        var m = FILE_LINES.matcher(reply.answer());
        while (m.find()) {
            for (var line : m.group(2).split("\\s*,\\s*")) {
                String fileLine = m.group(1) + ":" + line;
                if (!Pattern.compile(Pattern.quote(fileLine) + "(?!\\d)").matcher(cited).find()) return AnswerReason.MISSING_EVIDENCE.format(fileLine);
            }
        }
        return null;
    }
}
