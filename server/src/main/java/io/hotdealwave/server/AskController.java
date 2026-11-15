package io.hotdealwave.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AskController {
    private final Ask ask;

    AskController(Ask ask) { this.ask = ask; }

    @PostMapping("/api/ask")
    ResponseEntity<Ask.Answer> ask(@RequestBody AskRequestDto question) { return ResponseEntity.ok(ask.ask(question.question())); }
}
