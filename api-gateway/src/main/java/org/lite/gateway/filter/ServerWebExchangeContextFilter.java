package org.lite.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * WebFilter that stores the ServerWebExchange in the Reactor context.
 * This allows downstream services to access request information (IP address,
 * User-Agent)
 * for audit logging without passing the exchange through method parameters.
 * 
 * Usage in downstream services:
 * 
 * <pre>
 * Mono.deferContextual(ctx -> {
 *     ServerWebExchange exchange = ctx.getOrDefault(ServerWebExchangeContextFilter.EXCHANGE_CONTEXT_KEY, null);
 *     // Use exchange...
 * });
 * </pre>
 */
@Component
public class ServerWebExchangeContextFilter implements WebFilter, Ordered {

    /**
     * Context key for storing the ServerWebExchange
     */
    public static final String EXCHANGE_CONTEXT_KEY = ServerWebExchangeContextFilter.class.getName() + ".EXCHANGE";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .contextWrite(Context.of(EXCHANGE_CONTEXT_KEY, exchange));
    }

    @Override
    public int getOrder() {
        // Run early to ensure exchange is available to all downstream filters and
        // services
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
