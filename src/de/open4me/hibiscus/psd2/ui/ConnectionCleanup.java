package de.open4me.hibiscus.psd2.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.open4me.hibiscus.psd2.sync.Psd2SynchronizeBackend;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Konto;

final class ConnectionCleanup
{
    static final List<String> METADATA_KEYS = List.of(
            Psd2SynchronizeBackend.META_CONNECTION_ID,
            Psd2SynchronizeBackend.META_IDENTIFICATION_HASH,
            Psd2SynchronizeBackend.META_ACCOUNT_UID,
            Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC,
            Psd2SynchronizeBackend.META_TRANSACTION_IMPORT_VERSION,
            Psd2SynchronizeBackend.META_RATE_LIMIT_UNTIL,
            Psd2SynchronizeBackend.META_HISTORY_RESET_HANDLED);

    private ConnectionCleanup()
    {
    }

    static void detachAccounts(String connectionId) throws Exception
    {
        detachAccounts(Set.of(connectionId));
    }

    static void detachAccounts(Set<String> connectionIds) throws Exception
    {
        detachAccountsWithRollback(connectionIds);
    }

    static Detachment detachAccountsWithRollback(Set<String> connectionIds) throws Exception
    {
        List<AccountSnapshot> snapshots = new ArrayList<>();
        DBIterator<Konto> accounts = Settings.getDBService().createList(Konto.class);
        while (accounts.hasNext())
        {
            Konto account = accounts.next();
            if (!connectionIds.contains(account.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, "")))
                continue;
            snapshots.add(AccountSnapshot.capture(account));
        }
        Detachment detachment = new Detachment(snapshots);
        try
        {
            for (AccountSnapshot snapshot : snapshots)
                detachAccount(snapshot.account());
            return detachment;
        }
        catch (Exception failure)
        {
            detachment.restore(failure);
            throw failure;
        }
    }

    static void detachAccount(Konto account) throws Exception
    {
        for (String key : METADATA_KEYS)
            account.setMeta(key, null);
        if (Psd2SynchronizeBackend.class.getName().equals(account.getBackendClass()))
            account.setBackendClass(null);
        account.store();
    }

    static final class Detachment
    {
        private final List<AccountSnapshot> snapshots;

        private Detachment(List<AccountSnapshot> snapshots)
        {
            this.snapshots = snapshots;
        }

        void restore(Exception failure)
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
    }

    private record AccountSnapshot(Konto account, String backendClass, Map<String, String> metadata)
    {
        private static AccountSnapshot capture(Konto account) throws Exception
        {
            Map<String, String> metadata = new HashMap<>();
            for (String key : METADATA_KEYS)
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
