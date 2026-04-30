package dev.soupbase.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SessionRequest(
        @NotBlank @Email String email
) {}
