package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecoinvest.soa.SoaApplication;
import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceTransport;
import com.ecoinvest.soa.controller.EcoInvestController;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** Real Azure ConfigData, in-memory MVC, no listening server or external transport. */
@SpringBootTest(classes = SoaApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = { "spring.config.location=classpath:/application.yml",
                "spring.config.additional-location=", "spring.config.import=", "spring.profiles.include=" })
@ActiveProfiles(profiles = "azure", inheritProfiles = false)
@ContextConfiguration(initializers = AzureProfileTest.ProfileGuard.class)
@AutoConfigureMockMvc
class AzureProfileTest {
    private static final List<String> EXCLUSIONS = List.of(
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration");

    @MockBean private SenaceTransport transport;
    @MockBean private BcrpClient bcrp;
    @Autowired private ConfigurableApplicationContext context;
    @Autowired private MockMvc mvc;

    public static class ProfileGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext application) {
            var environment = application.getEnvironment();
            assertThat(environment.getActiveProfiles()).containsExactly("azure");
            assertThat(environment.getPropertySources().stream()
                    .anyMatch(source -> source.getName().contains("application-azure.yml"))).isTrue();
            assertThat(Binder.get(environment).bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                    .orElse(List.of())).containsAll(EXCLUSIONS);
            assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
            application.addBeanFactoryPostProcessor(AzureProfileTest::rejectPersistence);
        }
    }

    private static void rejectPersistence(ConfigurableListableBeanFactory beans) {
        for (Class<?> type : List.of(DataSource.class, EntityManagerFactory.class, EntityManager.class,
                Repository.class, JdbcOperations.class)) {
            assertThat(beans.getBeanNamesForType(type, true, false)).as(type.getSimpleName()).isEmpty();
        }
        for (String name : List.of("dataSource", "entityManagerFactory", "entityManager", "jdbcTemplate")) {
            assertThat(beans.containsBeanDefinition(name)).as(name).isFalse();
        }
    }

    @AfterEach
    void noProviders() {
        verifyNoInteractions(transport, bcrp);
    }

    @Test
    void realCloudProfileHasNoPersistenceOrListeningServer() {
        rejectPersistence(context.getBeanFactory());
        assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions()).containsAll(EXCLUSIONS);
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
        assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8081");
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(context.getBean(EcoInvestController.class)).isNotNull();
        assertThat(context.getBean(CircuitBreakerRegistry.class)).isNotNull();
        assertThat(context.getBean(JsonNullableModule.class)).isNotNull();
    }

    @Test
    void monitorRemainsLocalAndNeverCallsProviders() throws Exception {
        mvc.perform(get("/api/estado-servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senace.disponibilidadExterna").value("NO_VERIFICADA"))
                .andExpect(jsonPath("$.bcrp.respaldoLocal").value("NO_IMPLEMENTADO"));
    }

    @Test
    void probesDoNotQueryProvidersAndDocumentationIsNotPublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
