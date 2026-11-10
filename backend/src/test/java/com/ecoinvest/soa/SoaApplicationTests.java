package com.ecoinvest.soa;

import com.ecoinvest.soa.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")
class SoaApplicationTests {

    @MockBean
    private UsuarioRepository usuarioRepository;

    @Test
    void contextLoads() {
    }
}