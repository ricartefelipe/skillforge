package skillforge.domain.catalog;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class LtiLaunch {

    private LtiLaunch() {
    }

    public static Map<String, String> sign(
            String url,
            String secret,
            String user,
            String role,
            String resource,
            String nonce,
            long timestamp) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("lti_message_type", "basic-lti-launch-request");
        fields.put("lti_version", "LTI-1p0");
        fields.put("resource_link_id", resource);
        fields.put("user_id", user);
        fields.put("roles", role);
        fields.put("lis_person_name_full", user);
        fields.put("oauth_consumer_key", "skillforge");
        fields.put("oauth_nonce", nonce);
        fields.put("oauth_signature_method", "HMAC-SHA1");
        fields.put("oauth_timestamp", Long.toString(timestamp));
        fields.put("oauth_version", "1.0");
        fields.put("oauth_signature", signature("POST", url, fields, secret));
        return Map.copyOf(fields);
    }

    private static String signature(String method, String url, Map<String, String> fields, String secret) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                sorted.put(encode(field.getKey()), encode(field.getValue()));
            }
            List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, String> field : sorted.entrySet()) {
                pairs.add(field.getKey() + "=" + field.getValue());
            }
            String base = encode(method) + "&" + encode(url) + "&" + encode(String.join("&", pairs));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((encode(secret) + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return java.util.Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex) {
            throw new IllegalStateException("launch");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

}
