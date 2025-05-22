package org.lite.gateway.service;

import org.lite.gateway.entity.LinqTool;
import reactor.core.publisher.Mono;

public interface LinqToolService {
    Mono<LinqTool> saveLinqTool(LinqTool linqTool);
}
