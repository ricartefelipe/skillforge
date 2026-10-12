package skillforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/skillforge",
        "spring.datasource.username=skillforge",
        "spring.datasource.password=closed",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.validation-timeout=500",
                "spring.datasource.hikari.minimum-idle=0",
                "spring.datasource.hikari.maximum-pool-size=1",
                "skillforge.issuer=http://127.0.0.1:1/realms/skillforge",
                "skillforge.identity-timeout=500ms"
})
@AutoConfigureMockMvc
class LivenessWhenDatabaseIsDownTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void processAnswersLivenessWithoutPostgres() throws Exception {
        HealthAssertions.assertProbe(mvc, "/actuator/health/liveness", HttpStatus.OK.value(), "UP");
    }

    @Test
    void readinessRefusesTrafficWhenPostgresDoesNotAcceptConnections() throws Exception {
        HealthAssertions.assertProbe(
                mvc,
                "/actuator/health/readiness",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "DOWN");
    }

}
