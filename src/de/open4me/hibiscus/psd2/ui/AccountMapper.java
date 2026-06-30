package de.open4me.hibiscus.psd2.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.sync.Psd2SynchronizeBackend;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.util.ApplicationException;

public class AccountMapper
{
    public void map(ConnectionState connection, JsonNode authorizedAccounts) throws Exception
    {
        if (GUI.getDisplay().getThread() != Thread.currentThread())
        {
            Exception[] failure = { null };
            GUI.getDisplay().syncExec(() -> {
                try
                {
                    mapOnUi(connection, authorizedAccounts);
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
        mapOnUi(connection, authorizedAccounts);
    }

    private void mapOnUi(ConnectionState connection, JsonNode authorizedAccounts) throws Exception
    {
        List<Konto> available = loadAccounts();
        Set<String> usedIds = new HashSet<>();
        for (JsonNode remote : authorizedAccounts)
        {
            String iban = normalizeIban(remote.path("account_id").path("iban").asText(null));
            String hash = remote.path("identification_hash").asText();
            String uid = remote.path("uid").asText();
            List<Konto> matches = new ArrayList<>();
            for (Konto local : available)
            {
                if (!usedIds.contains(local.getID()) && iban != null && iban.equals(normalizeIban(local.getIban())))
                    matches.add(local);
            }
            Konto selected = matches.size() == 1 ? matches.get(0) : SelectionSupport.chooseAccount(
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
            if (selected == null)
                throw new ApplicationException("Kontenzuordnung wurde abgebrochen.");

            String oldBackend = selected.getBackendClass();
            if (oldBackend != null && !oldBackend.isBlank()
                    && !Psd2SynchronizeBackend.class.getName().equals(oldBackend)
                    && !Application.getCallback().askUser(
                            "Das Konto " + selected.getLongName() + " verwendet bereits einen anderen Zugangsweg. Ersetzen?"))
                throw new ApplicationException("Kontenzuordnung wurde nicht bestaetigt.");

            selected.setBackendClass(Psd2SynchronizeBackend.class.getName());
            selected.setMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, connection.id);
            selected.setMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, hash);
            selected.setMeta(Psd2SynchronizeBackend.META_ACCOUNT_UID, uid);
            selected.store();
            usedIds.add(selected.getID());
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
}
