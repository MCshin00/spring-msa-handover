package io.hotdealwave.server.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hotdealwave.server.client.LanguageModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Text embeddings kept in a file next to the analysis output, keyed by a hash of the text, so only new or changed texts are embedded. */
@Component
public class EmbeddingStore {
    private final LanguageModel languageModel;
    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public EmbeddingStore(LanguageModel languageModel, @Value("${analysis.path}") String analysisPath, @Value("${embeddings.path:}") String path) {
        this.languageModel = languageModel;
        this.file = path.isBlank() ? Path.of(analysisPath).resolveSibling("embeddings.json") : Path.of(path);
    }

    /** One vector per text. The file then holds exactly these texts' vectors. */
    public synchronized List<float[]> embeddingsFor(List<String> texts) {
        var stored = read();
        var kept = new LinkedHashMap<String, float[]>();
        var missing = new LinkedHashMap<String, String>();
        for (var text : texts) {
            String hash = hash(text);
            if (stored.containsKey(hash)) kept.put(hash, stored.get(hash));
            else missing.put(hash, text);
        }
        if (!missing.isEmpty()) {
            var vectors = languageModel.embed(List.copyOf(missing.values()));
            int i = 0;
            for (var hash : missing.keySet()) kept.put(hash, vectors.get(i++));
        }
        if (!missing.isEmpty() || kept.size() != stored.size()) write(kept);
        return texts.stream().map(text -> kept.get(hash(text))).toList();
    }

    /** A question's vector, made on every call and not stored. */
    float[] queryEmbedding(String text) { return languageModel.embed(List.of(text)).get(0); }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private Map<String, float[]> read() {
        if (!Files.exists(file)) return Map.of();
        try {
            Map<String, String> encoded = json.readValue(file.toFile(), new TypeReference<>() {});
            var vectors = new HashMap<String, float[]>();
            encoded.forEach((hash, base64) -> {
                var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).asFloatBuffer();
                var vector = new float[buffer.remaining()];
                buffer.get(vector);
                vectors.put(hash, vector);
            });
            return vectors;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void write(Map<String, float[]> vectors) {
        var encoded = new LinkedHashMap<String, String>();
        vectors.forEach((hash, vector) -> {
            var buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
            buffer.asFloatBuffer().put(vector);
            encoded.put(hash, Base64.getEncoder().encodeToString(buffer.array()));
        });
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            json.writeValue(file.toFile(), encoded);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
