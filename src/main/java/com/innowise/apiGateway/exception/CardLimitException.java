package com.innowise.apiGateway.exception;

public class CardLimitException extends RuntimeException {
    public CardLimitException(String message) {
        super(message);
    }
}
