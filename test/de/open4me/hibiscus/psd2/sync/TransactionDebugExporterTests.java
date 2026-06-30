package de.open4me.hibiscus.psd2.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TransactionDebugExporterTests
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TransactionDebugExporterTests()
    {
    }

    public static void run() throws Exception
    {
        JsonNode balances = MAPPER.readTree("""
                {"balances":[{"balance_type":"CLBD","balance_amount":{"amount":"12.34"}}]}
                """);
        JsonNode firstPage = MAPPER.readTree("{\"transactions\":[{\"entry_reference\":\"one\"}]}");
        JsonNode secondPage = MAPPER.readTree("{\"transactions\":[{\"entry_reference\":\"two\"}]}");

        ObjectNode document = TransactionDebugExporter.createDocument(
                "Girokonto", LocalDate.of(2026, 6, 29), balances,
                List.of(firstPage, secondPage), Instant.parse("2026-06-29T10:00:00Z"));

        require(document.path("balances").equals(balances), "Balance response must be exported");
        require(document.path("transactions").size() == 2, "All transaction pages must be exported");
        require("one".equals(document.path("transactions").path(0)
                .path("transactions").path(0).path("entry_reference").asText()),
                "First transaction page must be retained");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
