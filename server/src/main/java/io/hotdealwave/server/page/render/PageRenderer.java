package io.hotdealwave.server.page.render;

import io.hotdealwave.server.analysis.VerifierOutput;
import io.hotdealwave.server.page.dto.PageDto;
import io.hotdealwave.server.page.dto.PageSectionDto;
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
public class PageRenderer {
    private static final Pattern H2 = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);
    private final VerifierOutput verifierOutput;
    private final SpringTemplateEngine engine;
    private final ConfirmedLegend confirmedLegend;
    private final ThymeleafEvaluationContext evaluation;

    public PageRenderer(VerifierOutput verifierOutput, SpringTemplateEngine engine, ApplicationContext context, ConfirmedLegend confirmedLegend) {
        this.verifierOutput = verifierOutput;
        this.engine = engine;
        this.confirmedLegend = confirmedLegend;
        this.evaluation = new ThymeleafEvaluationContext(context, null);
    }

    public String html(PageDto page) {
        String html = HtmlDecorator.html(process(page.template(), page.model()), verifierOutput.target());
        int title = html.indexOf("</h1>") + "</h1>".length();
        return html.substring(0, title) + confirmedLegend.html(page.kind()) + html.substring(title);
    }

    /** Any other template as HTML for the browser, with display-only markup. */
    public String html(String template, Map<String, Object> model) {
        return HtmlDecorator.html(process(template, model), verifierOutput.target());
    }

    private String process(String template, Map<String, Object> model) {
        var context = new Context(Locale.KOREAN, model);
        context.setVariable("target", verifierOutput.target());
        context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, evaluation);
        return engine.process(template, context);
    }

    public List<PageSectionDto> renderSections(PageDto page) {
        String html = process(page.template(), page.model());
        html = html.substring(html.indexOf("<h1"), html.lastIndexOf("</body>"));
        String title = bodyText(html.substring(0, html.indexOf("</h1>")));
        var sections = new ArrayList<PageSectionDto>();
        var matcher = H2.matcher(html);
        int start = 0;
        String heading = "";
        while (matcher.find()) {
            sections.add(new PageSectionDto(page.path(), title, heading, bodyText(html.substring(start, matcher.start())), page.kind(),
                    page.services()));
            heading = bodyText(matcher.group(1));
            start = matcher.end();
        }
        sections.add(new PageSectionDto(page.path(), title, heading, bodyText(html.substring(start)), page.kind(), page.services()));
        return sections;
    }

    /** Block ends become line breaks; a template line break is dropped at a line start and is a space between inline elements. */
    private static String bodyText(String html) {
        String text = html.replaceAll("\\s*\\n\\s*", "\u0001")
                .replaceAll("(?i)</(p|div|h1|h2|h3|li|ul|section)>", "\n")
                .replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(text).lines().map(line -> line.replaceFirst("^\u0001+", "").replace('\u0001', ' ').stripTrailing())
                .filter(line -> !line.isBlank()).reduce((previous, next) -> previous + "\n" + next).orElse("");
    }
}
