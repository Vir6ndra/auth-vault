package com.github.vir6ndra.auth_vault.model.dto;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken; // to revoke only this device's token
}