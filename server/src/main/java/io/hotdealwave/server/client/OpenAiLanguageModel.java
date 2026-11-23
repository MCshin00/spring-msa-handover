package io.hotdealwave.server.client;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonSchemaLocalValidation;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ToolChoiceOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gpt-6-luna through the Responses API with a fixed output format, and text-embedding-3-large. The key comes from OPENAI_API_KEY. */
@Component
public class OpenAiLanguageModel implements LanguageModel {
    public static final String MODEL = "gpt-6-luna";
    static final long MAX_OUTPUT_TOKENS = 1500;
    static final EmbeddingModel EMBEDDING_MODEL = EmbeddingModel.TEXT_EMBEDDING_3_LARGE;
    /** Characters and inputs per embedding request, kept under the request limits even for Korean text. */
    static final int REQUEST_CHARS = 60_000, REQUEST_INPUTS = 2048;

    private record Output(
            @JsonPropertyDescription("질문에 대한 답. 보류할 때는 확인할 수 없는 이유") String answer,
            @JsonPropertyDescription("근거로 쓴 조각 번호. 예: [\"C1\", \"C3\"]") List<String> citations,
            @JsonPropertyDescription("질문이 묻는 것에 대한 답이 조각에 없으면 true") boolean abstain) {}

    /** The same fields for the tool-using path, described for answers drawn from tool results too. */
    private record ToolOutput(
            @JsonPropertyDescription("질문에 대한 답. 보류할 때는 확인할 수 없는 이유") String answer,
            @JsonPropertyDescription("근거로 쓴 조각과 도구 결과의 번호. 예: [\"C1\", \"C9\"]") List<String> citations,
            @JsonPropertyDescription("도구로 찾아본 뒤에도 질문의 핵심에 대한 답이 없으면 true") boolean abstain) {}

    private final OpenAIClient client;

    public OpenAiLanguageModel(@Value("${OPENAI_API_KEY:#{null}}") String key) {
        this.client = key == null || key.isBlank() ? null : OpenAIOkHttpClient.builder().apiKey(key).build();
    }

    @Override public String name() { return MODEL; }

    @Override
    public ModelReply reply(String instructions, String input) {
        requireKey();
        var params = ResponseCreateParams.builder().model(MODEL).instructions(instructions).input(input)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.NONE).build()).maxOutputTokens(MAX_OUTPUT_TOKENS)
                .text(Output.class, JsonSchemaLocalValidation.YES).build();
        var response = client.responses().create(params);
        // Take the first message that carries the structured output; an empty extra message is skipped.
        var output = response.output().stream().flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                .findFirst().orElseThrow(() -> new IllegalStateException(LanguageModelExceptionMessage.MISSING_ANSWER.getText()));
        var usage = response.usage();
        return new ModelReply(output.answer(), output.citations(), output.abstain(),
                usage.map(usageValue -> usageValue.inputTokens()).orElse(0L), usage.map(usageValue -> usageValue.outputTokens()).orElse(0L));
    }

    @Override
    public ModelTurn replyWithTools(String instructions, List<ToolDefinition> tools, List<ModelInput> input, boolean allowTools) {
        requireKey();
        var items = new ArrayList<ResponseInputItem>();
        for (var item : input) {
            if (item instanceof ModelInput.Text text) items.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(EasyInputMessage.Role.USER).content(text.text()).build()));
            else if (item instanceof ModelInput.ToolCall call) items.add(ResponseInputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                    .callId(call.callId()).name(call.tool()).arguments(call.arguments()).build()));
            else if (item instanceof ModelInput.ToolResult result) items.add(ResponseInputItem.ofFunctionCallOutput(
                    ResponseInputItem.FunctionCallOutput.builder().callId(result.callId()).output(result.output()).build()));
        }
        var builder = ResponseCreateParams.builder().model(MODEL).instructions(instructions).inputOfResponse(items)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.NONE).build()).maxOutputTokens(MAX_OUTPUT_TOKENS)
                .toolChoice(allowTools ? ToolChoiceOptions.AUTO : ToolChoiceOptions.NONE);
        for (var tool : tools) builder.addTool(functionTool(tool));
        var response = client.responses().create(builder.text(ToolOutput.class, JsonSchemaLocalValidation.YES).build());
        var usage = response.usage();
        long inputTokens = usage.map(usageValue -> usageValue.inputTokens()).orElse(0L);
        long cachedTokens = usage.map(usageValue -> usageValue.inputTokensDetails().cachedTokens()).orElse(0L);
        long outputTokens = usage.map(usageValue -> usageValue.outputTokens()).orElse(0L);
        // Any tool call makes this a tool turn, and text in the same response is not used.
        var calls = response.output().stream().filter(item -> item.isFunctionCall()).map(item -> item.asFunctionCall())
                .map(call -> new ModelInput.ToolCall(call.callId(), call.name(), call.arguments())).toList();
        if (!calls.isEmpty()) return new ModelTurn(calls, null, inputTokens, cachedTokens, outputTokens);
        var output = response.output().stream().flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                .findFirst().orElseThrow(() -> new IllegalStateException(LanguageModelExceptionMessage.MISSING_ANSWER.getText()));
        return new ModelTurn(List.of(), new ModelReply(output.answer(), output.citations(), output.abstain(), inputTokens, outputTokens),
                inputTokens, cachedTokens, outputTokens);
    }

    /** A strict function tool with one required string argument. */
    private static FunctionTool functionTool(ToolDefinition tool) {
        var parameters = FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(tool.argument(), Map.of("type", "string", "description",
                        tool.argumentDescription()))))
                .putAdditionalProperty("required", JsonValue.from(List.of(tool.argument())))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        return FunctionTool.builder().name(tool.name()).description(tool.description()).parameters(parameters).strict(true).build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        requireKey();
        var vectors = new ArrayList<float[]>();
        for (int from = 0; from < texts.size(); ) {
            int to = from, chars = 0;
            while (to < texts.size() && to - from < REQUEST_INPUTS && (to == from || chars + texts.get(to)
                    .length() <= REQUEST_CHARS)) chars += texts.get(to++).length();
            var params = EmbeddingCreateParams.builder().model(EMBEDDING_MODEL).inputOfArrayOfStrings(texts.subList(from, to)).build();
            var data = client.embeddings().create(params).data().stream().sorted(Comparator.comparingLong(Embedding::index)).toList();
            for (var embedding : data) {
                var values = embedding.embedding();
                var vector = new float[values.size()];
                for (int i = 0; i < vector.length; i++) vector[i] = values.get(i);
                vectors.add(vector);
            }
            from = to;
        }
        return vectors;
    }

    private void requireKey() {
        if (client == null) throw new IllegalStateException(LanguageModelExceptionMessage.MISSING_KEY.getText());
    }
}
