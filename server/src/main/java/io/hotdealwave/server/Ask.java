package io.hotdealwave.server;

import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed RAG: hybrid search, one model call, then the citation check decides between an answer and abstaining. */
@Component
class Ask {
    record Cited(String id, String page, String title, String section) {}
    record Answer(AnswerStatus status, String answer, List<Cited> citations, String reason, long inputTokens, long outputTokens) {}

    static final String INSTRUCTIONS = """
            너는 %s 저장소를 넘겨받은 개발자의 질문에, 저장소 분석 문서의 조각만 근거로 답하는 도우미다.
            규칙:
            - 아래에 주어진 조각만 근거로 쓴다. 상식이나 다른 프로젝트 지식으로 보충하지 않는다.
            - 조각의 상태 표시를 그대로 옮긴다. "확인 안 됨", "저장소에 근거 없음"인 값은 확정된 것처럼 쓰지 않고 그 상태와 이유를 밝힌다. 출처가 "AI 해석"인 설명·업무 규칙·함정은 AI 해석이라고 밝힌다.
            - 근거는 조각에 적힌 `파일:줄`을 그대로 옮겨 붙인다. 조각에 없는 `파일:줄`을 만들지 않는다.
            - 조각이 질문의 핵심에 답하면 abstain을 false로 하고 결론부터 답한다. 조각으로 확인되지 않는 부분은 답 안에서 그 부분만 확인할 수 없다고 밝힌다.
            - 질문이 묻는 것에 대한 답이 조각에 없으면 abstain을 true로 하고 answer에 확인할 수 없는 이유를 짧게 쓴다.
            - 답에 쓴 근거 조각의 번호(C1~C8)를 citations에 모두 적는다.
            - 한국어로 간결하게 답한다.""";

    /** What the model reads under one number: an endpoint or topic page whole, otherwise the found section. */
    private record Chunk(String page, String title, String section, String text) {}

    private final Search search;
    private final PageService pages;
    private final AskModel model;
    private final CitationChecker checker;

    private final String instructions;

    Ask(Search search, PageService pages, AskModel model, Analysis analysis, CitationChecker checker) {
        this.search = search; this.pages = pages; this.model = model; this.checker = checker;
        this.instructions = INSTRUCTIONS.formatted(analysis.target().name());
    }

    Answer ask(String question) {
        List<Search.Result> results;
        try {
            results = search.hybrid(question);
        } catch (RuntimeException e) {
            return new Answer(AnswerStatus.ERROR, null, List.of(), AnswerReason.EMBEDDING_FAILURE.format(e.getMessage()), 0, 0);
        }
        if (results.isEmpty()) return new Answer(AnswerStatus.ABSTAIN, null, List.of(), AnswerReason.NO_RESULTS.text(), 0, 0);
        var chunks = new LinkedHashMap<String, Chunk>();
        var input = new StringBuilder("조각:\n");
        for (var result : results) {
            String id = "C" + (chunks.size() + 1);
            // Endpoint and topic pages go whole so fields and connection states come along with whichever section matched.
            var chunk = result.page().startsWith(PageKind.ENDPOINT.path()) || result.page().startsWith(PageKind.TOPIC.path())
                    ? new Chunk(result.page(), result.title(), "", pages.text(result.page()))
                    : new Chunk(result.page(), result.title(), result.section(), search.text(result));
            chunks.put(id, chunk);
            input.append('[').append(id).append("] ").append(chunk.title()).append(chunk.section().isEmpty() ? "" : " — " + chunk.section())
                    .append(" (").append(chunk.page()).append(")\n").append(chunk.text()).append("\n\n");
        }
        input.append("질문: ").append(question);
        AskModel.Reply reply;
        try {
            reply = model.reply(instructions, input.toString());
        } catch (RuntimeException e) {
            return new Answer(AnswerStatus.ERROR, null, List.of(), AnswerReason.MODEL_FAILURE.format(e.getMessage()), 0, 0);
        }
        // The model's own abstain answer says why; an answer the check rejects is not shown.
        if (reply.abstain()) return new Answer(AnswerStatus.ABSTAIN, reply.answer(), List.of(), AnswerReason.MODEL_ABSTAIN.text(), reply.inputTokens(), reply.outputTokens());
        String reason = checker.check(reply, chunks.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().text())));
        if (reason != null) return new Answer(AnswerStatus.ABSTAIN, null, List.of(), reason, reply.inputTokens(), reply.outputTokens());
        var cited = reply.citations().stream().distinct()
                .map(id -> { var c = chunks.get(id); return new Cited(id, c.page(), c.title(), c.section()); }).toList();
        return new Answer(AnswerStatus.ANSWER, reply.answer(), cited, null, reply.inputTokens(), reply.outputTokens());
    }

}
