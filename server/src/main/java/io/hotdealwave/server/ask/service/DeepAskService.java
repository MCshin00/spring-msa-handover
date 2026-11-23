package io.hotdealwave.server.ask.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.ask.AnswerReason;
import io.hotdealwave.server.ask.AnswerStatus;
import io.hotdealwave.server.ask.StepMark;
import io.hotdealwave.server.ask.ToolResultMessage;
import io.hotdealwave.server.ask.dto.DeepAskResponseDto;
import io.hotdealwave.server.ask.dto.DeepCitationDto;
import io.hotdealwave.server.ask.dto.ToolStepDto;
import io.hotdealwave.server.client.LanguageModel;
import io.hotdealwave.server.client.ModelInput;
import io.hotdealwave.server.client.ModelReply;
import io.hotdealwave.server.client.ModelTurn;
import io.hotdealwave.server.client.ToolDefinition;
import io.hotdealwave.server.flow.service.FlowService;
import io.hotdealwave.server.mcp.service.McpMessage;
import io.hotdealwave.server.mcp.service.McpTool;
import io.hotdealwave.server.mcp.service.McpToolService;
import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.service.PageService;
import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.service.SearchService;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Starts from the same chunks as the fixed path, lets the same model call the MCP tools until it answers, then applies the same citation
 * check over every numbered text. Each request leaves one trace line.
 */
@Service
public class DeepAskService {
    static final String INSTRUCTIONS = """
            너는 %s 저장소를 넘겨받은 개발자의 질문에, 저장소 분석 문서의 조각만 근거로 답하는 도우미다.
            규칙:
            - 아래에 주어진 조각만 근거로 쓴다. 상식이나 다른 프로젝트 지식으로 보충하지 않는다.
            - 조각의 상태 표시를 그대로 옮긴다. "확인 안 됨", "저장소에 근거 없음"인 값은 확정된 것처럼 쓰지 않고 그 상태와 이유를 밝힌다. 출처가 "AI 해석"인 설명·업무 규칙·함정은 AI 해석이라고 밝힌다.
            - 근거는 조각에 적힌 `파일:줄`을 그대로 옮겨 붙인다. 조각에 없는 `파일:줄`을 만들지 않는다.
            - 조각이 질문의 핵심에 답하면 abstain을 false로 하고 결론부터 답한다. 조각으로 확인되지 않는 부분은 답 안에서 그 부분만 확인할 수 없다고 밝힌다.
            - 조각으로 답할 수 없으면 먼저 도구로 더 찾는다.
            - 도구 결과 앞의 번호를 조각 번호와 똑같이 인용한다.
            - search_docs 결과는 근거로 쓰지 않는다. 찾은 페이지의 본문을 읽은 뒤 그 번호를 인용한다.
            - 근거가 모이면 더 찾지 않고 답한다.
            - 도구로 찾아본 뒤에도 질문이 묻는 것에 대한 답이 없으면 abstain을 true로 하고 answer에 확인할 수 없는 이유를 짧게 쓴다.
            - 답에 쓴 근거 조각의 번호(C1~C8)를 citations에 모두 적는다.
            - 한국어로 간결하게 답한다.""";
    /** Tool calls run per request. The MCP condition in stage 9 made about eleven per question. */
    static final int MAX_TOOL_CALLS = 15;
    /** Input tokens of one call after which tools are turned off; gpt-6-luna charges double over 272,000. */
    static final long MAX_INPUT_TOKENS = 200_000;
    private static final List<ToolDefinition> TOOLS = Arrays.stream(McpTool.values()).map(McpTool::definition).toList();

    /** A numbered text the citation check reads: a starting chunk, or a tool result with its tool and argument. */
    private record Numbered(String text, String page, String title, String section, String tool, String argument) {}

    private record Ending(AnswerStatus status, String answer, List<DeepCitationDto> citations, String reason) {}

    private final SearchService searchService;
    private final StartChunks startChunks;
    private final LanguageModel languageModel;
    private final CitationChecker citationChecker;
    private final McpToolService mcpToolService;
    private final FlowService flowService;
    private final PageService pageService;
    private final AskTrace askTrace;
    private final ObjectMapper json = new ObjectMapper();

    private final String instructions;

