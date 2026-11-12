
package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecoinvest.model.DatastreamSenaceResponse;
import com.ecoinvest.model.IndicadorBcrp;
import com.ecoinvest.soa.SoaApplication;
import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.SenaceTransport;
import com.ecoinvest.soa.controller.AdminUsuariosController;
import com.ecoinvest.soa.controller.EcoInvestController;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.ecoinvest.soa.service.UsuarioDetailsService;

/**
 * Verifico que el perfil integracion-local funcione
 * sin infraestructura de persistencia ni llamadas externas.
 */
@SpringBootTest(
    classes = SoaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=",
        "spring.config.import=",
        "spring.profiles.include="
    }
)
@ActiveProfiles(
    profiles = "integracion-local",
    inheritProfiles = false
)
@ContextConfiguration(
    initializers = IntegracionLocalProfileTest.ProfileGuard.class
)
@AutoConfigureMockMvc(addFilters = false)
class IntegracionLocalProfileTest {

    private static final List<String> EXPECTED_EXCLUSIONS = List.of(
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
    );

    @MockBean
    private SenaceTransport transport;

    @MockBean
    private BcrpClient bcrp;

    @MockBean
    private AdminUsuariosController adminUsuariosController;
    
    @MockBean
private UsuarioDetailsService usuarioDetailsService;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper jackson;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    public static class ProfileGuard implements
            ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext application) {

            ConfigurableEnvironment environment =
                    application.getEnvironment();

            assertThat(environment.getActiveProfiles())
                    .containsExactly("integracion-local");

            assertThat(environment.getPropertySources().stream()
                    .anyMatch(source -> source.getName()
                    .contains("application-integracion-local.yml")))
                    .as("El perfil debe cargarse desde su recurso real")
                    .isTrue();

            assertThat(exclusions(environment))
                    .containsAll(EXPECTED_EXCLUSIONS);

            assertThat(environment.getProperty("server.address"))
                    .isEqualTo("127.0.0.1");

            assertThat(environment.getProperty("server.port"))
                    .isEqualTo("8081");

            application.addBeanFactoryPostProcessor(
                    IntegracionLocalProfileTest::rejectPersistenceDefinitions
            );
        }
    }

    private static List<String> exclusions(
            ConfigurableEnvironment environment) {

        return Binder.get(environment)
                .bind(
                    "spring.autoconfigure.exclude",
                    Bindable.listOf(String.class)
                )
                .orElse(List.of());
    }

    private static void rejectPersistenceDefinitions(
            ConfigurableListableBeanFactory beans) {

        for (Class<?> type : List.of(
                DataSource.class,
                EntityManagerFactory.class,
                EntityManager.class,
                JpaRepository.class,
                Repository.class,
                JdbcOperations.class)) {

            assertThat(beans.getBeanNamesForType(type, true, false))
                    .as(
                        "Infraestructura persistente inesperada: %s",
                        type.getSimpleName()
                    )
                    .isEmpty();
        }

        for (String name : List.of(
                "dataSource",
                "entityManagerFactory",
                "entityManager",
                "jdbcTemplate",
                "namedParameterJdbcTemplate")) {

            assertThat(beans.containsBeanDefinition(name))
                    .as("Bean no permitido: %s", name)
                    .isFalse();
        }
    }

    @AfterEach
    void noExternalCalls() {
        verifyNoInteractions(transport, bcrp);
    }

    @Test
    void actualProfileIsLoadedAndExclusionsAreApplied() {

        ConfigurableEnvironment environment =
                context.getEnvironment();

        assertThat(environment.getActiveProfiles())
                .containsExactly("integracion-local");

        assertThat(exclusions(environment))
                .containsAll(EXPECTED_EXCLUSIONS);

        assertThat(ConditionEvaluationReport
                .get(context.getBeanFactory())
                .getExclusions())
                .containsAll(EXPECTED_EXCLUSIONS);

        assertThat(environment.getProperty("server.address"))
                .isEqualTo("127.0.0.1");

        assertThat(environment.getProperty("server.port"))
                .isEqualTo("8081");

        assertThat(context)
                .isNotInstanceOf(WebServerApplicationContext.class);
    }

    @Test
    void persistenceIsAbsentAndEndpointDependenciesRemainAvailable() {

        rejectPersistenceDefinitions(context.getBeanFactory());

        for (Class<?> type : List.of(
                DataSource.class,
                EntityManagerFactory.class,
                EntityManager.class,
                JpaRepository.class,
                Repository.class,
                JdbcOperations.class)) {

            assertThat(context.getBeansOfType(type, true, false))
                    .isEmpty();
        }

        assertThat(context.getBean(EcoInvestController.class))
                .isNotNull();

        assertThat(context.getBean(SenaceClient.class))
                .isNotNull();

        assertThat(context.getBean(CircuitBreakerRegistry.class))
                .isNotNull();

        assertThat(context.getBean(JsonNullableModule.class))
                .isNotNull();

        verifyNoInteractions(transport, bcrp);
    }

    @Test
    void monitorUsesMvcWithoutProviderCallsOrServer() throws Exception {

        mvc.perform(get("/api/estado-servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senace.servicio")
                        .value("SENACE"))
                .andExpect(jsonPath("$.bcrp.servicio")
                        .value("BCRP"))
                .andExpect(jsonPath("$.senace.alcanceMetricas")
                        .value("LLAMADAS_LOGICAS"))
                .andExpect(jsonPath("$.bcrp.alcanceMetricas")
                        .value("LLAMADAS_LOGICAS"))
                .andExpect(jsonPath("$.senace.disponibilidadExterna")
                        .value("NO_VERIFICADA"))
                .andExpect(jsonPath("$.bcrp.disponibilidadExterna")
                        .value("NO_VERIFICADA"))
                .andExpect(jsonPath("$.senace.respaldoLocal")
                        .value("NO_IMPLEMENTADO"))
                .andExpect(jsonPath("$.bcrp.respaldoLocal")
                        .value("NO_IMPLEMENTADO"));
    }

    @Test
    void allFourExistingGetRoutesRemainRegistered() {

        Set<String> actual = mappings.getHandlerMethods()
                .entrySet()
                .stream()
                .filter(entry ->
                    EcoInvestController.class.isAssignableFrom(
                        entry.getValue().getBeanType()
                    )
                )
                .map(Map.Entry::getKey)
                .filter(info ->
                    info.getMethodsCondition()
                            .getMethods()
                            .contains(
                                org.springframework.web.bind.annotation
                                        .RequestMethod.GET
                            )
                )
                .map(RequestMappingInfo::getPatternValues)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrder(
                "/api/dashboard",
                "/api/estado-servicios",
                "/api/consultoras",
                "/api/senace/datastream"
        );
    }

    @Test
    void jacksonPreservesNullableArraysObjectsNullAndDecimalPrecision()
            throws Exception {

        DatastreamSenaceResponse response =
                new DatastreamSenaceResponse();

        response.data(List.of(
                Map.of("campo", "FIXTURE ARTIFICIAL")
        ));

        assertThat(tree(response).get("data").isArray())
                .isTrue();

        response.data(
                Map.of("campo", "FIXTURE ARTIFICIAL")
        );

        assertThat(tree(response)
                .get("data")
                .get("campo")
                .asText())
                .isEqualTo("FIXTURE ARTIFICIAL");

        response.data(null);

        assertThat(tree(response).has("data"))
                .isTrue();

        assertThat(tree(response).get("data").isNull())
                .isTrue();

        BigDecimal value =
                new BigDecimal("0.12345678901234567890123456789");

        IndicadorBcrp indicator = new IndicadorBcrp();
        indicator.setValor(value);

        ObjectMapper precise = jackson.copy()
                .enable(
                    DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
                );

        assertThat(precise.readTree(
                jackson.writeValueAsString(indicator))
                .get("valor")
                .decimalValue())
                .isEqualByComparingTo(value);
    }

    private JsonNode tree(Object value) throws Exception {
        return jackson.readTree(
                jackson.writeValueAsString(value)
        );
    }
}
