package io.hotdealwave.server;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {
    private final Search search;

    SearchController(Search search) { this.search = search; }

    @GetMapping("/api/search")
    ResponseEntity<List<Search.Result>> search(@RequestParam String q) { return ResponseEntity.ok(search.search(q)); }
}
