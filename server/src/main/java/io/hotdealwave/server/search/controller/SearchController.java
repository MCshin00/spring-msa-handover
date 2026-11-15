package io.hotdealwave.server.search.controller;

import io.hotdealwave.server.search.dto.SearchResultDto;
import io.hotdealwave.server.search.service.SearchService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SearchController {
    private final SearchService searchService;

    @GetMapping("/api/search")
    ResponseEntity<List<SearchResultDto>> search(@RequestParam("q") String query) { return ResponseEntity.ok(searchService.search(query)); }
}
