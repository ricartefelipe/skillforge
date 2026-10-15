package skillforge.domain.catalog;

import java.util.Map;

public record SignedLaunch(String url, Map<String, String> fields) {
}
