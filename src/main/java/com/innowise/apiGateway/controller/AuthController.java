package com.innowise.apiGateway.controller;

import com.innowise.apiGateway.service.RegistrationOrchestrator;
import com.innowise.apiGateway.dto.AuthRequest;
import com.innowise.apiGateway.dto.JwtResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tokens")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationOrchestrator orchestrator;

    @PostMapping("/register")
    public Mono<JwtResponse> register(@RequestBody AuthRequest request) {
        return orchestrator.register(request);
    }
}