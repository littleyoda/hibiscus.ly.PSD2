package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.dialogs.ListDialog;
import de.willuhn.jameica.system.Application;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.open4me.hibiscus.psd2.sync.Psd2SynchronizeBackend;
import de.willuhn.util.ApplicationException;

public class ManageConnectionsAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            List<ConnectionState> connections = Psd2Runtime.secrets().getConnections();
            if (connections.isEmpty())
            {
                UiSupport.info("Es sind keine PSD2-Verbindungen gespeichert.");
                return;
            }
            ListDialog dialog = new ListDialog(connections, AbstractDialog.POSITION_CENTER);
            dialog.setTitle("PSD2-Verbindungen");
            dialog.addColumn("Land", "aspspCountry");
            dialog.addColumn("Institut", "aspspName");
            dialog.addColumn("Gueltig bis", "validUntil");
            ConnectionState selected = (ConnectionState) dialog.open();
            if (selected != null && Application.getCallback().askUser(
                    "Soll die ausgewaehlte Verbindung zu " + selected.aspspName + " geloescht werden?"))
            {
                Psd2Runtime.secrets().deleteConnection(selected.id);
                detachAccounts(selected.id);
                UiSupport.info("PSD2-Verbindung und Kontozuordnungen wurden geloescht.");
            }
        }
        catch (Exception e)
        {
            throw new ApplicationException("Verbindungsverwaltung fehlgeschlagen: " + e.getMessage());
        }
    }

    private static void detachAccounts(String connectionId) throws Exception
    {
        DBIterator<Konto> accounts = Settings.getDBService().createList(Konto.class);
        while (accounts.hasNext())
        {
            Konto account = accounts.next();
            if (!connectionId.equals(account.getMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, "")))
                continue;
            account.setMeta(Psd2SynchronizeBackend.META_CONNECTION_ID, null);
            account.setMeta(Psd2SynchronizeBackend.META_IDENTIFICATION_HASH, null);
            account.setMeta(Psd2SynchronizeBackend.META_ACCOUNT_UID, null);
            account.setMeta(Psd2SynchronizeBackend.META_LAST_TRANSACTION_SYNC, null);
            account.setBackendClass(null);
            account.store();
        }
    }
}
