package com.ecoinvest.soa.controller;

import com.ecoinvest.soa.entity.Usuario;
import com.ecoinvest.soa.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuariosController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public List<UsuarioResumen> listar() {
        return usuarioRepository.findAll(Sort.by("username"))
                .stream()
                .map(usuario -> new UsuarioResumen(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getUsername(),
                usuario.getRol(),
                usuario.isActivo()))
                .toList();
    }

    public record UsuarioResumen(
            Long id,
            String nombre,
            String username,
            Usuario.Rol rol,
            boolean activo) {

    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumen crear(@RequestBody NuevoUsuario solicitud) {
        String nombre = solicitud.nombre() == null ? "" : solicitud.nombre().strip();
        String username = solicitud.username() == null ? "" : solicitud.username().strip();
        String password = solicitud.password();

        if (nombre.isBlank() || nombre.length() > 120
                || !username.matches("[a-zA-Z0-9._-]{3,60}")
                || password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || solicitud.rol() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Revisa el nombre, el usuario, la contraseña y el rol");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(solicitud.rol());
        usuario.setActivo(true);

        try {
            Usuario guardado = usuarioRepository.save(usuario);
            return new UsuarioResumen(
                    guardado.getId(),
                    guardado.getNombre(),
                    guardado.getUsername(),
                    guardado.getRol(),
                    guardado.isActivo());
        } catch (DataIntegrityViolationException ex) {
            if (usuarioRepository.existsByUsername(username)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El nombre de usuario ya existe");
            }
            throw ex;
        }
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    public record NuevoUsuario(
            String nombre,
            String username,
            String password,
            Usuario.Rol rol) {

    }

    @PatchMapping("/{id}/estado")
    public UsuarioResumen cambiarEstado(
            @PathVariable("id") Long id,
            @RequestBody CambioEstado solicitud,
            Principal principal) {

        if (solicitud.activo() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Indica si la cuenta debe quedar activa");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (!solicitud.activo()
                && ("admin".equals(usuario.getUsername())
                || principal.getName().equals(usuario.getUsername()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Esta cuenta no se puede desactivar");
        }

        usuario.setActivo(solicitud.activo());
        Usuario guardado = usuarioRepository.save(usuario);

        return new UsuarioResumen(
                guardado.getId(),
                guardado.getNombre(),
                guardado.getUsername(),
                guardado.getRol(),
                guardado.isActivo());
    }

    public record CambioEstado(Boolean activo) {

    }
}
