package dev.soupbase.config;

import dev.soupbase.infra.ClerkJwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ClerkJwtFilter clerkJwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Authentication is enforced by ClerkJwtFilter; authorization by @PreAuthorize.
                        .anyRequest().permitAll()
                )
                .addFilterBefore(clerkJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // Prevents Spring Boot from also registering the filter in the default servlet chain.
    @Bean
    public FilterRegistrationBean<ClerkJwtFilter> clerkJwtFilterRegistration(ClerkJwtFilter clerkJwtFilter) {
        FilterRegistrationBean<ClerkJwtFilter> registration = new FilterRegistrationBean<>(clerkJwtFilter);
        registration.setEnabled(false);
        return registration;
    }
}