    DeepAskService(SearchService searchService, StartChunks startChunks, LanguageModel languageModel, CitationChecker citationChecker,
            McpToolService mcpToolService, FlowService flowService, PageService pageService, AskTrace askTrace, VerifierOutput verifierOutput) {
        this.searchService = searchService;
        this.startChunks = startChunks;
        this.languageModel = languageModel;
        this.citationChecker = citationChecker;
        this.mcpToolService = mcpToolService;
        this.flowService = flowService;
        this.pageService = pageService;
        this.askTrace = askTrace;
        this.instructions = INSTRUCTIONS.formatted(verifierOutput.target().name());
    }

    public DeepAskResponseDto ask(String question) {
        long started = System.nanoTime();
        var run = new Run();
        var ending = run.answer(question);
        long elapsed = millisSince(started);
        var last = run.last == null ? null : new AskTrace.Output(run.last.answer(), run.last.citations(), run.last.abstain());
        askTrace.append(new AskTrace.Line(OffsetDateTime.now().toString(), question, ending.status(), ending.reason(), run.limitReached, elapsed,
                run.modelCalls, run.toolTraces, last));
        return new DeepAskResponseDto(ending.status(), ending.answer(), ending.citations(), ending.reason(), run.steps, run.modelCalls.size(),
                run.toolCalls, run.limitReached, run.modelCalls.stream().mapToLong(AskTrace.ModelCall::inputTokens).sum(),
                run.modelCalls.stream().mapToLong(AskTrace.ModelCall::cachedInputTokens).sum(),
                run.modelCalls.stream().mapToLong(AskTrace.ModelCall::outputTokens).sum(), elapsed);
    }

    private static long millisSince(long started) { return (System.nanoTime() - started) / 1_000_000; }

    /** One request's numbers, input and steps. */
    private class Run {
        final Map<String, Numbered> numbered = new LinkedHashMap<>();
        /** Pages whose whole body already has a number. */
        final Map<String, String> pageNumbers = new HashMap<>();
        /** tool(argument) to the number or mark it came to. */
        final Map<String, String> seen = new HashMap<>();
        final List<ModelInput> input = new ArrayList<>();
        final List<ToolStepDto> steps = new ArrayList<>();
        final List<AskTrace.ToolCall> toolTraces = new ArrayList<>();
        final List<AskTrace.ModelCall> modelCalls = new ArrayList<>();
        int toolCalls;
        boolean limitReached;
        ModelReply last;

        Ending answer(String question) {
            List<SearchResultDto> results;
            try {
                results = searchService.hybridSearch(question);
            } catch (RuntimeException exception) {
                return new Ending(AnswerStatus.ERROR, null, List.of(), AnswerReason.EMBEDDING_FAILURE.format(exception.getMessage()));
            }
            var start = startChunks.build(results, question);
            start.chunks().forEach((id, chunk) -> {
                numbered.put(id, new Numbered(chunk.text(), chunk.page(), chunk.title(), chunk.section(), null, null));
                if (StartChunks.whole(chunk.page())) pageNumbers.putIfAbsent(chunk.page(), id);
            });
            input.add(new ModelInput.Text(start.input()));
            boolean allowTools = true;
            while (true) {
                long started = System.nanoTime();
                ModelTurn turn;
                try {
                    turn = languageModel.replyWithTools(instructions, TOOLS, input, allowTools);
                } catch (RuntimeException exception) {
                    return new Ending(AnswerStatus.ERROR, null, List.of(), AnswerReason.MODEL_FAILURE.format(exception.getMessage()));
                }
                modelCalls.add(new AskTrace.ModelCall(turn.inputTokens(), turn.cachedInputTokens(), turn.outputTokens(), millisSince(started)));
                if (turn.toolCalls().isEmpty()) return finish(turn.reply());
                for (var call : turn.toolCalls()) {
                    input.add(call);
                    input.add(new ModelInput.ToolResult(call.callId(), run(call)));
                }
                // The tool calls of this response are handled; the next call answers with tool choice off.
                if (toolCalls >= MAX_TOOL_CALLS || turn.inputTokens() > MAX_INPUT_TOKENS) {
                    allowTools = false;
                    limitReached = true;
                }
            }
        }

