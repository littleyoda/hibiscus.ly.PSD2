package de.open4me.hibiscus.psd2.sync;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class TransactionSupport
{
    private TransactionSupport()
    {
    }

    public static BigDecimal signedAmount(JsonNode transaction)
    {
        BigDecimal amount = new BigDecimal(transaction.path("transaction_amount").path("amount").asText("0"));
        return "DBIT".equals(transaction.path("credit_debit_indicator").asText())
                ? amount.abs().negate()
                : amount.abs();
    }

    public static String stableId(String accountHash, JsonNode transaction)
    {
        String reference = transaction.path("entry_reference").asText();
        String source = reference.isBlank()
                ? String.join("|",
                        transaction.path("booking_date").asText(),
                        transaction.path("value_date").asText(),
                        transaction.path("transaction_amount").path("amount").asText(),
                        transaction.path("transaction_amount").path("currency").asText(),
                        transaction.path("credit_debit_indicator").asText(),
                        purpose(transaction),
                        counterpartyName(transaction))
                : reference;
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((accountHash + "|" + source).getBytes(StandardCharsets.UTF_8));
            return "PSD2:" + HexFormat.of().formatHex(digest);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static String duplicateId(String baseId, JsonNode transaction, int occurrence)
    {
        String source = String.join("|",
                baseId,
                Integer.toString(occurrence),
                transaction.path("booking_date").asText(),
                transaction.path("value_date").asText(),
                transaction.path("transaction_date").asText(),
                transaction.path("transaction_amount").path("amount").asText(),
                transaction.path("transaction_amount").path("currency").asText(),
                transaction.path("credit_debit_indicator").asText(),
                purpose(transaction),
                counterpartyName(transaction),
                counterpartyIban(transaction));
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "PSD2:" + HexFormat.of().formatHex(digest);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static LocalDate startDate(LocalDate today, int initialHistoryYears, int overlapDays,
            boolean fullHistoryRequested, boolean importVersionCurrent, String lastSync)
    {
        if (useLongestStrategy(fullHistoryRequested, importVersionCurrent, lastSync))
            return today.minusYears(initialHistoryYears);
        return LocalDate.parse(lastSync).minusDays(overlapDays);
    }

    public static boolean useLongestStrategy(boolean fullHistoryRequested, boolean importVersionCurrent,
            String lastSync)
    {
        return fullHistoryRequested || !importVersionCurrent || lastSync == null || lastSync.isBlank();
    }

    public static String continuationKey(JsonNode page)
    {
        String continuation = page.path("continuation_key").asText(null);
        return continuation == null || continuation.isBlank() ? null : continuation;
    }

    public static String purpose(JsonNode transaction)
    {
        List<String> parts = new ArrayList<>();
        transaction.path("remittance_information").forEach(value -> add(parts, value.asText()));
        add(parts, transaction.path("reference_number").asText(null));
        add(parts, transaction.path("note").asText(null));
        return String.join(" // ", parts);
    }

    public static String counterpartyName(JsonNode transaction)
    {
        String side = "DBIT".equals(transaction.path("credit_debit_indicator").asText())
                ? "creditor" : "debtor";
        return transaction.path(side).path("name").asText("");
    }

    public static String counterpartyIban(JsonNode transaction)
    {
        String side = "DBIT".equals(transaction.path("credit_debit_indicator").asText())
                ? "creditor_account" : "debtor_account";
        JsonNode account = transaction.path(side);
        String iban = account.path("iban").asText();
        if (!iban.isBlank())
            return iban;
        return account.path("other").path("identification").asText("");
    }

    public static String counterpartyBic(JsonNode transaction)
    {
        String side = "DBIT".equals(transaction.path("credit_debit_indicator").asText())
                ? "creditor_agent" : "debtor_agent";
        return transaction.path(side).path("bic_fi").asText("");
    }

    private static void add(List<String> values, String value)
    {
        if (value != null && !value.isBlank() && !values.contains(value.trim()))
            values.add(value.trim());
    }
}
