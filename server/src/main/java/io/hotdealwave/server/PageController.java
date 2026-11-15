package io.hotdealwave.server;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** Endpoint, topic and service bodies as HTML. Search and MCP read the same bodies as text. */
@Controller
class PageController {
    private final PageService pages;
    private final HomeService homeService;

    PageController(PageService pages, HomeService homeService) { this.pages = pages; this.homeService = homeService; }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String index() { return homeService.index(); }

    @GetMapping(value = "/endpoints/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String endpoint(@PathVariable String id) { return show(PageKind.ENDPOINT.path() + id); }

    @GetMapping(value = "/topics/{*name}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String topic(@PathVariable String name) { return show(PageKind.TOPIC.path().replaceFirst("/$", "") + name); }

    @GetMapping(value = "/services/{name}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String service(@PathVariable String name) { return show(PageKind.SERVICE.path() + name); }

    @GetMapping(value = "/repository", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String repository() { return show(PageKind.REPOSITORY.path()); }

    @GetMapping(value = "/revival", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody String revival() { return show(PageKind.REVIVAL.path()); }

    /** A recorded response file with numbered lines, which response evidence links to. */
    @GetMapping("/responses/{file:.+}")
    String response(@PathVariable String file, Model model) {
        var endpoint = pages.findResponseRecord(file);
        model.addAttribute("file", endpoint.file());
        model.addAttribute("endpoint", endpoint.endpoint());
        model.addAttribute("lines", endpoint.lines());
        return "response";
    }

    private String show(String path) {
        return pages.requireHtml(path);
    }
}
