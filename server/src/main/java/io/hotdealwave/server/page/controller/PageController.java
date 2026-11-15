package io.hotdealwave.server.page.controller;

import io.hotdealwave.server.page.PageKind;
import io.hotdealwave.server.page.service.HomeService;
import io.hotdealwave.server.page.service.PageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** Endpoint, topic and service bodies as HTML. Search and MCP read the same bodies as text. */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class PageController {
    private final PageService pageService;
    private final HomeService homeService;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String index() { return homeService.index(); }

    @GetMapping(value = "/endpoints/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String endpoint(@PathVariable String id) { return pageService.requireHtml(PageKind.ENDPOINT.getPath() + id); }

    @GetMapping(value = "/topics/{*name}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String topic(@PathVariable String name) { return pageService.requireHtml(PageKind.TOPIC.getPath().replaceFirst("/$", "") + name); }

    @GetMapping(value = "/services/{name}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String service(@PathVariable String name) { return pageService.requireHtml(PageKind.SERVICE.getPath() + name); }

    @GetMapping(value = "/repository", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String repository() { return pageService.requireHtml(PageKind.REPOSITORY.getPath()); }

    @GetMapping(value = "/revival", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String revival() { return pageService.requireHtml(PageKind.REVIVAL.getPath()); }

    /** A recorded response file with numbered lines, which response evidence links to. */
    @GetMapping("/responses/{file:.+}")
    String response(@PathVariable String file, Model model) {
        var record = pageService.findResponseRecord(file);
        model.addAttribute("file", record.file());
        model.addAttribute("endpoint", record.endpoint());
        model.addAttribute("lines", record.lines());
        return "response";
    }

}
