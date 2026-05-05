package com.innowise.apiGateway.dto;

public record JwtResponse(
        String accessToken,
    String refreshToken) {
}
