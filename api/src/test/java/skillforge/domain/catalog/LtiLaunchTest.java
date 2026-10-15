package skillforge.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LtiLaunchTest {

    @Test
    void signsALaunchWithoutPuttingTheSecretInTheForm() {
        Map<String, String> fields = launch("segredo-longo");

        assertEquals("basic-lti-launch-request", fields.get("lti_message_type"));
        assertEquals("skillforge", fields.get("oauth_consumer_key"));
        assertEquals("aluno", fields.get("user_id"));
        assertFalse(fields.containsValue("segredo-longo"));
        assertFalse(fields.get("oauth_signature").isBlank());
    }

    @Test
    void theSameInputsKeepTheSignatureAndAnotherSecretChangesIt() {
        String first = launch("segredo-longo").get("oauth_signature");

        assertEquals(first, launch("segredo-longo").get("oauth_signature"));
        assertNotEquals(first, launch("outro-segredo").get("oauth_signature"));
    }

    private static Map<String, String> launch(String secret) {
        return LtiLaunch.sign(
                "https://tool.example/launch",
                secret,
                "aluno",
                "Learner",
                "aula-1",
                "nonce-fixo",
                1_700_000_000L);
    }

}
