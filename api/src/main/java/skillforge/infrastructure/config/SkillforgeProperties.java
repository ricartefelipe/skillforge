package skillforge.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "skillforge")
public class SkillforgeProperties {

    private String issuer = "http://localhost:8180/realms/skillforge";

    private Duration identityTimeout = Duration.ofSeconds(2);

    private Catalog catalog = new Catalog();

    private String mailHost = "";

    private int mailPort = 587;

    private String mailUsername = "";

    private String mailPassword = "";

    private String mailFrom = "skillforge@localhost";

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

    public Catalog getCatalog() {
        return catalog;
    }

    public void setCatalog(Catalog catalog) {
        this.catalog = catalog;
    }

    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public int getMailPort() {
        return mailPort;
    }

    public void setMailPort(int mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public static class Catalog {

        private int markdownMax = 100_000;

        private DataSize pdfMax = DataSize.ofMegabytes(100);

        private DataSize videoMax = DataSize.ofMegabytes(512);

        public int getMarkdownMax() {
            return markdownMax;
        }

        public void setMarkdownMax(int markdownMax) {
            this.markdownMax = markdownMax;
        }

        public DataSize getPdfMax() {
            return pdfMax;
        }

        public void setPdfMax(DataSize pdfMax) {
            this.pdfMax = pdfMax;
        }

        public DataSize getVideoMax() {
            return videoMax;
        }

        public void setVideoMax(DataSize videoMax) {
            this.videoMax = videoMax;
        }

    }

}
