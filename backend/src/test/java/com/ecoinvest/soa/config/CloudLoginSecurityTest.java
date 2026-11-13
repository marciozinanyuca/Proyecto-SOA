package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecoinvest.soa.controller.AuthController;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Cadena real de seguridad y CSRF; identidades artificiales, sin Boot, SQLite o servidores. */
class CloudLoginSecurityTest {
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIXTURE_PASSWORD = "artificial-test-only-123!";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SeguridadConfig.class, AuthController.class, TestRoutes.class})
    static class TestConfiguration {
        @Bean
        UserDetailsService users(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    User.withUsername("fixture-admin").password(encoder.encode(FIXTURE_PASSWORD))
                            .roles("ADMINISTRADOR").build(),
                    User.withUsername("fixture-analista").password(encoder.encode(FIXTURE_PASSWORD))
                            .roles("ANALISTA").build());
        }
    }

    @RestController
    static class TestRoutes {
        @GetMapping({"/api/admin/usuarios", "/api/estado-servicios", "/actuator/health",
                "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/env"})
        Object protectedOrProbeRoute() { return java.util.Map.of("fixture", true); }
    }

    @BeforeAll
    static void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @AfterAll
    static void close() { if (context != null) context.close(); }

    private MockHttpSession login(String username) throws Exception {
        var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var csrf = JSON.readTree(csrfResponse.getResponse().getContentAsString());
        var initialSession = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        var loginResponse = mvc.perform(post("/login").session(initialSession)
                        .param("username", username).param("password", FIXTURE_PASSWORD)
                        .param(csrf.get("parameterName").asText(), csrf.get("token").asText()))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) loginResponse.getRequest().getSession(false);
    }

    @Test
    void administratorKeepsSessionAndCanUseProtectedRoutes() throws Exception {
        var session = login("fixture-admin");
        var result = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(JSON.readTree(result.getResponse().getContentAsString()).get("roles").toString())
                .contains("ROLE_ADMINISTRADOR");
        mvc.perform(get("/api/admin/usuarios").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/estado-servicios").session(session)).andExpect(status().isOk());
        var csrfResponse = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var csrf = JSON.readTree(csrfResponse.getResponse().getContentAsString());
        mvc.perform(post("/logout").session(session)
                        .param(csrf.get("parameterName").asText(), csrf.get("token").asText()))
                .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void analystIsAuthenticatedButCannotAdministerOrReadMonitor() throws Exception {
        var session = login("fixture-analista");
        var result = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(JSON.readTree(result.getResponse().getContentAsString()).get("roles").toString())
                .contains("ROLE_ANALISTA");
        mvc.perform(get("/api/admin/usuarios").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/estado-servicios").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void probesArePublicButAdminAndOtherActuatorPathsRemainProtected() throws Exception {
        for (String path : java.util.List.of("/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/estado-servicios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").param("username", "fixture-admin")
                .param("password", FIXTURE_PASSWORD)).andExpect(status().isForbidden());
    }
}
