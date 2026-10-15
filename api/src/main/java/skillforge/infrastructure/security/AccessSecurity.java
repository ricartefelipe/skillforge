package skillforge.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import skillforge.infrastructure.config.SkillforgeProperties;

@Configuration
class AccessSecurity {

    @Bean
    SecurityFilterChain access(HttpSecurity http) throws Exception {
        // The API has no cookie session. A stolen page cannot forge a bearer call.
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/me").authenticated()
                .requestMatchers("/courses", "/courses/**", "/modules/**", "/lessons/**").authenticated()
                .requestMatchers("/cohorts", "/cohorts/**").authenticated()
                .requestMatchers("/grades", "/resume", "/inbox", "/inbox/**").authenticated()
                .anyRequest().denyAll());
        http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    LazyIdentityJwtDecoder jwtDecoder(SkillforgeProperties properties) {
        // The first protected call resolves keys. A down provider must not stop liveness.
        return new LazyIdentityJwtDecoder(properties.getIssuer());
    }

}
