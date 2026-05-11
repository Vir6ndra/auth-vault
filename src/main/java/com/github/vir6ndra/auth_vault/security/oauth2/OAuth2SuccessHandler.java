package com.github.vir6ndra.auth_vault.security.oauth2;

import com.github.vir6ndra.auth_vault.model.entity.User;
import com.github.vir6ndra.auth_vault.model.enums.Role;
import com.github.vir6ndra.auth_vault.repository.UserRepository;
import com.github.vir6ndra.auth_vault.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // Create user on first Google login, fetch on subsequent logins
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(email)
                                .name(name)
                                .role(Role.USER)
                                .enabled(true)
                                .provider("GOOGLE")
                                .build()
                ));

        String accessToken = jwtUtil.generateAccessToken(user);

        // Send token back as query param — frontend reads it from URL
        response.sendRedirect("/oauth2/success?token=" + accessToken);
    }
}