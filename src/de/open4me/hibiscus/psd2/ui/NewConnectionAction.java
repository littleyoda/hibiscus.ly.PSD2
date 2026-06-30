package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.auth.AuthorizationService;
import de.open4me.hibiscus.psd2.auth.AuthorizationService.AuthorizationResult;
import de.open4me.hibiscus.psd2.model.Aspsp;
import de.willuhn.jameica.gui.Action;
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
            Boolean continueSetup = new ConnectionPrerequisitesDialog().open();
            if (!Boolean.TRUE.equals(continueSetup))
                return;

            List<Aspsp> aspsps = Psd2Runtime.client().getAspsps();
            String country = SelectionSupport.chooseCountry(aspsps);
            if (country == null)
                return;
            List<Aspsp> countryAspsps = aspsps.stream()
                    .filter(aspsp -> country.equals(aspsp.country))
                    .toList();
            Aspsp selected = SelectionSupport.chooseAspsp(countryAspsps);
            if (selected == null)
                return;
            String psuType = SelectionSupport.choosePsuType(selected);
            if (psuType == null)
                return;
            String authMethod = SelectionSupport.chooseAuthMethod(selected, psuType);
            Application.getController().start(new SetupTask(selected, psuType, authMethod));
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("PSD2-Verbindung konnte nicht eingerichtet werden: " + e.getMessage());
        }
    }

    private static final class SetupTask implements BackgroundTask
    {
        private final Aspsp aspsp;
        private final String psuType;
        private final String authMethod;
        private volatile boolean interrupted;

        private SetupTask(Aspsp aspsp, String psuType, String authMethod)
        {
            this.aspsp = aspsp;
            this.psuType = psuType;
            this.authMethod = authMethod;
        }

        @Override
        public void run(ProgressMonitor monitor) throws ApplicationException
        {
            AuthorizationResult result = null;
            try
            {
                monitor.setPercentComplete(5);
                monitor.log("Starte PSD2-Autorisierung im Systembrowser ...");
                result = new AuthorizationService().authorize(aspsp, psuType, authMethod, null);
                if (interrupted)
                    throw new ApplicationException("Einrichtung wurde abgebrochen.");
                monitor.setPercentComplete(75);
                new AccountMapper().map(result.connection(), result.accounts());
                monitor.setPercentComplete(100);
                UiSupport.info("PSD2-Verbindung wurde eingerichtet und den Hibiscus-Konten zugeordnet.");
            }
            catch (Exception e)
            {
                if (result != null)
                {
                    try
                    {
                        Psd2Runtime.secrets().deleteConnection(result.connection().id);
                    }
                    catch (Exception cleanup)
                    {
                        e.addSuppressed(cleanup);
                    }
                }
                throw e instanceof ApplicationException applicationException
                        ? applicationException
                        : new ApplicationException("PSD2-Verbindung konnte nicht eingerichtet werden: " + e.getMessage());
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
    }
}
