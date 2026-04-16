package com.innowise.apiGateway;

import com.innowise.apiGateway.config.TestSecurityConfig;
import com.innowise.apiGateway.controller.AuthController;
import com.innowise.apiGateway.dto.AuthRequest;
import com.innowise.apiGateway.dto.JwtResponse;
import com.innowise.apiGateway.service.RegistrationOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TestSecurityConfig.class)
@WebFluxTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegistrationOrchestrator orchestrator;

    @Test
    void register_Success() {
        AuthRequest request = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        JwtResponse expectedResponse = new JwtResponse("access.token.here", "refresh.token.here");

        when(orchestrator.register(any(AuthRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        webTestClient.post()
                .uri("/tokens/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JwtResponse.class)
                .isEqualTo(expectedResponse);
    }

    @Test
    void register_OrchestratorFails_ReturnsError() {
        AuthRequest request = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        when(orchestrator.register(any(AuthRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Registration failed")));

        webTestClient.post()
                .uri("/tokens/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void register_MissingRequestBody_ReturnsBadRequest() {
        webTestClient.post()
                .uri("/tokens/register")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void register_WrongContentType_ReturnsUnsupportedMediaType() {
        AuthRequest request = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        webTestClient.post()
                .uri("/tokens/register")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(request.toString())
                .exchange()
                .expectStatus().isEqualTo(415);
    }
}