        /** The model's own abstain answer says why; an answer the check rejects is not shown and stays only in the trace. */
        Ending finish(ModelReply reply) {
            last = reply;
            if (reply.abstain()) return new Ending(AnswerStatus.ABSTAIN, reply.answer(), List.of(), AnswerReason.MODEL_ABSTAIN.getText());
            String reason = citationChecker.check(reply, numbered.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> entry.getValue().text())));
            if (reason != null) return new Ending(AnswerStatus.ABSTAIN, null, List.of(), reason);
            var cited = reply.citations().stream().distinct().map(id -> {
                var text = numbered.get(id);
                return new DeepCitationDto(id, text.page(), text.title(), text.section(), text.tool(), text.argument());
            }).toList();
            return new Ending(AnswerStatus.ANSWER, reply.answer(), cited, null);
        }

        /** The result text sent back for one tool call. Every call counts toward the limit, repeats and misses included. */
        String run(ModelInput.ToolCall call) {
            toolCalls++;
            var tool = McpTool.named(call.tool());
            String argument = tool.map(found -> argument(call.arguments(), found.getArgument())).orElse(null);
            String shown = argument == null ? call.arguments() : argument;
            if (toolCalls > MAX_TOOL_CALLS) return step(call.tool(), shown, StepMark.LIMIT.getText(), ToolResultMessage.LIMIT.format(MAX_TOOL_CALLS));
            if (argument == null) return step(call.tool(), shown, StepMark.NOT_FOUND.getText(), ToolResultMessage.UNREADABLE.format(call.arguments()));
            String key = call.tool() + "(" + argument + ")";
            String earlier = seen.get(key);
            if (earlier != null) return numbered.containsKey(earlier) ? already(call.tool(), argument, earlier)
                    : step(call.tool(), argument, earlier, ToolResultMessage.ALREADY_SEARCHED.getText());
            String page = switch (tool.get()) {
                case GET_PAGE -> argument;
                case FIND_SUBSCRIBERS -> PageKind.TOPIC.getPath() + argument;
                default -> null;
            };
            if (page != null && pageNumbers.containsKey(page)) return already(call.tool(), argument, pageNumbers.get(page));
            String text = mcpToolService.run(tool.get(), argument);
            if (text == null) {
                seen.put(key, StepMark.NOT_FOUND.getText());
                return step(call.tool(), argument, StepMark.NOT_FOUND.getText(), McpMessage.NOT_FOUND.format(argument));
            }
            if (tool.get() == McpTool.SEARCH_DOCS) {
                seen.put(key, StepMark.SEARCH_RESULT.getText());
                return step(call.tool(), argument, StepMark.SEARCH_RESULT.getText(), text);
            }
            String id = "C" + (numbered.size() + 1);
            String output = "[" + id + "] " + key + "\n" + text;
            String read = tool.get() == McpTool.FIND_ENDPOINT ? endpointPage(argument) : page;
            numbered.put(id, new Numbered(output, read, read == null ? null : pageService.sections().get(read).get(0).title(), null, call.tool(),
                    argument));
            if (read != null) pageNumbers.putIfAbsent(read, id);
            seen.put(key, id);
            return step(call.tool(), argument, id, output);
        }

        private String already(String tool, String argument, String id) {
            return step(tool, argument, StepMark.ALREADY.format(id), ToolResultMessage.ALREADY_NUMBERED.format(id));
        }

        private String step(String tool, String argument, String mark, String output) {
            steps.add(new ToolStepDto(tool, argument, mark));
            toolTraces.add(new AskTrace.ToolCall(tool, argument, mark, output.length()));
            return output;
        }

        /** The one endpoint page find_endpoint read, or null when it found several. */
        private String endpointPage(String query) {
            var found = flowService.findEndpoint(query);
            return found.size() == 1 ? PageKind.ENDPOINT.getPath() + found.get(0).path("id").asText() : null;
        }

        /** The tool's one string argument with surrounding spaces removed, or null when the model's JSON cannot be read. */
        private String argument(String arguments, String name) {
            try {
                var value = json.readTree(arguments).path(name);
                return value.isTextual() ? value.asText().strip() : null;
            } catch (JsonProcessingException exception) {
                return null;
            }
        }
    }
}
