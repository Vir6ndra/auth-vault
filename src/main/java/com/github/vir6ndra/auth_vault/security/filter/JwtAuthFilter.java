//This class is a Security Guard that stands at the entrance of your application.
// It inspects every incoming request to see if the user has a valid "ID Card" (JWT).

package com.github.vir6ndra.auth_vault.security.filter;

import com.github.vir6ndra.auth_vault.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    //this method ruuns for every request
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String tokenId = jwtUtil.extractTokenId(token);

        // Check if token is blacklisted in Redis (Logout logic)
        if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + tokenId))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

//        if (jwtUtil.isTokenValid(token)) {
//            String email = jwtUtil.extractEmail(token);
//            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
//
//            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
//                    userDetails, null, userDetails.getAuthorities());
//
//            SecurityContextHolder.getContext().setAuthentication(authToken);
//        }

        // ... inside doFilterInternal ...
        if (jwtUtil.isTokenValid(token)) {
            String email = jwtUtil.extractEmail(token); // Define email here

            // Ensure you use the instance variable 'userDetailsService' injected via constructor
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // This tells Spring: "This person is allowed in"
            SecurityContextHolder.getContext().setAuthentication(authToken);
            System.out.println("User: " + email + " Authorities: " + userDetails.getAuthorities());
        }


        chain.doFilter(request, response);
    }
}


//
//
//Client Request
//     ↓
//JwtAuthFilter
//     ↓
//Extract Token
//     ↓
//Check Blacklist in Redis
//     ↓
//Validate JWT
//     ↓
//Load User
//     ↓
//Set Authentication
//     ↓
//Controller Access Granted
