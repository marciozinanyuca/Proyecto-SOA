
package com.ecoinvest.soa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain permisos(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(reglas -> reglas

                .requestMatchers(
                        "/api/auth/csrf"
                ).permitAll()

                .requestMatchers(
                        "/api/dashboard",
                        "/api/consultoras",
                        "/api/senace/datastream"
                ).permitAll()

                .requestMatchers(
                        "/api/admin/**",
                        "/api/estado-servicios",
                        "/actuator/**"
                ).hasRole("ADMINISTRADOR")

                .anyRequest().authenticated()
        )
        .exceptionHandling(config -> config
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")
                )
        )
        .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
