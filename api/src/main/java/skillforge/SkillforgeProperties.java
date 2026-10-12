package skillforge;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillforge")
public class SkillforgeProperties {

    private String issuer = "http://localhost:8180/realms/skillforge";

    private Duration identityTimeout = Duration.ofSeconds(2);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getIdentityTimeout() {
        return identityTimeout;
    }

    public void setIdentityTimeout(Duration identityTimeout) {
        this.identityTimeout = identityTimeout;
    }

}
