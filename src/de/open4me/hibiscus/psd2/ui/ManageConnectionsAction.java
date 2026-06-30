package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingException;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

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
            ConnectionState selected = new ManageConnectionsDialog(connections).open();
            if (selected != null && Application.getCallback().askUser(
                    "Soll die ausgewaehlte Verbindung zu " + selected.aspspName + " geloescht werden? "
                            + "Die zugehoerige Bankfreigabe wird ebenfalls geschlossen."))
            {
                Application.getController().start(new DeleteTask(selected));
            }
        }
        catch (Exception e)
        {
            throw new ApplicationException("Verbindungsverwaltung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static final class DeleteTask implements BackgroundTask
    {
        private final ConnectionState connection;
        private volatile boolean interrupted;

        private DeleteTask(ConnectionState connection)
        {
            this.connection = connection;
        }

        @Override
        public void run(ProgressMonitor monitor) throws ApplicationException
        {
            try
            {
                monitor.setStatusText("Schliesse Enable-Banking-Session ...");
                if (connection.sessionId != null && !connection.sessionId.isBlank())
                {
                    try
                    {
                        Psd2Runtime.client().deleteSession(connection.sessionId);
                    }
                    catch (EnableBankingException e)
                    {
                        if (e.getStatus() != 404 && !e.isExpiredSession())
                            throw e;
                    }
                }
                checkInterrupted();
                monitor.setPercentComplete(60);
                monitor.setStatusText("Entferne lokale Kontozuordnungen ...");
                ConnectionCleanup.Detachment detachment = ConnectionCleanup.detachAccountsWithRollback(
                        java.util.Set.of(connection.id));
                try
                {
                    Psd2Runtime.secrets().deleteConnection(connection.id);
                }
                catch (Exception failure)
                {
                    detachment.restore(failure);
                    throw failure;
                }
                monitor.setPercentComplete(100);
                UiSupport.info("PSD2-Verbindung, Bankfreigabe und Kontozuordnungen wurden geloescht.");
            }
            catch (ApplicationException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new ApplicationException("Verbindung konnte nicht geloescht werden: " + e.getMessage(), e);
            }
        }

        private void checkInterrupted() throws ApplicationException
        {
            if (interrupted)
                throw new ApplicationException("Loeschen der Verbindung wurde abgebrochen.");
        }

        @Override
        public void interrupt()
        {
            interrupted = true;
        }

        @Override
        public boolean isInterrupted()
        {
            return interrupted;
        }
    }
}
