package com.innowise.apiGateway;

import com.innowise.apiGateway.dto.AuthRequest;
import com.innowise.apiGateway.dto.JwtResponse;
import com.innowise.apiGateway.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
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

    public Mono<JwtResponse> register(AuthRequest request) {
        return registerCredentials(request)
                .flatMap(authResponse -> {
                    String token = authResponse.accessToken();
                    UUID userId = jwtService.extractUserId(token);

                    log.info("User registered in AuthService with id: {}", userId);

                    return createUser(request, userId)
                            .thenReturn(authResponse)
                            .onErrorResume(ex -> {
                                return rollbackCredentials(userId)
                                        .then(Mono.error(new RuntimeException("User credentials creation failed")));
                            });
                });
    }

    private Mono<UserDto> createUser(AuthRequest request, UUID userId) {
        var build = UserDto.builder()
                .id(userId)
                .name(request.getUsername())
                .surname("Please, change your data")
                .email("placeholder@mail.com")
                .birthDate(LocalDate.of(1800, 1, 1))
                .build();

        return webClient.post()
            .uri(userServiceUrl + "/users")
            .bodyValue(build)
            .retrieve()
            .bodyToMono(UserDto.class);
    }

    private Mono<JwtResponse> registerCredentials(AuthRequest request) {
        AuthRequest registerRequest = AuthRequest.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .build();

        return webClient.post()
                .uri(authServiceUrl + "/auth/register")
                .bodyValue(registerRequest)
                .retrieve()
                .bodyToMono(JwtResponse.class);
    }

    private Mono<Void> rollbackCredentials(UUID userId) {
        return webClient.delete()
            .uri(authServiceUrl + "/auth/" + userId)
            .retrieve()
            .bodyToMono(Void.class)
            .onErrorResume(ex -> {
                log.error("Rollback User Credential failed for userId={}: {}", userId, ex.getMessage());
                return Mono.empty();
            });
    }
}