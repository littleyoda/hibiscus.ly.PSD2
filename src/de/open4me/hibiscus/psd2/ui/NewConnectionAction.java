package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.auth.AuthorizationService;
import de.open4me.hibiscus.psd2.auth.AuthorizationService.AuthorizationResult;
import de.open4me.hibiscus.psd2.model.Aspsp;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class NewConnectionAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            AccountSetupMode accountMode = new ConnectionPrerequisitesDialog().open();
            if (accountMode == null)
                return;

            Application.getController().start(new SetupTask(accountMode));
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("PSD2-Verbindung konnte nicht eingerichtet werden: " + e.getMessage(), e);
        }
    }

    private static final class SetupTask implements BackgroundTask
    {
        private final AccountSetupMode accountMode;
        private volatile boolean interrupted;

        private SetupTask(AccountSetupMode accountMode)
        {
            this.accountMode = accountMode;
        }

        @Override
        public void run(ProgressMonitor monitor) throws ApplicationException
        {
            AuthorizationResult result = null;
            try
            {
                monitor.setPercentComplete(2);
                monitor.setStatusText("Lade verfuegbare Institute ...");
                List<Aspsp> aspsps = Psd2Runtime.client().getAspsps();
                checkInterrupted();
                Selection selection = chooseSelection(aspsps);
                if (selection == null)
                {
                    monitor.setPercentComplete(100);
                    monitor.setStatusText("Einrichtung wurde nicht ausgefuehrt.");
                    return;
                }
                monitor.setPercentComplete(10);
                monitor.log("Starte PSD2-Autorisierung im Systembrowser ...");
                result = new AuthorizationService().authorize(selection.aspsp(), selection.psuType(),
                        selection.authMethod(), null, this::isInterrupted);
                checkInterrupted();
                monitor.setPercentComplete(75);
                new AccountMapper().map(result.connection(), result.accounts(), accountMode);
                monitor.setPercentComplete(100);
                UiSupport.info(accountMode == AccountSetupMode.CREATE_NEW
                        ? "PSD2-Verbindung wurde eingerichtet und neue Hibiscus-Konten wurden angelegt."
                        : "PSD2-Verbindung wurde eingerichtet und den Hibiscus-Konten zugeordnet.");
            }
            catch (Exception e)
            {
                if (result != null)
                    cleanup(result, e);
                throw e instanceof ApplicationException applicationException
                        ? applicationException
                        : new ApplicationException("PSD2-Verbindung konnte nicht eingerichtet werden: "
                                + e.getMessage(), e);
            }
        }

        private static Selection chooseSelection(List<Aspsp> aspsps) throws Exception
        {
            Selection[] selected = { null };
            Exception[] failure = { null };
            GUI.getDisplay().syncExec(() -> {
                try
                {
                    Aspsp aspsp = SelectionSupport.chooseAspsp(aspsps);
                    if (aspsp == null)
                        return;
                    String psuType = SelectionSupport.choosePsuType(aspsp);
                    if (psuType == null)
                        return;
                    SelectionSupport.AuthMethodSelection authMethod =
                            SelectionSupport.chooseAuthMethod(aspsp, psuType);
                    if (authMethod.cancelled())
                        return;
                    selected[0] = new Selection(aspsp, psuType, authMethod.name());
                }
                catch (Exception e)
                {
                    failure[0] = e;
                }
            });
            if (failure[0] != null)
                throw failure[0];
            return selected[0];
        }

        private void checkInterrupted() throws ApplicationException
        {
            if (interrupted)
                throw new ApplicationException("Einrichtung wurde abgebrochen.");
        }

        private static void cleanup(AuthorizationResult result, Exception failure)
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
                ConnectionCleanup.detachAccounts(result.connection().id);
            }
            catch (Exception cleanup)
            {
                failure.addSuppressed(cleanup);
            }
            try
            {
                Psd2Runtime.secrets().deleteConnection(result.connection().id);
            }
            catch (Exception cleanup)
            {
                failure.addSuppressed(cleanup);
            }
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

        private record Selection(Aspsp aspsp, String psuType, String authMethod)
        {
        }
    }
}
