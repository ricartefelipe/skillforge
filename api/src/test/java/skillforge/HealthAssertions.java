package skillforge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

final class HealthAssertions {

    private HealthAssertions() {
    }

    static void assertProbe(MockMvc mvc, String path, int httpStatus, String bodyStatus) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.status").value(bodyStatus))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

}
