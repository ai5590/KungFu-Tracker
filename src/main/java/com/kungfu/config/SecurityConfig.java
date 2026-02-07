package com.kungfu.config;

import com.kungfu.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;

    public SecurityConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login.html", "/css/**", "/js/**", "/assets/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/tree", "/api/exercises", "/api/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/files/stream").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/me/change-password").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/sections", "/api/exercises", "/api/files/upload").hasRole("EDITOR")
                .requestMatchers(HttpMethod.PUT, "/api/sections/**", "/api/exercises/**", "/api/files/**").hasRole("EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/sections", "/api/exercises", "/api/files").hasRole("EDITOR")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .userDetailsService(userService);
        return http.build();
    }
}
