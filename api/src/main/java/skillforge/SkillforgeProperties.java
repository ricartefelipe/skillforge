package skillforge;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "skillforge")
public class SkillforgeProperties {

    private String issuer = "http://localhost:8180/realms/skillforge";

    private Duration identityTimeout = Duration.ofSeconds(2);

    private Catalog catalog = new Catalog();

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

    public static class Catalog {

        private int markdownMax = 100_000;

        private DataSize pdfMax = DataSize.ofMegabytes(8);

        private DataSize videoMax = DataSize.ofMegabytes(64);

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
