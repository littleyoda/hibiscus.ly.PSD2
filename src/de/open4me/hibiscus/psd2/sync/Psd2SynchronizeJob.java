package de.open4me.hibiscus.psd2.sync;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.api.EnableBankingClient.TransactionStrategy;
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
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class Psd2SynchronizeJob extends SynchronizeJobKontoauszug
{
    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();
    private static final Object CONNECTION_LOCK = new Object();
    private static final String TRANSACTION_IMPORT_VERSION = "2";
    private static final Duration RATE_LIMIT_WAIT = Duration.ofHours(6);
    private static final DateTimeFormatter RATE_LIMIT_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(LOCAL_ZONE);

    private record TransactionRequest(LocalDate from, TransactionStrategy strategy)
    {
    }

    public void execute(ProgressMonitor monitor) throws Exception
    {
        execute(monitor, () -> false);
    }

    public void execute(ProgressMonitor monitor, BooleanSupplier interrupted) throws Exception
    {
        checkInterrupted(interrupted);
        Konto konto = getKonto();
        if (konto == null)
            konto = (Konto) getContext(CTX_ENTITY);
        monitor.log("PSD2-Synchronisierung fuer " + konto.getLongName());
        monitor.setPercentComplete(1);

        SynchronizeOptions options = new SynchronizeOptions(konto);
        boolean fetchSaldo = options.getSyncSaldo() || Boolean.TRUE.equals(getContext(CTX_FORCE_SALDO));
        boolean fetchTransactions = options.getSyncKontoauszuege() || Boolean.TRUE.equals(getContext(CTX_FORCE_UMSATZ));
        boolean debugExport = Psd2Config.isTransactionDebugExportEnabled() && fetchTransactions;
        boolean fullHistoryRequested = prepareHistoryReset(konto);
        checkRateLimitCooldown(konto);
        SecretStore secrets = Psd2Runtime.secrets();
        EnableBankingClient client = Psd2Runtime.client();
        ConnectionState connection = requireConnection(konto, secrets);
        try
        {
            connection = ensureAuthorized(connection, client, secrets, interrupted);
        }
        catch (EnableBankingException e)
        {
            if (e.isRateLimitExceeded())
                throw rememberRateLimit(konto, e);
            throw e;
        }
        String accountHash = konto.getMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, "");
        String accountUid = connection.accountUids.get(accountHash);
        if (accountUid == null || accountUid.isBlank())
            throw new ApplicationException("Das Konto ist in der aktuellen PSD2-Session nicht freigegeben.");

        try
        {
            JsonNode balanceResponse = null;
            if (fetchSaldo || debugExport)
            {
                checkInterrupted(interrupted);
                balanceResponse = client.getBalances(accountUid);
                if (fetchSaldo)
                    importBalances(konto, balanceResponse);
            }
            monitor.setPercentComplete(25);
            if (fetchTransactions)
                importTransactions(konto, accountHash, accountUid, client, monitor, fullHistoryRequested,
                        interrupted, balanceResponse);
            if (fetchSaldo || fetchTransactions)
                clearRateLimitCooldown(konto);
            monitor.setPercentComplete(100);
        }
        catch (EnableBankingException e)
        {
            if (e.isRateLimitExceeded())
                throw rememberRateLimit(konto, e);
            if (!e.isExpiredSession())
                throw e;
            connection = reauthorize(connection, secrets, interrupted);
            accountUid = connection.accountUids.get(accountHash);
            if (accountUid == null)
                throw new ApplicationException("Konto wurde bei der erneuten Autorisierung nicht freigegeben.");
            try
            {
                JsonNode balanceResponse = null;
                if (fetchSaldo || debugExport)
                {
                    checkInterrupted(interrupted);
                    balanceResponse = Psd2Runtime.client().getBalances(accountUid);
                    if (fetchSaldo)
                        importBalances(konto, balanceResponse);
                }
                if (fetchTransactions)
                    importTransactions(konto, accountHash, accountUid, Psd2Runtime.client(), monitor,
                            fullHistoryRequested, interrupted, balanceResponse);
                if (fetchSaldo || fetchTransactions)
                    clearRateLimitCooldown(konto);
            }
            catch (EnableBankingException retryError)
            {
                if (retryError.isRateLimitExceeded())
                    throw rememberRateLimit(konto, retryError);
                throw retryError;
            }
        }
    }

    private static void checkRateLimitCooldown(Konto konto) throws Exception
    {
        String stored = konto.getMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, "");
        if (stored.isBlank())
            return;
        try
        {
            Instant retryAt = Instant.parse(stored);
            if (retryAt.isAfter(Instant.now()))
                throw rateLimitMessage(retryAt);
        }
        catch (DateTimeParseException e)
        {
            Logger.warn("Ungueltige gespeicherte PSD2-Abrufsperre: " + stored);
        }
        konto.setMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, null);
        konto.store();
    }

    private static ApplicationException rememberRateLimit(Konto konto, EnableBankingException cause)
    {
        Instant retryAt = Instant.now().plus(RATE_LIMIT_WAIT);
        try
        {
            konto.setMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, retryAt.toString());
            konto.store();
        }
        catch (Exception e)
        {
            cause.addSuppressed(e);
            Logger.error("PSD2-Abrufsperre konnte nicht gespeichert werden", e);
        }
        ApplicationException result = rateLimitMessage(retryAt);
        result.initCause(cause);
        return result;
    }

    private static ApplicationException rateLimitMessage(Instant retryAt)
    {
        return new ApplicationException("Das Konto wurde zu oft aktualisiert. Die Bank hat weitere Zugriffe "
                + "voruebergehend gesperrt. Bitte warten Sie mindestens 6 Stunden und synchronisieren Sie "
                + "das Konto erst danach erneut. Naechster Versuch fruehestens ab "
                + RATE_LIMIT_TIME.format(retryAt) + " Uhr.");
    }

    private static void clearRateLimitCooldown(Konto konto) throws Exception
    {
        if (konto.getMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, "").isBlank())
            return;
        konto.setMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, null);
        konto.store();
    }

    private static boolean prepareHistoryReset(Konto konto) throws Exception
    {
        if (konto.getSaldoDatum() != null)
        {
            if (!konto.getMeta(Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED, "").isBlank())
            {
                konto.setMeta(Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED, null);
                konto.store();
            }
            return false;
        }
        if ("true".equals(konto.getMeta(Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED, "")))
            return false;
        konto.setMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, null);
        konto.setMeta(Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION, null);
        konto.setMeta(Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED, "true");
        konto.store();
        return true;
    }

    private ConnectionState ensureAuthorized(ConnectionState connection, EnableBankingClient client,
            SecretStore secrets, BooleanSupplier interrupted) throws Exception
    {
        try
        {
            JsonNode session = client.getSession(connection.sessionId);
            if ("AUTHORIZED".equals(session.path("status").asText()) && !isExpired(session))
            {
                JsonNode accounts = session.get("accounts_data");
                if (accounts != null && accounts.isArray())
                    updateAccountUids(connection, accounts);
                secrets.saveConnection(connection);
                return connection;
            }
        }
        catch (EnableBankingException e)
        {
            if (!e.isExpiredSession() && e.getStatus() != 404)
                throw e;
        }
        return reauthorize(connection, secrets, interrupted);
    }

    private ConnectionState reauthorize(ConnectionState old, SecretStore secrets, BooleanSupplier interrupted)
            throws Exception
    {
        synchronized (CONNECTION_LOCK)
        {
            ConnectionState latest = secrets.getConnection(old.id);
            if (latest != null && latest.sessionId != null && !latest.sessionId.equals(old.sessionId))
                return latest;
            ConnectionState current = latest == null ? old : latest;
            Aspsp aspsp = Psd2Runtime.client().getAspsps().stream()
                    .filter(item -> item.name.equals(current.aspspName)
                            && item.country.equals(current.aspspCountry))
                    .findFirst()
                    .orElseThrow(() -> new ApplicationException(
                            "ASPSP ist nicht mehr verfuegbar: " + current.aspspName));
            AuthorizationService.AuthorizationResult result = new AuthorizationService()
                    .authorize(aspsp, current.psuType, current.authMethod, current.id, interrupted);
            try
            {
                new AccountMapper().map(result.connection(), result.accounts());
                return result.connection();
            }
            catch (Exception failure)
            {
                try
                {
                    Psd2Runtime.client().deleteSession(result.connection().sessionId);
                }
                catch (Exception cleanup)
                {
                    failure.addSuppressed(cleanup);
                }
                try
                {
                    secrets.saveConnection(current);
                }
                catch (Exception cleanup)
                {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
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
        Map<String, String> refreshed = new LinkedHashMap<>();
        for (JsonNode account : accounts)
        {
            String hash = account.path("identification_hash").asText();
            String uid = account.path("uid").asText();
            if (!hash.isBlank() && !uid.isBlank())
                refreshed.put(hash, uid);
        }
        connection.replaceAccountUids(refreshed);
    }

    private static void importBalances(Konto konto, JsonNode response) throws Exception
    {
        JsonNode booked = preferredBalance(response.path("balances"), "CLBD", "ITBD", "PRCD", "OTHR", "XPCD");
        JsonNode available = preferredBalance(response.path("balances"), "ITAV", "CLAV", "FWAV", "OPAV", "XPCD");
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
            EnableBankingClient client, ProgressMonitor monitor, boolean fullHistoryRequested,
            BooleanSupplier interrupted, JsonNode balanceResponse) throws Exception
    {
        Map<String, Umsatz> existing = existingTransactions(konto);
        TransactionRequest request = determineTransactionRequest(konto, fullHistoryRequested);
        LocalDate from = request.from();
        monitor.log("Rufe PSD2-Umsaetze ab " + from + " ab (Strategie: "
                + request.strategy().queryValue() + ").");
        Set<String> returnedPending = new HashSet<>();
        List<Umsatz> created = new ArrayList<>();
        List<JsonNode> responsePages = Psd2Config.isTransactionDebugExportEnabled() ? new ArrayList<>() : null;
        Set<String> continuationKeys = new HashSet<>();
        Map<String, Integer> idOccurrences = new HashMap<>();
        String continuation = null;
        do
        {
            checkInterrupted(interrupted);
            JsonNode page = client.getTransactions(
                    accountUid, from.toString(), continuation, request.strategy());
            if (responsePages != null)
                responsePages.add(page.deepCopy());
            for (JsonNode transaction : page.path("transactions"))
            {
                String status = transaction.path("status").asText();
                if (!Set.of("BOOK", "PDNG", "HOLD").contains(status))
                    continue;
                String baseId = TransactionSupport.stableId(accountHash, transaction);
                int occurrence = idOccurrences.merge(baseId, 1, Integer::sum) - 1;
                String id = occurrence == 0 ? baseId
                        : TransactionSupport.duplicateId(baseId, transaction, occurrence);
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
            continuation = TransactionSupport.continuationKey(page);
            if (continuation != null && !continuationKeys.add(continuation))
                throw new ApplicationException("Enable Banking hat einen Continuation-Key wiederholt. "
                        + "Der Umsatzabruf wurde zum Schutz vor einer Endlosschleife abgebrochen.");
        }
        while (continuation != null && !continuation.isBlank());

        if (responsePages != null)
            TransactionDebugExporter.saveIfEnabled(konto, from, balanceResponse, responsePages);

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
                transaction.path("booking_date").asText(
                        transaction.path("transaction_date").asText(LocalDate.now().toString())))));
        int flags = target.getFlags() & ~Umsatz.FLAG_NOTBOOKED;
        target.setFlags(pending ? flags | Umsatz.FLAG_NOTBOOKED : flags);
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

    @SuppressWarnings("unchecked")
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

    private static TransactionRequest determineTransactionRequest(Konto konto, boolean fullHistoryRequested)
            throws Exception
    {
        String importVersion = konto.getMeta(Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION, "");
        String stored = konto.getMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, "");
        boolean importVersionCurrent = TRANSACTION_IMPORT_VERSION.equals(importVersion);
        LocalDate from = TransactionSupport.startDate(
                LocalDate.now(LOCAL_ZONE),
                Psd2Config.getInitialHistoryYears(),
                14,
                fullHistoryRequested,
                importVersionCurrent,
                stored);
        TransactionStrategy strategy = TransactionSupport.useLongestStrategy(
                fullHistoryRequested, importVersionCurrent, stored)
                        ? TransactionStrategy.LONGEST
                        : TransactionStrategy.DEFAULT;
        return new TransactionRequest(from, strategy);
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

    private static void checkInterrupted(BooleanSupplier interrupted) throws OperationCanceledException
    {
        if (interrupted.getAsBoolean())
            throw new OperationCanceledException("Synchronisierung durch Benutzer abgebrochen");
    }
}
