package com.innowise.apiGateway.service;

import com.innowise.apiGateway.dto.RegisterRequest;
import com.innowise.apiGateway.dto.JwtResponse;
import com.innowise.apiGateway.dto.UserDto;
import com.innowise.apiGateway.exception.RegistrationRollbackException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationOrchestrator {

    private final JwtService jwtService;
    private final WebClient webClient;

    @Value("${url.user-service}")
    private String userServiceUrl;

    @Value("${url.auth-service}")
    private String authServiceUrl;

    public Mono<JwtResponse> register(RegisterRequest request) {
        return registerCredentials(request)
                .flatMap(authResponse -> {
                    String token = authResponse.accessToken();
                    UUID userId = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);

                    log.info("User registered in AuthService with id: {}, {}", userId, role);

                    return createUser(request, userId, role, token)
                            .thenReturn(authResponse)
                            .onErrorResume(ex -> {
                                return rollbackCredentials(userId)
                                        .then(Mono.error(new RegistrationRollbackException("User profile creation failed")));
                            });
                });
    }

    private Mono<UserDto> createUser(RegisterRequest request, UUID userId, String role, String token) {
        var build = UserDto.builder()
                .id(userId)
                .email(request.getEmail())
                .build();

        return webClient.post()
                .uri(userServiceUrl + "/users")
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(build)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    private Mono<JwtResponse> registerCredentials(RegisterRequest request) {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .email(request.getEmail())
                .build();

        return webClient.post()
                .uri(authServiceUrl + "/auth/register")
                .bodyValue(registerRequest)
                .retrieve()
                .bodyToMono(JwtResponse.class);
    }

    private Mono<Void> rollbackCredentials(UUID userId) {
        return webClient.delete()
            .uri(authServiceUrl + "/auth/rollback/" + userId)
            .retrieve()
            .bodyToMono(Void.class)
            .onErrorResume(ex -> {
                log.error("Rollback User Credential failed for userId={}: {}", userId, ex.getMessage());
                return Mono.empty();
            });
    }
}