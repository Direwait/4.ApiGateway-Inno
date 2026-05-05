package com.innowise.apiGateway.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto implements Serializable {

    private UUID id;

    private String name;

    private String surname;

    private LocalDate birthDate;

    private String email;

    private boolean active;
}
