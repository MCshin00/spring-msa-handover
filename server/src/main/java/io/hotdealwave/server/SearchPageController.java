package io.hotdealwave.server;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Search and question page. A question is a POST so reloading the page does not call the model again by accident. */
@Controller
class SearchPageController {
    private final Search search;
    private final Ask ask;
    private final AskModel askModel;

    SearchPageController(Search search, Ask ask, AskModel askModel) { this.search = search; this.ask = ask; this.askModel = askModel; }

    @GetMapping("/search")
    String search(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        if (q != null && !q.isBlank()) model.addAttribute("results", search.search(q));
        return "search";
    }

    @PostMapping("/search")
    String ask(@RequestParam String question, Model model) {
        model.addAttribute("question", question);
        model.addAttribute("answer", ask.ask(question));
        model.addAttribute("model", askModel.name());
        return "search";
    }
}
