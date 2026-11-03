package com.ecoinvest.soa.config;

import com.ecoinvest.soa.entity.Usuario;
import com.ecoinvest.soa.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UsuariosInicialesConfig {

    @Bean
    ApplicationRunner crearUsuariosIniciales(
            UsuarioRepository repository,
            PasswordEncoder encoder,
            @Value("${ECOINVEST_ADMIN_PASSWORD:}") String claveAdmin,
            @Value("${ECOINVEST_ANALISTA_PASSWORD:}") String claveAnalista) {

        return args -> {
            crearSiNoExiste(repository, encoder, "admin",
                    "Administrador", claveAdmin, Usuario.Rol.ADMINISTRADOR);

            crearSiNoExiste(repository, encoder, "analista",
                    "Analista", claveAnalista, Usuario.Rol.ANALISTA);
        };
    }

    private void crearSiNoExiste(
            UsuarioRepository repository,
            PasswordEncoder encoder,
            String username,
            String nombre,
            String clave,
            Usuario.Rol rol) {

        if (repository.existsByUsername(username)) {
            return;
        }

        if (clave.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la contraseña inicial de " + username);
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setUsername(username);
        usuario.setPassword(encoder.encode(clave));
        usuario.setRol(rol);
        usuario.setActivo(true);

        repository.save(usuario);
    }
}