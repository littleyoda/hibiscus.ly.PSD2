package de.open4me.hibiscus.psd2.api;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JwtSigner
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private JwtSigner()
    {
    }

    public static String create(String applicationId, PrivateKey privateKey) throws Exception
    {
        long issuedAt = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "RS256");
        header.put("kid", applicationId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("iss", "enablebanking.com");
        body.put("aud", "api.enablebanking.com");
        body.put("iat", issuedAt);
        body.put("exp", issuedAt + 3600);

        String unsigned = encode(header) + "." + encode(body);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + BASE64.encodeToString(signature.sign());
    }

    private static String encode(Object value) throws Exception
    {
        return BASE64.encodeToString(MAPPER.writeValueAsBytes(value));
    }
}
