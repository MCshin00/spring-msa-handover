package io.hotdealwave.server.search.controller;

import io.hotdealwave.server.ask.service.AskService;
import io.hotdealwave.server.ask.service.DeepAskService;
import io.hotdealwave.server.client.LanguageModel;
import io.hotdealwave.server.search.service.SearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Search and question page. A question is a POST so reloading the page does not call the model again by accident. */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SearchPageController {
    private final SearchService searchService;
    private final AskService askService;
    private final DeepAskService deepAskService;
    private final LanguageModel languageModel;

    @GetMapping("/search")
    String search(@RequestParam(name = "q", required = false) String query, Model model) {
        model.addAttribute("q", query);
        if (query != null && !query.isBlank()) model.addAttribute("results", searchService.search(query));
        return "search";
    }

    @PostMapping("/search")
    String ask(@RequestParam String question, Model model) {
        model.addAttribute("question", question);
        model.addAttribute("answer", askService.ask(question));
        model.addAttribute("model", languageModel.name());
        return "search";
    }

    @PostMapping("/search/deep")
    String deepAsk(@RequestParam String question, Model model) {
        model.addAttribute("question", question);
        model.addAttribute("deep", deepAskService.ask(question));
        model.addAttribute("model", languageModel.name());
        return "search";
    }
}
