package skillforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.MultipartConfigElement;

@Configuration
class UploadCeiling {

    static final long BYTES = 512L * 1024 * 1024;

    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", BYTES, BYTES, 0);
    }

}
