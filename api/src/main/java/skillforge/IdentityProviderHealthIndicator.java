package skillforge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
class IdentityProviderHealthIndicator implements HealthIndicator {

    private final String discovery;

    private final HttpClient client;

    IdentityProviderHealthIndicator(SkillforgeProperties properties) {
        this.discovery = properties.getIssuer() + "/.well-known/openid-configuration";
        Duration timeout = properties.getIdentityTimeout();
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Health health() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(discovery))
                .timeout(client.connectTimeout().orElse(Duration.ofSeconds(2)))
                .GET()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return Health.up().build();
            }
            return Health.down().build();
        }
        catch (IOException ex) {
            return Health.down().build();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        }
    }

}
