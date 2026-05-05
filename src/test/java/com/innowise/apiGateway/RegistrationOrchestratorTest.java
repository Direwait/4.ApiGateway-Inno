package com.innowise.apiGateway;

import com.innowise.apiGateway.dto.RegisterRequest;
import com.innowise.apiGateway.dto.JwtResponse;
import com.innowise.apiGateway.dto.UserDto;
import com.innowise.apiGateway.service.JwtService;
import com.innowise.apiGateway.service.RegistrationOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationOrchestratorTest {

    private static final String AUTH_SERVICE_URL = "http://auth-service";
    private static final String USER_SERVICE_URL = "http://user-service";

    @Mock
    private JwtService jwtService;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @InjectMocks
    private RegistrationOrchestrator orchestrator;

    private RegisterRequest authRequest;
    private JwtResponse jwtResponse;
    private UserDto userDto;
    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orchestrator, "authServiceUrl", AUTH_SERVICE_URL);
        ReflectionTestUtils.setField(orchestrator, "userServiceUrl", USER_SERVICE_URL);

        userId = UUID.randomUUID();
        token = "access.token";

        authRequest = RegisterRequest.builder()
                .username("testuser")
                .password("password")
                .email("test@mail.com")
                .build();

        jwtResponse = new JwtResponse(token, "refresh.token");

        userDto = UserDto.builder()
                .id(userId)
                .name("testname")
                .surname("testsurname")
                .email("test@mail.com")
                .birthDate(LocalDate.of(1800, 1, 1))
                .build();
    }

    @Test
    void register_Success() {
        // Mock JwtService
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("USER");

        // Mock Auth Service call
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class)).thenReturn(Mono.just(jwtResponse));

        // Mock User Service call
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(USER_SERVICE_URL + "/users")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("Authorization"), eq("Bearer " + token))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("X-User-Id"), eq(userId.toString()))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("X-User-Role"), eq("USER"))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("X-Username"), eq(authRequest.getUsername()))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(UserDto.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(UserDto.class)).thenReturn(Mono.just(userDto));

        StepVerifier.create(orchestrator.register(authRequest))
                .expectNextMatches(response -> response.accessToken().equals(token))
                .verifyComplete();

        verify(jwtService, times(1)).extractUserId(token);
        verify(jwtService, times(1)).extractRole(token);
        verify(webClient, times(2)).post();
    }

    @Test
    void register_UserServiceFails_Rollback() {
        // Mock JwtService
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("USER");

        // Mock Auth Service call (success)
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class)).thenReturn(Mono.just(jwtResponse));

        // Mock User Service call (fails)
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(USER_SERVICE_URL + "/users")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(UserDto.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(UserDto.class))
                .thenReturn(Mono.error(new RuntimeException("UserService failed")));

        // Mock Rollback call
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(AUTH_SERVICE_URL + "/auth/rollback/" + userId))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        StepVerifier.create(orchestrator.register(authRequest))
                .expectErrorMatches(ex -> ex.getMessage().contains("User profile creation failed"))
                .verify();

        verify(webClient).delete();
        verify(jwtService).extractUserId(token);
    }

    @Test
    void register_AuthServiceFails() {
        // Mock Auth Service call (fails)
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class))
                .thenReturn(Mono.error(new RuntimeException("AuthService failed")));

        StepVerifier.create(orchestrator.register(authRequest))
                .expectErrorMatches(ex -> ex.getMessage().contains("AuthService failed"))
                .verify();

        verify(webClient, times(1)).post();
        verifyNoInteractions(jwtService);
    }
}