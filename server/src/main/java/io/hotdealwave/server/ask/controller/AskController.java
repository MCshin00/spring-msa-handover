package io.hotdealwave.server.ask.controller;

import io.hotdealwave.server.ask.dto.AskRequestDto;
import io.hotdealwave.server.ask.dto.AskResponseDto;
import io.hotdealwave.server.ask.service.AskService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AskController {
    private final AskService askService;

    @PostMapping("/api/ask")
    ResponseEntity<AskResponseDto> ask(@RequestBody AskRequestDto question) { return ResponseEntity.ok(askService.ask(question.question())); }
}
