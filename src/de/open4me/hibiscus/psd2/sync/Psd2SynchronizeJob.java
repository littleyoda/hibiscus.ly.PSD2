package de.open4me.hibiscus.psd2.sync;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.api.EnableBankingException;
import de.open4me.hibiscus.psd2.auth.AuthorizationService;
import de.open4me.hibiscus.psd2.model.Aspsp;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.security.SecretStore;
import de.open4me.hibiscus.psd2.ui.AccountMapper;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.SynchronizeOptions;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.messaging.ObjectDeletedMessage;
import de.willuhn.jameica.hbci.messaging.SaldoMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class Psd2SynchronizeJob extends SynchronizeJobKontoauszug
{
    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();
    private static final Object CONNECTION_LOCK = new Object();
    private static final String TRANSACTION_IMPORT_VERSION = "2";

    public void execute(ProgressMonitor monitor) throws Exception
    {
        Konto konto = getKonto();
        if (konto == null)
            konto = (Konto) getContext(CTX_ENTITY);
        monitor.log("PSD2-Synchronisierung fuer " + konto.getLongName());
        monitor.setPercentComplete(1);

        SynchronizeOptions options = new SynchronizeOptions(konto);
        boolean fetchSaldo = options.getSyncSaldo() || Boolean.TRUE.equals(getContext(CTX_FORCE_SALDO));
        boolean fetchTransactions = options.getSyncKontoauszuege() || Boolean.TRUE.equals(getContext(CTX_FORCE_UMSATZ));
        SecretStore secrets = Psd2Runtime.secrets();
        EnableBankingClient client = Psd2Runtime.client();
        ConnectionState connection = requireConnection(konto, secrets);
        connection = ensureAuthorized(connection, client, secrets);
        String accountHash = konto.getMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, "");
        String accountUid = connection.accountUids.get(accountHash);
        if (accountUid == null || accountUid.isBlank())
            throw new ApplicationException("Das Konto ist in der aktuellen PSD2-Session nicht freigegeben.");

        try
        {
            if (fetchSaldo)
                importBalances(konto, client.getBalances(accountUid));
            monitor.setPercentComplete(25);
            if (fetchTransactions)
                importTransactions(konto, accountHash, accountUid, client);
            monitor.setPercentComplete(100);
        }
        catch (EnableBankingException e)
        {
            if (!e.isExpiredSession())
                throw e;
            connection = reauthorize(connection, secrets);
            accountUid = connection.accountUids.get(accountHash);
            if (accountUid == null)
                throw new ApplicationException("Konto wurde bei der erneuten Autorisierung nicht freigegeben.");
            if (fetchSaldo)
                importBalances(konto, Psd2Runtime.client().getBalances(accountUid));
            if (fetchTransactions)
                importTransactions(konto, accountHash, accountUid, Psd2Runtime.client());
        }
    }

    private ConnectionState ensureAuthorized(ConnectionState connection, EnableBankingClient client,
            SecretStore secrets) throws Exception
    {
        try
        {
            JsonNode session = client.getSession(connection.sessionId);
            if ("AUTHORIZED".equals(session.path("status").asText()) && !isExpired(session))
            {
                updateAccountUids(connection, session.path("accounts_data"));
                secrets.saveConnection(connection);
                return connection;
            }
        }
        catch (EnableBankingException e)
        {
            if (!e.isExpiredSession() && e.getStatus() != 404)
                throw e;
        }
        return reauthorize(connection, secrets);
    }

    private ConnectionState reauthorize(ConnectionState old, SecretStore secrets) throws Exception
    {
        synchronized (CONNECTION_LOCK)
        {
            ConnectionState latest = secrets.getConnection(old.id);
            if (latest != null && latest.sessionId != null && !latest.sessionId.equals(old.sessionId))
                return latest;
            Aspsp aspsp = Psd2Runtime.client().getAspsps().stream()
                    .filter(item -> item.name.equals(old.aspspName) && item.country.equals(old.aspspCountry))
                    .findFirst()
                    .orElseThrow(() -> new ApplicationException("ASPSP ist nicht mehr verfuegbar: " + old.aspspName));
            AuthorizationService.AuthorizationResult result = new AuthorizationService()
                    .authorize(aspsp, old.psuType, old.authMethod, old.id);
            new AccountMapper().map(result.connection(), result.accounts());
            return result.connection();
        }
    }

    private static ConnectionState requireConnection(Konto konto, SecretStore secrets) throws Exception
    {
        String id = konto.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, "");
        ConnectionState connection = id.isBlank() ? null : secrets.getConnection(id);
        if (connection == null)
            throw new ApplicationException("Keine gespeicherte PSD2-Verbindung fuer dieses Konto gefunden.");
        return connection;
    }

    private static boolean isExpired(JsonNode session)
    {
        String validUntil = session.path("access").path("valid_until").asText();
        if (validUntil.isBlank())
            return false;
        try
        {
            return java.time.Instant.parse(validUntil).isBefore(java.time.Instant.now());
        }
        catch (DateTimeParseException e)
        {
            return true;
        }
    }

    private static void updateAccountUids(ConnectionState connection, JsonNode accounts)
    {
        for (JsonNode account : accounts)
        {
            String hash = account.path("identification_hash").asText();
            String uid = account.path("uid").asText();
            if (!hash.isBlank() && !uid.isBlank())
                connection.accountUids.put(hash, uid);
        }
    }

    private static void importBalances(Konto konto, JsonNode response) throws Exception
    {
        JsonNode booked = preferredBalance(response.path("balances"), "CLBD", "ITBD", "PRCD", "OTHR");
        JsonNode available = preferredBalance(response.path("balances"), "ITAV", "CLAV", "FWAV", "OPAV");
        if (booked != null)
            konto.setSaldo(booked.path("balance_amount").path("amount").asDouble());
        if (available != null)
            konto.setSaldoAvailable(available.path("balance_amount").path("amount").asDouble());
        else if (booked != null)
            konto.setSaldoAvailable(konto.getSaldo());
        konto.store();
        Application.getMessagingFactory().sendMessage(new SaldoMessage(konto));
    }

    private static JsonNode preferredBalance(JsonNode balances, String... types)
    {
        for (String type : types)
            for (JsonNode balance : balances)
                if (type.equals(balance.path("balance_type").asText()))
                    return balance;
        return null;
    }

    private static void importTransactions(Konto konto, String accountHash, String accountUid,
            EnableBankingClient client) throws Exception
    {
        LocalDate from = determineFrom(konto);
        Map<String, Umsatz> existing = existingTransactions(konto);
        Set<String> returnedPending = new HashSet<>();
        List<Umsatz> created = new ArrayList<>();
        List<JsonNode> responsePages = new ArrayList<>();
        String continuation = null;
        do
        {
            JsonNode page = client.getTransactions(accountUid, from.toString(), continuation);
            responsePages.add(page.deepCopy());
            for (JsonNode transaction : page.path("transactions"))
            {
                String status = transaction.path("status").asText();
                if (!Set.of("BOOK", "PDNG", "HOLD").contains(status))
                    continue;
                String id = TransactionSupport.stableId(accountHash, transaction);
                Umsatz old = existing.get(id);
                boolean pending = !"BOOK".equals(status);
                if (pending)
                    returnedPending.add(id);
                if (old != null && !(old.hasFlag(Umsatz.FLAG_NOTBOOKED) && !pending))
                    continue;

                Umsatz target = old != null ? old : (Umsatz) Settings.getDBService().createObject(Umsatz.class, null);
                populate(target, konto, id, transaction, pending);
                if (old == null)
                    created.add(target);
                else
                {
                    target.store();
                    Application.getMessagingFactory().sendMessage(new ImportMessage(target));
                }
            }
            continuation = page.path("continuation_key").asText(null);
        }
        while (continuation != null && !continuation.isBlank());

        TransactionDebugExporter.saveIfEnabled(konto, from, responsePages);

        created.sort(Comparator.comparing(transaction -> {
            try
            {
                return transaction.getDatum();
            }
            catch (RemoteException e)
            {
                return new Date(0);
            }
        }));
        for (Umsatz transaction : created)
        {
            transaction.store();
            Application.getMessagingFactory().sendMessage(new ImportMessage(transaction));
        }
        deleteMissingPending(existing, returnedPending, from);
        konto.setMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, LocalDate.now(LOCAL_ZONE).toString());
        konto.setMeta(Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION, TRANSACTION_IMPORT_VERSION);
        konto.store();
    }

    private static void populate(Umsatz target, Konto konto, String id, JsonNode transaction,
            boolean pending) throws Exception
    {
        target.setKonto(konto);
        target.setTransactionId(id);
        target.setBetrag(TransactionSupport.signedAmount(transaction).doubleValue());
        target.setDatum(toDate(transaction.path("booking_date").asText(
                transaction.path("transaction_date").asText(LocalDate.now().toString()))));
        target.setValuta(toDate(transaction.path("value_date").asText(
                transaction.path("booking_date").asText(LocalDate.now().toString()))));
        target.setFlags(pending ? Umsatz.FLAG_NOTBOOKED : Umsatz.FLAG_NONE);
        target.setGegenkontoName(limit(TransactionSupport.counterpartyName(transaction), 70));
        target.setGegenkontoNummer(limit(TransactionSupport.counterpartyIban(transaction), 34));
        target.setGegenkontoBLZ(limit(TransactionSupport.counterpartyBic(transaction), 11));
        target.setZweck(limit(TransactionSupport.purpose(transaction), 255));
        target.setCustomerRef(limit(transaction.path("reference_number").asText(""), 35));
        JsonNode bankCode = transaction.path("bank_transaction_code");
        String art = bankCode.path("description").asText();
        if (art.isBlank())
            art = String.join("/", bankCode.path("code").asText(""), bankCode.path("sub_code").asText(""));
        target.setArt(limit(art, 100));
        if (!transaction.path("balance_after_transaction").isMissingNode()
                && !transaction.path("balance_after_transaction").isNull())
            target.setSaldo(new BigDecimal(transaction.path("balance_after_transaction").path("amount").asText("0")).doubleValue());
    }

    private static Map<String, Umsatz> existingTransactions(Konto konto) throws Exception
    {
        Map<String, Umsatz> result = new HashMap<>();
        DBIterator<Umsatz> iterator = konto.getUmsaetze();
        while (iterator.hasNext())
        {
            Umsatz transaction = iterator.next();
            String id = transaction.getTransactionId();
            if (id != null && id.startsWith("PSD2:"))
                result.put(id, transaction);
        }
        return result;
    }

    private static void deleteMissingPending(Map<String, Umsatz> existing, Set<String> returned,
            LocalDate from) throws Exception
    {
        Date threshold = toDate(from.toString());
        for (Umsatz transaction : existing.values())
        {
            if (transaction.hasFlag(Umsatz.FLAG_NOTBOOKED) && !returned.contains(transaction.getTransactionId())
                    && !transaction.getDatum().before(threshold))
            {
                String id = transaction.getID();
                transaction.delete();
                Application.getMessagingFactory().sendMessage(new ObjectDeletedMessage(transaction, id));
            }
        }
    }

    private static LocalDate determineFrom(Konto konto) throws Exception
    {
        String importVersion = konto.getMeta(Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION, "");
        if (!TRANSACTION_IMPORT_VERSION.equals(importVersion))
            return LocalDate.now(LOCAL_ZONE).minusDays(Psd2Config.getInitialHistoryDays());

        String stored = konto.getMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, "");
        if (!stored.isBlank())
            return LocalDate.parse(stored).minusDays(14);
        return LocalDate.now(LOCAL_ZONE).minusDays(Psd2Config.getInitialHistoryDays());
    }

    private static Date toDate(String value)
    {
        return Date.from(LocalDate.parse(value).atStartOfDay(LOCAL_ZONE).toInstant());
    }

    private static String limit(String value, int max)
    {
        if (value == null)
            return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
