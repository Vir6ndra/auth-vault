package com.github.vir6ndra.auth_vault.model.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
}