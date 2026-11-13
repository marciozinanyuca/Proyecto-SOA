package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecoinvest.soa.entity.Usuario;
import java.sql.DriverManager;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** SQLite exclusivamente en memoria: no abre los .db del proyecto ni crea archivos. */
class RenderAuthSchemaTest {
    @Test
    void freshSchemaMatchesHibernateEntityAndBootstrapPreservesExistingRows() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var script = new ClassPathResource("db/render-auth-schema.sql");
            ScriptUtils.executeSqlScript(connection, script);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO usuarios(nombre, username, password, rol, activo) "
                        + "VALUES ('Artificial', 'fixture-only', 'not-a-real-password', 'ANALISTA', 1)");
            }
            ScriptUtils.executeSqlScript(connection, script);
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT count(*) FROM usuarios")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            var config = new Configuration().addAnnotatedClass(Usuario.class)
                    .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                    .setProperty("hibernate.hbm2ddl.auto", "validate")
                    .setProperty("hibernate.show_sql", "false");
            config.getProperties().put("hibernate.connection.datasource",
                    new SingleConnectionDataSource(connection, true));
            try (var factory = config.buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();
            }
        }
    }
}
