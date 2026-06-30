package de.open4me.hibiscus.psd2.api;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.open4me.hibiscus.psd2.model.Aspsp;
import de.open4me.hibiscus.psd2.model.AuthMethod;

public class EnableBankingClient
{
    public static final String API_ORIGIN = "https://api.enablebanking.com";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String applicationId;
    private final PrivateKey privateKey;

    public EnableBankingClient(String applicationId, PrivateKey privateKey)
    {
        this.applicationId = applicationId;
        this.privateKey = privateKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public JsonNode getApplication() throws Exception
    {
        return request("GET", "/application", null);
    }

    public List<Aspsp> getAspsps() throws Exception
    {
        List<Aspsp> result = new ArrayList<>();
        for (JsonNode node : request("GET", "/aspsps", null).path("aspsps"))
        {
            Aspsp aspsp = new Aspsp();
            aspsp.name = node.path("name").asText();
            aspsp.country = node.path("country").asText();
            aspsp.maximumConsentValidity = node.path("maximum_consent_validity").asLong(86400);
            node.path("psu_types").forEach(type -> aspsp.psuTypes.add(type.asText()));
            for (JsonNode methodNode : node.path("auth_methods"))
            {
                AuthMethod method = new AuthMethod();
                method.name = methodNode.path("name").asText(null);
                method.title = methodNode.path("title").asText(null);
                method.psuType = methodNode.path("psu_type").asText();
                method.approach = methodNode.path("approach").asText();
                method.hidden = methodNode.path("hidden_method").asBoolean(false);
                aspsp.authMethods.add(method);
            }
            result.add(aspsp);
        }
        result.sort(Comparator.comparing(Aspsp::getCountry).thenComparing(Aspsp::getName));
        return result;
    }

    public JsonNode startAuthorization(Aspsp aspsp, String psuType, String authMethod, String state, String redirectUrl)
            throws Exception
    {
        long desired = Duration.ofDays(179).getSeconds();
        long maximum = aspsp.maximumConsentValidity > 60
                ? aspsp.maximumConsentValidity - 60
                : aspsp.maximumConsentValidity;
        long validity = Math.max(1, Math.min(desired, maximum));
        ObjectNode access = MAPPER.createObjectNode();
        access.put("balances", true);
        access.put("transactions", true);
        access.put("valid_until", Instant.now().plus(validity, ChronoUnit.SECONDS).toString());

        ObjectNode bank = MAPPER.createObjectNode();
        bank.put("name", aspsp.name);
        bank.put("country", aspsp.country);

        ObjectNode body = MAPPER.createObjectNode();
        body.set("access", access);
        body.set("aspsp", bank);
        body.put("state", state);
        body.put("redirect_url", redirectUrl);
        body.put("psu_type", psuType);
        if (authMethod != null && !authMethod.isBlank())
            body.put("auth_method", authMethod);
        body.put("language", "de");
        return request("POST", "/auth", body);
    }

    public JsonNode authorizeSession(String code) throws Exception
    {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("code", code);
        return request("POST", "/sessions", body);
    }

    public JsonNode getSession(String sessionId) throws Exception
    {
        return request("GET", "/sessions/" + path(sessionId), null);
    }

    public JsonNode getAccountDetails(String accountUid) throws Exception
    {
        return request("GET", "/accounts/" + path(accountUid) + "/details", null);
    }

    public JsonNode getBalances(String accountUid) throws Exception
    {
        return request("GET", "/accounts/" + path(accountUid) + "/balances", null);
    }

    public JsonNode getTransactions(String accountUid, String dateFrom, String continuationKey)
            throws Exception
    {
        StringBuilder endpoint = new StringBuilder("/accounts/").append(path(accountUid)).append("/transactions");
        endpoint.append("?date_from=").append(query(dateFrom));
        if (continuationKey != null && !continuationKey.isBlank())
            endpoint.append("&continuation_key=").append(query(continuationKey));
        return request("GET", endpoint.toString(), null);
    }

    private JsonNode request(String method, String endpoint, JsonNode body) throws Exception
    {
        String jwt = JwtSigner.create(applicationId, privateKey);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API_ORIGIN + endpoint))
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + jwt)
                .header("User-Agent", "hibiscus.ly.PSD2/0.1.0");
        if (body == null)
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        else
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));

        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank()
                ? MAPPER.createObjectNode()
                : MAPPER.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            String code = json.path("error").asText(json.path("code").asText("HTTP_ERROR"));
            String message = json.path("message").asText(json.path("detail").asText(response.body()));
            throw new EnableBankingException(response.statusCode(), code,
                    "Enable Banking: " + code + (message == null || message.isBlank() ? "" : " - " + message));
        }
        return json;
    }

    private static String path(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
