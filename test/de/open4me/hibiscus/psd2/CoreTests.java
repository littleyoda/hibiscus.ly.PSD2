package de.open4me.hibiscus.psd2;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.open4me.hibiscus.psd2.api.JwtSigner;
import de.open4me.hibiscus.psd2.api.EnableBankingClient.TransactionStrategy;
import de.open4me.hibiscus.psd2.api.EnableBankingException;
import de.open4me.hibiscus.psd2.security.PemKeyReader;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.sync.TransactionDebugExporterTests;
import de.open4me.hibiscus.psd2.sync.TransactionSupport;
import de.open4me.hibiscus.psd2.ui.AccountMapper;
import de.open4me.hibiscus.psd2.ui.AccountMapperTests;
import de.open4me.hibiscus.psd2.ui.AspspSelectionDialogTests;
import de.open4me.hibiscus.psd2.ui.CallbackUrlValidator;
import de.open4me.hibiscus.psd2.ui.ImportPemAction;

public class CoreTests
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception
    {
        testJwt();
        testPem();
        testApplicationIdFromFilename();
        testCallbackUrlValidation();
        testEnableBankingErrors();
        testTransactionMapping();
        testTransactionStartDate();
        testMultipleAccountState();
        testIbanNormalization();
        AccountMapperTests.run();
        AspspSelectionDialogTests.run();
        TransactionDebugExporterTests.run();
        System.out.println("CoreTests: OK");
    }

    private static void testEnableBankingErrors()
    {
        require(new EnableBankingException(429, "ASPSP_RATE_LIMIT_EXCEEDED", "limit").isRateLimitExceeded(),
                "rate limit error detection");
        require(!new EnableBankingException(401, "EXPIRED_SESSION", "expired").isRateLimitExceeded(),
                "non-rate-limit error detection");
        EnableBankingException aspspError = new EnableBankingException(
                502, "ASPSP_ERROR", "Enable Banking: ASPSP_ERROR - Error interacting with ASPSP");
        require(aspspError.getMessage().contains(
                "Bitte Verfügbarkeit des Services prüfen: https://enablebanking.com/cp/aspsps"),
                "ASPSP error must include service availability hint");
    }

    private static void testCallbackUrlValidation() throws Exception
    {
        String value = "https://127.0.0.1:18443/callback";
        require(value.equals(CallbackUrlValidator.validate("  " + value + "  ")), "callback URL normalization");
        requireInvalidCallback("http://127.0.0.1:18443/callback");
        requireInvalidCallback("https://192.0.2.1:18443/callback");
        requireInvalidCallback("https://127.0.0.1:18443");
        requireInvalidCallback("https://127.0.0.1:18443/callback?code=test");
    }

    private static void requireInvalidCallback(String value) throws Exception
    {
        try
        {
            CallbackUrlValidator.validate(value);
            throw new AssertionError("Invalid callback URL must be rejected: " + value);
        }
        catch (IllegalArgumentException expected)
        {
            // Expected.
        }
    }

    private static void testApplicationIdFromFilename()
    {
        String applicationId = "18977c78-a7e5-4462-9ffa-9bfbcef29127";
        require(applicationId.equals(ImportPemAction.applicationIdFromFilename(Path.of(applicationId + ".pem"))),
                "Application ID from PEM filename");
        try
        {
            ImportPemAction.applicationIdFromFilename(Path.of("private-key.pem"));
            throw new AssertionError("Invalid PEM filename must be rejected");
        }
        catch (IllegalArgumentException expected)
        {
            // Expected.
        }
    }

    private static void testJwt() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String token = JwtSigner.create("00000000-0000-0000-0000-000000000001", pair.getPrivate());
        String[] parts = token.split("\\.");
        require(parts.length == 3, "JWT must have three parts");
        JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode body = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        require("RS256".equals(header.path("alg").asText()), "JWT algorithm");
        require("api.enablebanking.com".equals(body.path("aud").asText()), "JWT audience");
        require(body.path("exp").asLong() - body.path("iat").asLong() == 3600, "JWT lifetime");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        require(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "JWT signature");
    }

    private static void testPem() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path pem = Files.createTempFile("psd2-key-", ".pem");
        try
        {
            StringWriter content = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(content))
            {
                writer.writeObject(pair);
            }
            Files.writeString(pem, content.toString());
            require(java.util.Arrays.equals(pair.getPrivate().getEncoded(), PemKeyReader.read(pem).getEncoded()),
                    "PEM private key");
        }
        finally
        {
            Files.deleteIfExists(pem);
        }
    }

    private static void testTransactionMapping() throws Exception
    {
        JsonNode debit = MAPPER.readTree("""
                {"entry_reference":"ref-1","booking_date":"2026-01-02","value_date":"2026-01-03",
                 "transaction_amount":{"amount":"12.34","currency":"EUR"},"credit_debit_indicator":"DBIT",
                 "creditor":{"name":"Shop"},"creditor_account":{"iban":"DE123"},
                 "remittance_information":["Order 42"]}
                """);
        JsonNode credit = debit.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) credit).put("credit_debit_indicator", "CRDT");
        require(TransactionSupport.signedAmount(debit).toPlainString().equals("-12.34"), "debit sign");
        require(TransactionSupport.signedAmount(credit).toPlainString().equals("12.34"), "credit sign");
        require("Shop".equals(TransactionSupport.counterpartyName(debit)), "debit counterparty");
        require(TransactionSupport.stableId("account", debit).equals(TransactionSupport.stableId("account", debit)),
                "stable transaction id");
        require(!TransactionSupport.stableId("account", debit).equals(TransactionSupport.stableId("other", debit)),
                "account-specific transaction id");
        String duplicate = TransactionSupport.duplicateId(TransactionSupport.stableId("account", debit), debit, 1);
        require(!duplicate.equals(TransactionSupport.stableId("account", debit)),
                "duplicate transaction id must differ from base id");
        require(duplicate.equals(TransactionSupport.duplicateId(
                TransactionSupport.stableId("account", debit), debit, 1)), "duplicate transaction id stability");
        JsonNode otherReference = debit.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) otherReference).put("entry_reference", "ref-2");
        require(!TransactionSupport.stableId("account", debit)
                .equals(TransactionSupport.stableId("account", otherReference)), "reference-specific transaction id");
    }

    private static void testTransactionStartDate() throws Exception
    {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 6, 27);
        require(java.time.LocalDate.of(2021, 6, 27).equals(
                TransactionSupport.startDate(today, 5, 14, true, true, "2026-06-27")),
                "saldo reset must use initial history");
        require(java.time.LocalDate.of(2026, 6, 13).equals(
                TransactionSupport.startDate(today, 5, 14, false, true, "2026-06-27")),
                "normal synchronization must use overlap");
        require(java.time.LocalDate.of(2021, 6, 27).equals(
                TransactionSupport.startDate(today, 5, 14, false, false, "2026-06-27")),
                "old import version must use initial history");
        require(TransactionSupport.useLongestStrategy(true, true, "2026-06-27"),
                "saldo reset must use longest strategy");
        require(TransactionSupport.useLongestStrategy(false, false, "2026-06-27"),
                "old import version must use longest strategy");
        require(TransactionSupport.useLongestStrategy(false, true, ""),
                "initial synchronization must use longest strategy");
        require(!TransactionSupport.useLongestStrategy(false, true, "2026-06-27"),
                "normal synchronization must use default strategy");
        require("longest".equals(TransactionStrategy.LONGEST.queryValue()), "longest API query value");
        require("default".equals(TransactionStrategy.DEFAULT.queryValue()), "default API query value");
        JsonNode emptyPage = MAPPER.readTree("{\"transactions\":[],\"continuation_key\":\"next-page\"}");
        require("next-page".equals(TransactionSupport.continuationKey(emptyPage)),
                "empty transaction page must retain continuation key");
    }

    private static void testIbanNormalization()
    {
        require("DE123456".equals(AccountMapper.normalizeIban("de12 3456")), "IBAN normalization");
        require(AccountMapper.normalizeIban(" ") == null, "empty IBAN");
    }

    private static void testMultipleAccountState()
    {
        ConnectionState connection = new ConnectionState();
        connection.accountUids.put("old-account", "old-uid");
        connection.replaceAccountUids(Map.of("new-account-1", "uid-1", "new-account-2", "uid-2"));
        require(!connection.accountUids.containsKey("old-account"), "stale account UID must be removed");
        require(connection.accountUids.size() == 2, "all current account UIDs must be retained");
        require(AccountMapper.replacesConnection("connection-1", "connection-2"),
                "different connection must require replacement confirmation");
        require(!AccountMapper.replacesConnection("connection-1", "connection-1"),
                "same connection must not require replacement confirmation");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
