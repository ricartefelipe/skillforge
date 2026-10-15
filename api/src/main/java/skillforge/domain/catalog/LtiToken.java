package skillforge.domain.catalog;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class LtiToken {

    private LtiToken() {
    }

    public static String sign(String kid, String privatePem, String payload) {
        try {
            String header = json("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\",\"typ\":\"JWT\"}");
            String body = json(payload);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(privatePem));
            signature.update((header + "." + body).getBytes(StandardCharsets.US_ASCII));
            return header + "." + body + "." + base64(signature.sign());
        }
        catch (Exception ex) {
            throw new IllegalStateException("token");
        }
    }

    public static String jwk(String kid, String publicPem) {
        try {
            PublicKey key = publicKey(publicPem);
            java.security.interfaces.RSAPublicKey rsa = (java.security.interfaces.RSAPublicKey) key;
            return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                    + kid + "\",\"n\":\"" + unsigned(rsa.getModulus()) + "\",\"e\":\""
                    + unsigned(rsa.getPublicExponent()) + "\"}]}";
        }
        catch (Exception ex) {
            throw new IllegalStateException("jwk");
        }
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return base64(bytes);
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        byte[] der = der(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey publicKey(String pem) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(pem)));
    }

    private static byte[] der(String pem) {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String json(String value) {
        return base64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
