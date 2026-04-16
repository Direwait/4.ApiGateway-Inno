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
import org.springframework.http.MediaType;
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orchestrator, "authServiceUrl", AUTH_SERVICE_URL);
        ReflectionTestUtils.setField(orchestrator, "userServiceUrl", USER_SERVICE_URL);

        userId = UUID.randomUUID();
        authRequest = RegisterRequest.builder()
                .username("testuser")
                .password("password")
                .build();
        jwtResponse = new JwtResponse("access.token", "refresh.token");
        userDto = UserDto.builder()
                .id(userId)
                .name("testname")
                .surname("testsurname")
                .email("placeholder@mail.com")
                .birthDate(LocalDate.of(1800, 1, 1))
                .build();
    }

    @Test
    void register_Success() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class)).thenReturn(Mono.just(jwtResponse));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(USER_SERVICE_URL + "/users")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(UserDto.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(UserDto.class)).thenReturn(Mono.just(userDto));

        when(jwtService.extractUserId(anyString())).thenReturn(userId);

        StepVerifier.create(orchestrator.register(authRequest))
                .expectNext(jwtResponse)
                .verifyComplete();

        verify(webClient, times(2)).post();
        verify(jwtService).extractUserId(anyString());
    }

    @Test
    void register_UserServiceFails_Rollback() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class)).thenReturn(Mono.just(jwtResponse));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(USER_SERVICE_URL + "/users")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(UserDto.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(UserDto.class))
                .thenReturn(Mono.error(new RuntimeException("UserService failed")));

        when(jwtService.extractUserId(anyString())).thenReturn(userId);


        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(AUTH_SERVICE_URL + "/auth/rollback/" + userId))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        StepVerifier.create(orchestrator.register(authRequest))
                .expectError(RuntimeException.class)
                .verify();

        verify(webClient).delete();
    }

    @Test
    void register_AuthServiceFails() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(AUTH_SERVICE_URL + "/auth/register")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(RegisterRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JwtResponse.class))
                .thenReturn(Mono.error(new RuntimeException("AuthService failed")));

        StepVerifier.create(orchestrator.register(authRequest))
                .expectError(RuntimeException.class)
                .verify();

        verify(webClient, times(1)).post();
        verifyNoInteractions(jwtService);
    }
}