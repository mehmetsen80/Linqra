package org.lite.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Profile({ "dev", "remote-dev" })
@Slf4j
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("LoggingGlobalFilter (HIGHEST_PRECEDENCE): Path='{}'", exchange.getRequest().getPath());

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Object requestUrlObj = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        if (route != null) {
            log.info("Gateway Route Matched: RouteId='{}' -> ResolvedTarget='{}'", route.getId(), requestUrlObj);
        } else {
            log.warn("Gateway Route NOT matched yet (or null) at start of chain.");
        }

        return chain.filter(exchange)
                .doOnError(e -> log.error("Error in Gateway Filter Chain", e))
                .then(Mono.fromRunnable(() -> {
                    log.info("Gateway Filter Chain Completed. Response Status: {}",
                            exchange.getResponse().getStatusCode());
                }));
    }
}
