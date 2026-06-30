package de.open4me.hibiscus.psd2.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.sync.Psd2SynchronizeBackend;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.messaging.ObjectChangedMessage;
import de.willuhn.jameica.hbci.messaging.SaldoMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public class AccountMapper
{
    public void map(ConnectionState connection, JsonNode authorizedAccounts) throws Exception
    {
        map(connection, authorizedAccounts, AccountSetupMode.USE_EXISTING);
    }

    void map(ConnectionState connection, JsonNode authorizedAccounts, AccountSetupMode mode) throws Exception
    {
        if (GUI.getDisplay().getThread() != Thread.currentThread())
        {
            Exception[] failure = { null };
            GUI.getDisplay().syncExec(() -> {
                try
                {
                    mapOnUi(connection, authorizedAccounts, mode);
                }
                catch (Exception e)
                {
                    failure[0] = e;
                }
            });
            if (failure[0] != null)
                throw failure[0];
            return;
        }
        mapOnUi(connection, authorizedAccounts, mode);
    }

    private void mapOnUi(ConnectionState connection, JsonNode authorizedAccounts, AccountSetupMode mode)
            throws Exception
    {
        if (authorizedAccounts == null || !authorizedAccounts.isArray())
            throw new ApplicationException("Enable Banking hat keine gueltige Kontoliste geliefert.");
        if (mode == AccountSetupMode.CREATE_NEW)
        {
            createAccounts(connection, authorizedAccounts);
            return;
        }
        List<Konto> available = loadAccounts();
        List<Assignment> assignments = planAssignments(connection, authorizedAccounts, available);
        if (authorizedAccounts.isEmpty() && !hasExistingMappings(connection.id, available))
            throw new ApplicationException("Enable Banking hat keine Konten fuer die Zuordnung geliefert.");
        Set<String> selectedIds = new HashSet<>();
        for (Assignment assignment : assignments)
            selectedIds.add(assignment.account().getID());

        Map<String, Konto> affected = new LinkedHashMap<>();
        for (Assignment assignment : assignments)
            affected.put(assignment.account().getID(), assignment.account());
        for (Konto account : available)
        {
            if (connection.id.equals(account.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, ""))
                    && !selectedIds.contains(account.getID()))
                affected.put(account.getID(), account);
        }

        List<AccountSnapshot> snapshots = new ArrayList<>();
        for (Konto account : affected.values())
            snapshots.add(AccountSnapshot.capture(account));
        try
        {
            for (Assignment assignment : assignments)
                apply(connection, assignment);
            for (Konto account : affected.values())
                if (!selectedIds.contains(account.getID()))
                    ConnectionCleanup.detachAccount(account);
        }
        catch (Exception failure)
        {
            rollback(snapshots, failure);
            throw failure;
        }
    }

    private static List<Assignment> planAssignments(ConnectionState connection, JsonNode authorizedAccounts,
            List<Konto> available) throws Exception
    {
        List<Assignment> assignments = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedHashes = new HashSet<>();
        for (JsonNode remote : authorizedAccounts)
        {
            String iban = normalizeIban(remote.path("account_id").path("iban").asText(null));
            String hash = remote.path("identification_hash").asText();
            String uid = remote.path("uid").asText();
            if (hash.isBlank() || uid.isBlank())
                throw new ApplicationException("Enable Banking hat ein Konto ohne eindeutige Kennung geliefert.");
            if (!usedHashes.add(hash))
                throw new ApplicationException("Enable Banking hat ein Konto mehrfach geliefert: " + iban);
            Konto selected = findAutomaticMatch(connection, available, usedIds, hash, iban);
            if (selected == null)
            {
                AccountSelectionDialog.Result choice = SelectionSupport.chooseAccount(
                        available.stream().filter(konto -> {
                            try
                            {
                                return !usedIds.contains(konto.getID());
                            }
                            catch (Exception e)
                            {
                                return false;
                            }
                        }).toList(), iban);
                if (choice == null)
                    throw new ApplicationException("Kontenzuordnung wurde abgebrochen.");
                if (choice.skipped())
                    continue;
                selected = choice.account();
            }

            confirmReplacement(connection, selected);
            assignments.add(new Assignment(selected, hash, uid));
            usedIds.add(selected.getID());
        }
        return assignments;
    }

    static Konto findAutomaticMatch(ConnectionState connection, List<Konto> available, Set<String> usedIds,
            String hash, String iban) throws Exception
    {
        List<Konto> existingMappings = new ArrayList<>();
        for (Konto local : available)
        {
            if (!usedIds.contains(local.getID())
                    && connection.id.equals(local.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, ""))
                    && hash.equals(local.getMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, "")))
                existingMappings.add(local);
        }
        if (existingMappings.size() == 1)
            return existingMappings.get(0);

        List<Konto> ibanMatches = new ArrayList<>();
        for (Konto local : available)
        {
            if (!usedIds.contains(local.getID()) && iban != null && iban.equals(normalizeIban(local.getIban())))
                ibanMatches.add(local);
        }
        return ibanMatches.size() == 1 ? ibanMatches.get(0) : null;
    }

    private static void createAccounts(ConnectionState connection, JsonNode authorizedAccounts) throws Exception
    {
        List<NewAccountData> accountData = new ArrayList<>();
        Set<String> usedHashes = new HashSet<>();
        for (JsonNode remote : authorizedAccounts)
        {
            NewAccountData data = newAccountData(connection, remote);
            if (!usedHashes.add(data.hash()))
                throw new ApplicationException("Enable Banking hat ein Konto mehrfach geliefert: "
                        + displayAccount(data.iban()));
            accountData.add(data);
        }
        if (accountData.isEmpty())
            throw new ApplicationException("Enable Banking hat keine Konten fuer die Anlage geliefert.");

        List<Konto> created = new ArrayList<>();
        try
        {
            for (NewAccountData data : accountData)
            {
                Konto account = (Konto) Settings.getDBService().createObject(Konto.class, null);
                created.add(account);
                populateNewAccount(account, data);
                apply(connection, new Assignment(account, data.hash(), data.uid()));
            }
        }
        catch (Exception failure)
        {
            rollbackCreated(created, failure);
            throw failure;
        }

        for (Konto account : created)
            notifyCreated(account);
    }

    static NewAccountData newAccountData(ConnectionState connection, JsonNode remote) throws ApplicationException
    {
        String hash = remote.path("identification_hash").asText();
        String uid = remote.path("uid").asText();
        if (hash.isBlank() || uid.isBlank())
            throw new ApplicationException("Enable Banking hat ein Konto ohne eindeutige Kennung geliefert.");

        String iban = normalizeIban(remote.path("account_id").path("iban").asText(null));
        String accountNumber = legacyAccountNumber(remote, iban, hash);
        String bankCode = legacyBankCode(remote, iban);
        String aspspName = nonBlank(connection.aspspName, "PSD2");
        String owner = truncate(nonBlank(remote.path("name").asText(null), aspspName), 70);
        String description = truncate(nonBlank(
                remote.path("details").asText(null),
                remote.path("product").asText(null),
                remote.path("name").asText(null),
                aspspName + " PSD2"), 255);
        String currency = remote.path("currency").asText("").trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}"))
            currency = "EUR";
        String bic = remote.path("account_servicer").path("bic_fi").asText("")
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!bic.matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?"))
            bic = null;
        return new NewAccountData(accountNumber, bankCode, owner, description, currency, iban, bic, hash, uid);
    }

    static String legacyAccountNumber(JsonNode remote, String iban, String hash)
    {
        if (iban != null && iban.matches("DE[0-9]{20}"))
            return iban.substring(12);
        JsonNode identifiers = remote.path("all_account_ids");
        if (identifiers.isArray())
        {
            for (JsonNode identifier : identifiers)
            {
                if (!"BBAN".equalsIgnoreCase(identifier.path("scheme_name").asText()))
                    continue;
                String digits = digitsOnly(identifier.path("identification").asText());
                if (!digits.isBlank())
                    return rightmost(digits, 16);
            }
        }
        if (iban != null && iban.length() > 4)
        {
            String digits = digitsOnly(iban.substring(4));
            if (!digits.isBlank())
                return rightmost(digits, 16);
        }
        return Integer.toUnsignedString(hash.hashCode());
    }

    static String legacyBankCode(JsonNode remote, String iban)
    {
        if (iban != null && iban.matches("DE[0-9]{20}"))
            return iban.substring(4, 12);
        String memberId = remote.path("account_servicer").path("clearing_system_member_id")
                .path("member_id").asText("").trim();
        if (memberId.matches("[0-9]+"))
            return rightmost(memberId, 15);
        return "0";
    }

    private static void populateNewAccount(Konto account, NewAccountData data) throws Exception
    {
        account.setKontonummer(data.accountNumber());
        account.setBLZ(data.bankCode());
        account.setName(data.owner());
        account.setBezeichnung(data.description());
        account.setKundennummer(data.accountNumber());
        account.setWaehrung(data.currency());
        account.setIban(data.iban());
        account.setBic(data.bic());
        account.setPassportClass(null);
    }

    private static void rollbackCreated(List<Konto> created, Exception failure)
    {
        for (int i = created.size() - 1; i >= 0; i--)
        {
            try
            {
                created.get(i).delete();
            }
            catch (Exception rollbackFailure)
            {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void notifyCreated(Konto account)
    {
        try
        {
            Application.getMessagingFactory().sendMessage(new ObjectChangedMessage(account));
            Application.getMessagingFactory().sendMessage(new SaldoMessage(account));
        }
        catch (Exception e)
        {
            Logger.warn("Neu angelegtes PSD2-Konto konnte nicht an die Hibiscus-Oberflaeche gemeldet werden: "
                    + e.getMessage());
        }
    }

    private static String displayAccount(String iban)
    {
        return iban == null ? "ohne IBAN" : iban;
    }

    private static String digitsOnly(String value)
    {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static String rightmost(String value, int maximumLength)
    {
        return value.length() <= maximumLength ? value : value.substring(value.length() - maximumLength);
    }

    private static String nonBlank(String... values)
    {
        for (String value : values)
            if (value != null && !value.isBlank())
                return value.trim();
        return "";
    }

    private static String truncate(String value, int maximumLength)
    {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static boolean hasExistingMappings(String connectionId, List<Konto> accounts) throws Exception
    {
        for (Konto account : accounts)
            if (connectionId.equals(account.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, "")))
                return true;
        return false;
    }

    private static void confirmReplacement(ConnectionState connection, Konto selected) throws Exception
    {
        String oldConnection = selected.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, "");
        if (replacesConnection(oldConnection, connection.id))
        {
            if (!Application.getCallback().askUser(
                    "Das Konto " + selected.getLongName()
                            + " ist bereits einer anderen PSD2-Verbindung zugeordnet. Ersetzen?"))
                throw new ApplicationException("Kontenzuordnung wurde nicht bestaetigt.");
            return;
        }

        String oldBackend = selected.getBackendClass();
        if (oldBackend != null && !oldBackend.isBlank()
                && !Psd2SynchronizeBackend.class.getName().equals(oldBackend)
                && !Application.getCallback().askUser(
                        "Das Konto " + selected.getLongName() + " verwendet bereits einen anderen Zugangsweg. Ersetzen?"))
                throw new ApplicationException("Kontenzuordnung wurde nicht bestaetigt.");
    }

    private static void apply(ConnectionState connection, Assignment assignment) throws Exception
    {
        Konto account = assignment.account();
        boolean mappingChanged = !connection.id.equals(
                account.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, ""))
                || !assignment.hash().equals(
                        account.getMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, ""));
        account.setBackendClass(Psd2SynchronizeBackend.class.getName());
        account.setMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, connection.id);
        account.setMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, assignment.hash());
        account.setMeta(Psd2SynchronizeBackend.META_ACCOUNT_UID, assignment.uid());
        if (mappingChanged)
        {
            account.setMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, null);
            account.setMeta(Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION, null);
            account.setMeta(Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL, null);
            account.setMeta(Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED, null);
        }
        account.store();
    }

    private static void rollback(List<AccountSnapshot> snapshots, Exception failure)
    {
        for (int i = snapshots.size() - 1; i >= 0; i--)
        {
            try
            {
                snapshots.get(i).restore();
            }
            catch (Exception rollbackFailure)
            {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static List<Konto> loadAccounts() throws Exception
    {
        List<Konto> result = new ArrayList<>();
        DBIterator<Konto> iterator = Settings.getDBService().createList(Konto.class);
        while (iterator.hasNext())
            result.add(iterator.next());
        return result;
    }

    public static String normalizeIban(String iban)
    {
        if (iban == null || iban.isBlank())
            return null;
        return iban.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean replacesConnection(String existingConnectionId, String newConnectionId)
    {
        return existingConnectionId != null && !existingConnectionId.isBlank()
                && !existingConnectionId.equals(newConnectionId);
    }

    private record Assignment(Konto account, String hash, String uid)
    {
    }

    record NewAccountData(String accountNumber, String bankCode, String owner, String description,
            String currency, String iban, String bic, String hash, String uid)
    {
    }

    private record AccountSnapshot(Konto account, String backendClass, Map<String, String> metadata)
    {
        private static AccountSnapshot capture(Konto account) throws Exception
        {
            Map<String, String> metadata = new HashMap<>();
            for (String key : ConnectionCleanup.METADATA_KEYS)
                metadata.put(key, account.getMeta(key, null));
            return new AccountSnapshot(account, account.getBackendClass(), metadata);
        }

        private void restore() throws Exception
        {
            account.setBackendClass(backendClass);
            for (Map.Entry<String, String> entry : metadata.entrySet())
                account.setMeta(entry.getKey(), entry.getValue());
            account.store();
        }
    }
}
