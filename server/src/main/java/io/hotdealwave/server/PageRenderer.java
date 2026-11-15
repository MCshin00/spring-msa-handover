package io.hotdealwave.server;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;

/** Renders HTML and extracts the unchanged h2 sections and plain body text. */
@Component
class PageRenderer {
    private static final Pattern H2 = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);
    private final Analysis analysis;
    private final SpringTemplateEngine engine;
    private final Legend legend;
    private final ThymeleafEvaluationContext evaluation;

    PageRenderer(Analysis analysis, SpringTemplateEngine engine, ApplicationContext context, Legend legend) {
        this.analysis = analysis; this.engine = engine; this.legend = legend;
        this.evaluation = new ThymeleafEvaluationContext(context, null);
    }

    String html(PageDto page) {
        String html = Decor.html(process(page.template(), page.model()), analysis.target());
        int title = html.indexOf("</h1>") + "</h1>".length();
        return html.substring(0, title) + legend.html(page.kind()) + html.substring(title);
    }

    /** Any other template as HTML for the browser, with display-only markup. */
    String html(String template, Map<String, Object> model) {
        return Decor.html(process(template, model), analysis.target());
    }

    private String process(String template, Map<String, Object> model) {
        var context = new Context(Locale.KOREAN, model);
        context.setVariable("target", analysis.target());
        context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, evaluation);
        return engine.process(template, context);
    }

    List<PageSectionDto> render(PageDto page) {
        String html = process(page.template(), page.model());
        html = html.substring(html.indexOf("<h1"), html.lastIndexOf("</body>"));
        String title = plain(html.substring(0, html.indexOf("</h1>")));
        var sections = new ArrayList<PageSectionDto>();
        var m = H2.matcher(html);
        int start = 0;
        String heading = "";
        while (m.find()) {
            sections.add(new PageSectionDto(page.path(), title, heading, plain(html.substring(start, m.start())), page.kind(), page.services()));
            heading = plain(m.group(1));
            start = m.end();
        }
        sections.add(new PageSectionDto(page.path(), title, heading, plain(html.substring(start)), page.kind(), page.services()));
        return sections;
    }

    /** Block ends become line breaks; a template line break is dropped at a line start and is a space between inline elements. */
    private static String plain(String html) {
        String text = html.replaceAll("\\s*\\n\\s*", "\u0001")
                .replaceAll("(?i)</(p|div|h1|h2|h3|li|ul|section)>", "\n")
                .replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(text).lines().map(l -> l.replaceFirst("^\u0001+", "").replace('\u0001', ' ').stripTrailing())
                .filter(l -> !l.isBlank()).reduce((x, y) -> x + "\n" + y).orElse("");
    }
}
