package io.hotdealwave.server.search.service;

import io.hotdealwave.server.page.dto.PageSectionDto;
import io.hotdealwave.server.page.service.PageService;
import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.dto.SearchRouteDto;
import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * BM25 over the h2 sections of page bodies, with service and kind filters taken from the question. The answer path fuses BM25 and embedding
 * ranks.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SearchService {
    private record Doc(PageSectionDto section, Map<String, Integer> tf, int length) {}
    private record Scored(PageSectionDto section, double score) {}

    private static final double K1 = 1.2, B = 0.75;
    static final int LIMIT = 5;
    /** The answer path takes more: with five, RRF pushed out sections BM25 ranked second and fourth that the answer needed. */
    static final int ASK_LIMIT = 8;
    /** Reciprocal rank fusion constant. */
    static final int RRF = 60;
    /** Characters per embedded piece of a section, under the embedding input limit even for Korean text. */
    static final int PIECE = 3000;

    private final PageService pageService;
    private final EmbeddingStore embeddingStore;
    private final SearchRouter searchRouter;
    private List<Doc> docs;
    private final Map<String, Integer> df = new HashMap<>();
    private double averageLength;
    /** Each doc's piece vectors, in docs order. */
    private List<List<float[]>> vectors;

    /** Top sections by BM25, one per page. Filters are dropped one at a time, kind first, while nothing matches. */
    public List<SearchResultDto> search(String query) { return search(query, null); }

    /** For /api/ask: BM25 and embedding ranks fused by RRF, top ASK_LIMIT, otherwise as search. Throws when the embedding call fails. */
    public List<SearchResultDto> hybridSearch(String query) { return search(query, embeddingStore.queryEmbedding(query)); }

    private List<SearchResultDto> search(String query, float[] question) {
        var route = searchRouter.route(query);
        var tried = new LinkedHashSet<>(List.of(route, new SearchRouteDto(route.service(), null), new SearchRouteDto(null, null)));
        for (var filter : tried) {
            var results = rank(query, filter, question);
            if (!results.isEmpty()) return results;
        }
        return List.of();
    }

    /** Full text of a result's section. */
    public String sectionText(SearchResultDto result) {
        return docs().stream().map(Doc::section)
                .filter(section -> section.page().equals(result.page()) && section.heading().equals(result.section()))
                .map(PageSectionDto::text).findFirst().orElse("");
    }

    private List<SearchResultDto> rank(String query, SearchRouteDto filter, float[] question) {
        var terms = Tokenizer.tokens(query);
        var bm25 = new ArrayList<Scored>();
        var similar = new ArrayList<Scored>();
        var all = docs();
        for (int i = 0; i < all.size(); i++) {
            var doc = all.get(i);
            var section = doc.section();
            if (filter.service() != null && !section.services().contains(filter.service())) continue;
            if (filter.kind() != null && section.kind() != filter.kind()) continue;
            double score = 0;
            for (var term : terms) {
                int tf = doc.tf().getOrDefault(term, 0);
                if (tf == 0) continue;
                double idf = Math.log(1 + (docs.size() - df.get(term) + 0.5) / (df.get(term) + 0.5));
                score += idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * doc.length() / averageLength));
            }
            if (score > 0) bm25.add(new Scored(section, Math.round(score * 1000) / 1000.0));
            if (question != null) {
                // A long section is embedded in pieces; its closest piece stands for it.
                double best = vectors().get(i).stream().mapToDouble(vector -> EmbeddingStore.cosine(question, vector)).max().orElse(0);
                similar.add(new Scored(section, best));
            }
        }
        bm25.sort(Comparator.comparingDouble(Scored::score).reversed());
        var scored = bm25;
        if (question != null) {
            similar.sort(Comparator.comparingDouble(Scored::score).reversed());
            var fused = new LinkedHashMap<PageSectionDto, Double>();
            for (var ranking : List.of(bm25, similar))
                for (int rank = 0; rank < ranking.size(); rank++) fused.merge(ranking.get(rank).section(), 1.0 / (RRF + rank + 1), Double::sum);
            scored = new ArrayList<>();
            for (var entry : fused.entrySet()) scored.add(new Scored(entry.getKey(), Math.round(entry.getValue() * 1_000_000) / 1_000_000.0));
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        }
        var seen = new HashSet<String>();
        return scored.stream().filter(result -> seen.add(result.section().page())).limit(question == null ? LIMIT : ASK_LIMIT)
                .map(result -> new SearchResultDto(result.section().page(), result.section().title(), result.section().heading(),
                        result.score(), preview(result.section().text(), terms), result.section().services())).toList();
    }

    private static String preview(String text, List<String> terms) {
        String lower = text.toLowerCase();
        int at = terms.stream().sorted(Comparator.comparingInt(String::length).reversed()).mapToInt(lower::indexOf)
                .filter(i -> i >= 0).findFirst().orElse(0);
        int start = Math.max(0, at - 60), end = Math.min(text.length(), start + 200);
        return (start > 0 ? "…" : "") + text.substring(start, end).replace('\n', ' ') + (end < text.length() ? "…" : "");
    }

    /** What is embedded for a section: its title and heading, then its text cut at line ends into pieces of at most PIECE characters. */
    static List<String> pieces(PageSectionDto section) {
        String head = section.title() + " — " + section.heading() + "\n";
        var pieces = new ArrayList<String>();
        var piece = new StringBuilder();
        for (var line : section.text().split("\n")) {
            for (int at = 0; at < Math.max(1, line.length()); at += PIECE) {
                String part = line.substring(at, Math.min(line.length(), at + PIECE));
                if (piece.length() > 0 && piece.length() + 1 + part.length() > PIECE) {
                    pieces.add(head + piece);
                    piece.setLength(0);
                }
                piece.append(piece.length() > 0 ? "\n" : "").append(part);
            }
        }
        if (piece.length() > 0 || pieces.isEmpty()) pieces.add(head + piece);
        return pieces;
    }

    private synchronized List<List<float[]>> vectors() {
        if (vectors == null) {
            var all = docs().stream().map(document -> pieces(document.section())).toList();
            var flat = embeddingStore.embeddingsFor(all.stream().flatMap(List::stream).toList());
            var grouped = new ArrayList<List<float[]>>();
            int at = 0;
            for (var pieces : all) {
                grouped.add(flat.subList(at, at + pieces.size()));
                at += pieces.size();
            }
            vectors = grouped;
        }
        return vectors;
    }

    private synchronized List<Doc> docs() {
        if (docs == null) {
            var built = new ArrayList<Doc>();
            for (var page : pageService.sections().values()) {
                for (var section : page) {
                    if (section.page().equals(PageService.REVIVAL)) continue;
                    var tf = new HashMap<String, Integer>();
                    var tokens = Tokenizer.tokens(section.title() + " " + section.heading() + " " + section.text());
                    tokens.forEach(term -> tf.merge(term, 1, Integer::sum));
                    tf.keySet().forEach(term -> df.merge(term, 1, Integer::sum));
                    built.add(new Doc(section, tf, tokens.size()));
                }
            }
            averageLength = built.stream().mapToInt(Doc::length).average().orElse(1);
            docs = built;
        }
        return docs;
    }
}
