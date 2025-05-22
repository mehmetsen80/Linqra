package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.lite.gateway.entity.LinqTool;
import org.lite.gateway.service.LinqToolService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class LinqToolController {

    private final LinqToolService linqToolService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LinqTool> createLinqTool(@RequestBody LinqTool linqTool) {
        return linqToolService.saveLinqTool(linqTool);
    }
}
