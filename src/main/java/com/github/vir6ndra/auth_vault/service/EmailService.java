package com.github.vir6ndra.auth_vault.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("AuthVault — Password Reset Request");
        message.setText(
                "Hi,\n\n" +
                        "You requested a password reset. Use the token below:\n\n" +
                        "Token: " + resetToken + "\n\n" +
                        "POST to: /api/auth/reset-password\n" +
                        "Body: { \"token\": \"" + resetToken + "\", \"newPassword\": \"yourNewPassword\" }\n\n" +
                        "This token expires in 15 minutes.\n\n" +
                        "If you didn't request this, ignore this email.\n\n" +
                        "— AuthVault"
        );
        mailSender.send(message);
    }
}