package de.open4me.hibiscus.psd2.ui;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.sync.Psd2SynchronizeBackend;
import de.willuhn.jameica.hbci.rmi.Konto;

public final class AccountMapperTests
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AccountMapperTests()
    {
    }

    public static void run() throws Exception
    {
        testGermanIban();
        testForeignIbanFallback();
        testAccountFieldFallbacks();
        testCurrencyFromBalances();
        testCurrencySelectionOrder();
        testExistingMappingPrecedesIban();
    }

    private static void testGermanIban() throws Exception
    {
        JsonNode remote = MAPPER.readTree("""
                {
                  "account_id":{"iban":"DE89 3704 0044 0532 0130 00"},
                  "all_account_ids":[{"identification":"991234567890123456","scheme_name":"BBAN"}],
                  "account_servicer":{"bic_fi":"COBADEFFXXX",
                    "clearing_system_member_id":{"member_id":"12345"}},
                  "name":"Girokonto",
                  "details":"Privates Girokonto",
                  "currency":"eur",
                  "identification_hash":"hash-de",
                  "uid":"uid-de"
                }
                """);
        AccountMapper.NewAccountData data = AccountMapper.newAccountData(connection("Commerzbank"), remote);
        require("DE89370400440532013000".equals(data.iban()), "German IBAN normalization");
        require("37040044".equals(data.bankCode()), "German bank code");
        require("0532013000".equals(data.accountNumber()), "German account number");
        require("EUR".equals(data.currency()), "Currency normalization");
        require("COBADEFFXXX".equals(data.bic()), "Valid BIC");
        require("Privates Girokonto".equals(data.description()), "Account description");
    }

    private static void testForeignIbanFallback() throws Exception
    {
        JsonNode remote = MAPPER.readTree("""
                {
                  "account_id":{"iban":"NL91ABNA0417164300"},
                  "name":"Betaalrekening",
                  "currency":"EUR",
                  "identification_hash":"hash-nl",
                  "uid":"uid-nl"
                }
                """);
        AccountMapper.NewAccountData data = AccountMapper.newAccountData(connection("ABN AMRO"), remote);
        require("0".equals(data.bankCode()), "Foreign bank code fallback");
        require("0417164300".equals(data.accountNumber()), "Foreign account number digits");

        JsonNode withBban = MAPPER.readTree("""
                {
                  "all_account_ids":[{"identification":"AB12-345678901234567890","scheme_name":"BBAN"}],
                  "account_servicer":{"clearing_system_member_id":{"member_id":"123456789012345678"}},
                  "identification_hash":"hash-bban",
                  "uid":"uid-bban"
                }
                """);
        require("345678901234567890".substring(2).equals(
                AccountMapper.legacyAccountNumber(withBban, null, "hash-bban")), "BBAN length limit");
        require("456789012345678".equals(AccountMapper.legacyBankCode(withBban, null)),
                "Bank code length limit");
    }

    private static void testAccountFieldFallbacks() throws Exception
    {
        String longName = "N".repeat(80);
        String longDetails = "D".repeat(280);
        JsonNode remote = MAPPER.readTree("""
                {
                  "name":"%s",
                  "details":"%s",
                  "currency":"EURO",
                  "account_servicer":{"bic_fi":"invalid"},
                  "identification_hash":"hash-only",
                  "uid":"uid-only"
                }
                """.formatted(longName, longDetails));
        AccountMapper.NewAccountData data = AccountMapper.newAccountData(connection("Testbank"), remote);
        require(data.owner().length() == 70, "Owner length limit");
        require(data.description().length() == 255, "Description length limit");
        require("EUR".equals(data.currency()), "Currency fallback");
        require(data.bic() == null, "Invalid BIC must be omitted");
        require(data.iban() == null, "Missing IBAN");
        require(Integer.toUnsignedString("hash-only".hashCode()).equals(data.accountNumber()),
                "Identification hash fallback");
    }

    private static void testCurrencyFromBalances() throws Exception
    {
        JsonNode remote = MAPPER.readTree("""
                {
                  "currency":"XXX",
                  "identification_hash":"hash-multi",
                  "uid":"uid-multi"
                }
                """);
        JsonNode balances = MAPPER.readTree("""
                {"balances":[{
                  "balance_amount":{"currency":"EUR","amount":"184.22"},
                  "balance_type":"XPCD"
                }]}
                """);
        AccountMapper.NewAccountData data = AccountMapper.newAccountData(connection("Testbank"), remote, balances);
        require("EUR".equals(data.currency()), "XXX must be resolved from balance currency");

        JsonNode usdBalances = MAPPER.readTree("""
                {"balances":[{
                  "balance_amount":{"currency":"USD","amount":"12.00"},
                  "balance_type":"XPCD"
                }]}
                """);
        data = AccountMapper.newAccountData(connection("Testbank"), remote, usdBalances);
        require("USD".equals(data.currency()), "Single foreign balance currency must be retained");
    }

    private static void testCurrencySelectionOrder() throws Exception
    {
        JsonNode balances = MAPPER.readTree("""
                {"balances":[{
                  "balance_amount":{"currency":"USD","amount":"100.00"},
                  "balance_type":"XPCD"
                },{
                  "balance_amount":{"currency":"EUR","amount":"0.00"},
                  "balance_type":"XPCD"
                }]}
                """);
        List<AccountMapper.CurrencyOption> options = AccountMapper.currencyOptions(balances);
        require("EUR".equals(options.get(0).currency()), "EUR must be first proposal when present");
        require(AccountMapper.preferredCurrency(options) == options.get(0), "EUR must be preselected");
        require(CurrencySelectionDialog.WARNING.contains("nur EUR-Konten"),
                "Currency dialog must warn about EUR account limitation");
        require(CurrencySelectionDialog.WARNING.contains("nur einen davon als Kontosaldo"),
                "Currency dialog must warn about single displayed balance");
    }

    private static void testExistingMappingPrecedesIban() throws Exception
    {
        ConnectionState connection = connection("Testbank");
        connection.id = "connection-1";
        Konto sameIban = account("account-1", "DE89370400440532013000", "", "");
        Konto existingMapping = account("account-2", "DE89370400440532013000", connection.id, "hash-2");
        Konto selected = AccountMapper.findAutomaticMatch(connection, List.of(sameIban, existingMapping), Set.of(),
                "hash-2", "DE89370400440532013000");
        require(selected == existingMapping, "Existing identification hash mapping must precede IBAN matches");
    }

    private static Konto account(String id, String iban, String connectionId, String hash)
    {
        return (Konto) Proxy.newProxyInstance(AccountMapperTests.class.getClassLoader(), new Class<?>[] { Konto.class },
                (proxy, method, args) -> switch (method.getName())
                {
                    case "getID" -> id;
                    case "getIban" -> iban;
                    case "getMeta" -> Psd2SynchronizeBackend.META_CONNECTION_ID.equals(args[0])
                            ? connectionId
                            : Psd2SynchronizeBackend.META_IDENTIFICATION_HASH.equals(args[0]) ? hash : args[1];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ConnectionState connection(String aspspName)
    {
        ConnectionState connection = new ConnectionState();
        connection.aspspName = aspspName;
        return connection;
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
