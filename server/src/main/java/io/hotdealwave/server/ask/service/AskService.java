package io.hotdealwave.server.ask.service;

import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.ask.AnswerReason;
import io.hotdealwave.server.ask.AnswerStatus;
import io.hotdealwave.server.ask.dto.AskResponseDto;
import io.hotdealwave.server.ask.dto.CitedChunkDto;
import io.hotdealwave.server.client.LanguageModel;
import io.hotdealwave.server.client.ModelReply;
import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.service.SearchService;
import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed RAG: hybrid search, one model call, then the citation check decides between an answer and abstaining. */
@Component
public class AskService {
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

    private final SearchService searchService;
    private final StartChunks startChunks;
    private final LanguageModel languageModel;
    private final CitationChecker citationChecker;

    private final String instructions;

    AskService(SearchService searchService, StartChunks startChunks, LanguageModel languageModel, VerifierOutput verifierOutput,
            CitationChecker citationChecker) {
        this.searchService = searchService;
        this.startChunks = startChunks;
        this.languageModel = languageModel;
        this.citationChecker = citationChecker;
        this.instructions = INSTRUCTIONS.formatted(verifierOutput.target().name());
    }

    public AskResponseDto ask(String question) {
        List<SearchResultDto> results;
        try {
            results = searchService.hybridSearch(question);
        } catch (RuntimeException exception) {
            return new AskResponseDto(AnswerStatus.ERROR, null, List.of(), AnswerReason.EMBEDDING_FAILURE.format(exception.getMessage()), 0, 0);
        }
        if (results.isEmpty()) return new AskResponseDto(AnswerStatus.ABSTAIN, null, List.of(), AnswerReason.NO_RESULTS.getText(), 0, 0);
        var start = startChunks.build(results, question);
        var chunks = start.chunks();
        ModelReply reply;
        try {
            reply = languageModel.reply(instructions, start.input());
        } catch (RuntimeException exception) {
            return new AskResponseDto(AnswerStatus.ERROR, null, List.of(), AnswerReason.MODEL_FAILURE.format(exception.getMessage()), 0, 0);
        }
        // The model's own abstain answer says why; an answer the check rejects is not shown.
        if (reply.abstain()) return new AskResponseDto(AnswerStatus.ABSTAIN, reply.answer(), List.of(),
                AnswerReason.MODEL_ABSTAIN.getText(), reply.inputTokens(), reply.outputTokens());
        String reason = citationChecker.check(reply, chunks.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().text())));
        if (reason != null) return new AskResponseDto(AnswerStatus.ABSTAIN, null, List.of(), reason, reply.inputTokens(), reply.outputTokens());
        var cited = reply.citations().stream().distinct()
                .map(id -> {
                    var chunk = chunks.get(id);
                    return new CitedChunkDto(id, chunk.page(), chunk.title(), chunk.section());
                }).toList();
        return new AskResponseDto(AnswerStatus.ANSWER, reply.answer(), cited, null, reply.inputTokens(), reply.outputTokens());
    }

}
