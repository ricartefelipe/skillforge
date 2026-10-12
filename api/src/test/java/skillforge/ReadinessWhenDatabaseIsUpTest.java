package skillforge;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReadinessWhenDatabaseIsUpTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final HttpServer IDENTITY = startIdentity();

    private static HttpServer startIdentity() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream response = exchange.getResponseBody()) {
                    response.write(body);
                }
            });
            server.start();
            return server;
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void stopIdentityProvider() {
        IDENTITY.stop(0);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("skillforge.issuer", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort()
                + "/realms/skillforge");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void processAnswersLiveness() throws Exception {
        HealthAssertions.assertProbe(mvc, "/actuator/health/liveness", HttpStatus.OK.value(), "UP");
    }

    @Test
    void readinessAcceptsTrafficWhenPostgresAndIdentityProviderAcceptConnections() throws Exception {
        HealthAssertions.assertProbe(mvc, "/actuator/health/readiness", HttpStatus.OK.value(), "UP");
    }

}
