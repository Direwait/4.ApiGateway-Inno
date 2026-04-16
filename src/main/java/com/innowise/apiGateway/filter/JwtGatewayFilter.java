package com.innowise.apiGateway.filter;

import com.innowise.apiGateway.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final String AUTH_HEADERS = "Authorization";
    public static final String BEARER = "Bearer ";

    private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();
    private static final PathPattern LOGIN_PATTERN = PATTERN_PARSER.parse("/auth/login");
    private static final PathPattern REGISTER_PATTERN = PATTERN_PARSER.parse("/auth/register");

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();

        if (LOGIN_PATTERN.matches(path) || REGISTER_PATTERN.matches(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        if (token == null || !jwtService.isTokenValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String userId = jwtService.extractUserId(token).toString();
        String role = jwtService.extractRole(token);
        String username = jwtService.extractUsername(token);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .header("X-Username", username))
                .build();

        return chain.filter(mutatedExchange);
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(AUTH_HEADERS);
        if (authHeader != null && authHeader.startsWith(BEARER)) {
            return authHeader.substring(7);
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}