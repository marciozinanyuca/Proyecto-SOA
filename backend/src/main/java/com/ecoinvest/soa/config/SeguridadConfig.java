package com.ecoinvest.soa.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain permisos(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(reglas -> reglas
                .requestMatchers("/api/estado-servicios", "/actuator/**")
                    .hasRole("ADMINISTRADOR")
                .requestMatchers("/api/dashboard", "/api/consultoras",
                        "/api/senace/datastream")
                    .hasAnyRole("ANALISTA", "ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    
}