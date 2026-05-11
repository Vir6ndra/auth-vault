//package com.github.vir6ndra.auth_vault.service;
//
//import com.github.vir6ndra.auth_vault.model.dto.AuthResponse;
//import com.github.vir6ndra.auth_vault.model.dto.LoginRequest;
//import com.github.vir6ndra.auth_vault.model.dto.RegisterRequest;
//
//import com.github.vir6ndra.auth_vault.model.entity.RefreshToken;
//import com.github.vir6ndra.auth_vault.model.entity.User;
//import com.github.vir6ndra.auth_vault.model.enums.Role;
//import com.github.vir6ndra.auth_vault.repository.RefreshTokenRepository;
//import com.github.vir6ndra.auth_vault.repository.UserRepository;
//import com.github.vir6ndra.auth_vault.util.JwtUtil;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.temporal.ChronoUnit;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Transactional // Ensures atomicity
//public class AuthService {
//    private final UserRepository userRepository;
//    private final RefreshTokenRepository refreshTokenRepository;
//    private final PasswordEncoder passwordEncoder;
//    private final JwtUtil jwtUtil;
//    private final AuthenticationManager authManager;
//
//    public AuthResponse register(RegisterRequest request) {
//        if (userRepository.findByEmail(request.getEmail()).isPresent())
//            throw new RuntimeException("Email already exists");
//
//        User user = User.builder()
//                .name(request.getName())
//                .email(request.getEmail())
//                .password(passwordEncoder.encode(request.getPassword()))
//                .role(Role.USER)
//                .enabled(true)
//                .provider("LOCAL")
//                .build();
//
//        userRepository.save(user);
//        return generateTokens(user);
//    }
//
//    public AuthResponse login(LoginRequest request) {
//        // This will throw exception if credentials are wrong
////        This internally calls CustomUserDetailsService.loadUserByUsername(email)
//        authManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                        request.getEmail(), request.getPassword()));
//
//        User user = userRepository.findByEmail(request.getEmail())
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        // For Rotation: Delete old tokens on new login (Optional but cleaner)
//        refreshTokenRepository.deleteAllByUserId(user.getId());
//
//        return generateTokens(user);
//    }
//
//    private AuthResponse generateTokens(User user) {
//        String accessToken = jwtUtil.generateAccessToken(user);
//
//        // Create refresh token
//        RefreshToken refreshToken = RefreshToken.builder()
//                .token(UUID.randomUUID().toString())
//                .userId(user.getId())
//                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
//                .revoked(false)
//                .build();
//
//        refreshTokenRepository.save(refreshToken);
//
//        return AuthResponse.builder()
//                .accessToken(accessToken)
//                .refreshToken(refreshToken.getToken())
//                .email(user.getEmail())
//                .role(user.getRole().name())
//                .build();
//    }
//}




package com.github.vir6ndra.auth_vault.service;

import com.github.vir6ndra.auth_vault.model.dto.AuthResponse;
import com.github.vir6ndra.auth_vault.model.dto.LoginRequest;
import com.github.vir6ndra.auth_vault.model.dto.RegisterRequest;
import com.github.vir6ndra.auth_vault.model.entity.PasswordResetToken;
import com.github.vir6ndra.auth_vault.model.entity.RefreshToken;
import com.github.vir6ndra.auth_vault.model.entity.User;
import com.github.vir6ndra.auth_vault.model.enums.Role;
import com.github.vir6ndra.auth_vault.repository.PasswordResetTokenRepository;
import com.github.vir6ndra.auth_vault.repository.RefreshTokenRepository;
import com.github.vir6ndra.auth_vault.repository.UserRepository;
import com.github.vir6ndra.auth_vault.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    // ─── REGISTER ───────────────────────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent())
            throw new RuntimeException("Email already exists");

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .provider("LOCAL")
                .build();

        userRepository.save(user);

        String accessToken = generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ─── LOGIN ───────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // On fresh login: revoke all previous refresh tokens for this user (clean slate)
        refreshTokenRepository.deleteAllByUserId(user.getId());

        String accessToken = generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ─── REFRESH TOKEN (with rotation) ──────────────────────────────────────────

    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (stored.isRevoked())
            throw new RuntimeException("Refresh token has been revoked");

        if (stored.getExpiryDate().isBefore(Instant.now()))
            throw new RuntimeException("Refresh token has expired");

        // ROTATION: revoke only this specific token, not all user tokens
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Issue brand new access + refresh token pair
        String newAccessToken = generateAccessToken(user);
        RefreshToken newRefreshToken = createRefreshToken(user);

        return buildAuthResponse(newAccessToken, newRefreshToken, user);
    }

    // ─── LOGOUT ─────────────────────────────────────────────────────────────────

    public void logout(String accessToken, String refreshTokenValue) {
        // 1. Blacklist the access token in Redis until it naturally expires
        String tokenId = jwtUtil.extractTokenId(accessToken);
        long ttlMillis = jwtUtil.getExpirationTime(accessToken) - System.currentTimeMillis();

        if (ttlMillis > 0) {
            redisTemplate.opsForValue()
                    .set("blacklist:" + tokenId, "true", ttlMillis, TimeUnit.MILLISECONDS);
        }

        // 2. Revoke only THIS device's refresh token (not all devices)
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    // ─── FORGOT PASSWORD ─────────────────────────────────────────────────────────

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        // Invalidate any existing unused reset tokens for this user
        passwordResetTokenRepository.deleteAllByUserId(user.getId());

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .userId(user.getId())
                .expiryDate(Instant.now().plus(15, ChronoUnit.MINUTES))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(email, token);
    }

    // ─── RESET PASSWORD ──────────────────────────────────────────────────────────

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (resetToken.isUsed())
            throw new RuntimeException("Reset token has already been used");

        if (resetToken.getExpiryDate().isBefore(Instant.now()))
            throw new RuntimeException("Reset token has expired");

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used (don't delete — useful for audit trail)
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all refresh tokens (force re-login on all devices after password change)
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────────

    private String generateAccessToken(User user) {
        return jwtUtil.generateAccessToken(user);
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(user.getId())
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(String accessToken,
                                           RefreshToken refreshToken,
                                           User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
