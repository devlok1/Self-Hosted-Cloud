package com.homecloud.backend.security;

import com.homecloud.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final List<String> allowedOriginPatterns;

    public SecurityConfig(
            JwtService jwtService,
            @Value("${homecloud.security.cors.allowed-origin-patterns:http://localhost,http://localhost:*,http://127.0.0.1,http://127.0.0.1:*,http://192.168.*,http://192.168.*:*,http://10.*,http://10.*:*,http://172.16.*,http://172.16.*:*,http://172.17.*,http://172.17.*:*,http://172.18.*,http://172.18.*:*,http://172.19.*,http://172.19.*:*,http://172.20.*,http://172.20.*:*,http://172.21.*,http://172.21.*:*,http://172.22.*,http://172.22.*:*,http://172.23.*,http://172.23.*:*,http://172.24.*,http://172.24.*:*,http://172.25.*,http://172.25.*:*,http://172.26.*,http://172.26.*:*,http://172.27.*,http://172.27.*:*,http://172.28.*,http://172.28.*:*,http://172.29.*,http://172.29.*:*,http://172.30.*,http://172.30.*:*,http://172.31.*,http://172.31.*:*}")
            String rawOriginPatterns) {
        this.jwtService = jwtService;
        this.allowedOriginPatterns = parseAndSanitizeOriginPatterns(rawOriginPatterns);
    }

    public static List<String> parseAndSanitizeOriginPatterns(String rawPatterns) {
        if (rawPatterns == null || rawPatterns.isBlank()) {
            return List.of("http://localhost", "http://localhost:*", "http://127.0.0.1", "http://127.0.0.1:*");
        }

        List<String> result = new java.util.ArrayList<>();
        String[] tokens = rawPatterns.split(",");
        for (String token : tokens) {
            String pattern = token.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            while (pattern.endsWith("/")) {
                pattern = pattern.substring(0, pattern.length() - 1).trim();
            }
            if (pattern.equals("*")) {
                throw new IllegalArgumentException("Unrestricted CORS wildcard origin '*' is strictly prohibited");
            }

            result.add(pattern);

            if (pattern.contains("://")) {
                String afterScheme = pattern.substring(pattern.indexOf("://") + 3);
                if (!afterScheme.contains(":") && !afterScheme.endsWith("*")) {
                    result.add(pattern + ":*");
                }
            }
        }
        return result.stream().distinct().toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPassword())
                        .authorities(Collections.emptyList())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        http
                // CSRF is disabled because this is a stateless API using Bearer tokens in headers.
                // It does not use browser cookies for session state, preventing CSRF vectors.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
