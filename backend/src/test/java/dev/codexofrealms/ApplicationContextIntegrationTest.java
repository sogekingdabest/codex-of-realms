package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApplicationContextIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg18-trixie")
            .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithPgvectorAndFlyway() {
        String vectorVersion = jdbcClient.sql("""
                SELECT extversion
                FROM pg_extension
                WHERE extname = 'vector'
                """)
            .query(String.class)
            .single();

        assertThat(vectorVersion).isEqualTo("0.8.6");
    }

    @Test
    void exposesHealthAndOpenApiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void protectsApplicationEndpointsByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/realms"))
            .andExpect(status().isUnauthorized());
    }
}
