package com.github.vir6ndra.auth_vault.model.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}