package com.github.vir6ndra.auth_vault.model.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
