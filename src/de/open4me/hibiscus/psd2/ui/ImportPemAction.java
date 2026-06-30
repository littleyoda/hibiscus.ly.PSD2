package de.open4me.hibiscus.psd2.ui;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.Psd2MenuState;
import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.api.EnableBankingException;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.security.PemKeyReader;
import de.open4me.hibiscus.psd2.security.SecretStore;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class ImportPemAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            if (!Boolean.TRUE.equals(new PemImportIntroductionDialog().open()))
                return;

            FileDialog dialog = new FileDialog(GUI.getShell(), SWT.OPEN);
            dialog.setText("Enable-Banking-PEM-Datei importieren");
            dialog.setFilterExtensions(new String[] { "*.pem", "*.key", "*" });
            String filename = dialog.open();
            if (filename == null)
                return;

            Path pemFile = Path.of(filename);
            String applicationId = applicationIdFromFilename(pemFile);
            PrivateKey key = PemKeyReader.read(pemFile);
            Application.getController().start(new ImportTask(applicationId, key));
        }
        catch (OperationCanceledException e)
        {
            return;
        }
        catch (Exception e)
        {
            throw new ApplicationException("PEM-Import fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static final class ImportTask implements BackgroundTask
    {
        private final String applicationId;
        private final PrivateKey key;
        private volatile boolean interrupted;

        private ImportTask(String applicationId, PrivateKey key)
        {
            this.applicationId = applicationId;
            this.key = key;
        }

        @Override
        public void run(ProgressMonitor monitor) throws ApplicationException
        {
            try
            {
                monitor.setPercentComplete(5);
                monitor.setStatusText("Pruefe Enable-Banking-Anwendung ...");
                EnableBankingClient client = new EnableBankingClient(applicationId, key);
                JsonNode application = client.getApplication();
                boolean callbackKnown = false;
                for (JsonNode redirect : application.path("redirect_urls"))
                    callbackKnown |= Psd2Config.getCallbackUrl().equals(redirect.asText());
                if (!callbackKnown)
                    throw new ApplicationException(
                            "Die konfigurierte Redirect-URL ist fuer diese Anwendung nicht registriert. "
                                    + "Bitte tragen Sie sie bei enablebanking.com in den Applications-Einstellungen nach: "
                                    + Psd2Config.getCallbackUrl());

                checkInterrupted();
                monitor.setPercentComplete(30);
                SecretStore secrets = Psd2Runtime.secrets();
                List<ConnectionState> oldConnections = secrets.getConnections();
                boolean replacing = secrets.hasPrivateKey() || !Psd2Config.getApplicationId().isBlank()
                        || !oldConnections.isEmpty();
                if (replacing && !Application.getCallback().askUser(
                        "Es sind bereits Enable-Banking-Zugangsdaten gespeichert. Beim Import werden alle alten "
                                + "Sessions geschlossen, gespeicherte PSD2-Verbindungen geloescht und die "
                                + "Kontozuordnungen entfernt. Hibiscus-Konten und importierte Umsaetze bleiben erhalten.\n\n"
                                + "Neue PEM-Datei wirklich uebernehmen?",
                        false))
                {
                    monitor.setPercentComplete(100);
                    monitor.setStatusText("PEM-Import wurde nicht ausgefuehrt.");
                    return;
                }

                checkInterrupted();
                String oldApplicationId = Psd2Config.getApplicationId();
                PrivateKey oldKey = secrets.hasPrivateKey() ? secrets.getPrivateKey() : null;
                EnableBankingClient oldClient = oldKey != null && !oldApplicationId.isBlank()
                        ? new EnableBankingClient(oldApplicationId, oldKey)
                        : null;
                ConnectionCleanup.Detachment detachment = null;
                if (!oldConnections.isEmpty())
                {
                    monitor.setStatusText("Entferne alte PSD2-Verbindungen ...");
                    Set<String> connectionIds = new HashSet<>();
                    for (ConnectionState connection : oldConnections)
                        connectionIds.add(connection.id);
                    detachment = ConnectionCleanup.detachAccountsWithRollback(connectionIds);
                }

                try
                {
                    for (ConnectionState connection : oldConnections)
                        secrets.deleteConnection(connection.id);
                    secrets.setPrivateKey(key);
                    Psd2Config.setApplicationId(applicationId);
                }
                catch (Exception failure)
                {
                    rollbackLocalImport(secrets, oldConnections, oldKey, oldApplicationId, detachment, failure);
                    throw failure;
                }

                try
                {
                    GUI.startSync(Psd2MenuState::refresh);
                }
                catch (Exception e)
                {
                    Logger.error("PSD2-Menuezustand konnte nach dem PEM-Import nicht aktualisiert werden", e);
                }
                int sessionsNotClosed = 0;
                if (oldClient != null && !oldConnections.isEmpty())
                {
                    monitor.setStatusText("Schliesse alte Enable-Banking-Sessions ...");
                    sessionsNotClosed = deleteRemoteSessions(oldClient, oldConnections, monitor);
                }
                else if (!oldConnections.isEmpty())
                {
                    sessionsNotClosed = (int) oldConnections.stream()
                            .filter(connection -> connection.sessionId != null && !connection.sessionId.isBlank())
                            .count();
                }
                monitor.setPercentComplete(100);
                String cleanupResult = oldConnections.isEmpty() ? ""
                        : " Alte lokale Verbindungen und Kontozuordnungen wurden entfernt; vorhandene "
                                + "Bankfreigaben wurden soweit moeglich geschlossen.";
                if (sessionsNotClosed > 0)
                    cleanupResult += " " + sessionsNotClosed
                            + " alte Bankfreigabe(n) konnten nicht geschlossen werden und laufen spaetestens "
                            + "mit ihrer bisherigen Gueltigkeit ab.";
                UiSupport.info("PEM-Schluessel und Application-ID " + applicationId
                        + " wurden geprueft und gespeichert." + cleanupResult);
            }
            catch (ApplicationException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new ApplicationException("PEM-Import fehlgeschlagen: " + e.getMessage(), e);
            }
        }

        private int deleteRemoteSessions(EnableBankingClient client, List<ConnectionState> connections,
                ProgressMonitor monitor)
        {
            int completed = 0;
            int failures = 0;
            for (ConnectionState connection : connections)
            {
                if (connection.sessionId != null && !connection.sessionId.isBlank())
                {
                    try
                    {
                        client.deleteSession(connection.sessionId);
                    }
                    catch (EnableBankingException e)
                    {
                        if (e.getStatus() != 404 && !e.isExpiredSession())
                        {
                            failures++;
                            Logger.warn("Alte Session fuer " + connection.aspspName
                                    + " konnte nicht geschlossen werden: " + e.getMessage());
                        }
                    }
                    catch (Exception e)
                    {
                        failures++;
                        Logger.warn("Alte Session fuer " + connection.aspspName
                                + " konnte nicht geschlossen werden: " + e.getMessage());
                    }
                }
                completed++;
                monitor.setPercentComplete(75 + (completed * 20 / connections.size()));
            }
            return failures;
        }

        private static void rollbackLocalImport(SecretStore secrets, List<ConnectionState> oldConnections,
                PrivateKey oldKey, String oldApplicationId, ConnectionCleanup.Detachment detachment,
                Exception failure)
        {
            try
            {
                Psd2Config.setApplicationId(oldApplicationId);
                if (oldKey == null)
                    secrets.deletePrivateKey();
                else
                    secrets.setPrivateKey(oldKey);
                for (ConnectionState connection : oldConnections)
                    secrets.saveConnection(connection);
            }
            catch (Exception rollbackFailure)
            {
                failure.addSuppressed(rollbackFailure);
            }
            if (detachment != null)
                detachment.restore(failure);
        }

        private void checkInterrupted() throws ApplicationException
        {
            if (interrupted)
                throw new ApplicationException("PEM-Import wurde abgebrochen.");
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

    public static String applicationIdFromFilename(Path pemFile)
    {
        String filename = pemFile.getFileName().toString();
        int extension = filename.lastIndexOf('.');
        String candidate = extension > 0 ? filename.substring(0, extension) : filename;
        try
        {
            String applicationId = UUID.fromString(candidate).toString();
            if (!applicationId.equalsIgnoreCase(candidate))
                throw new IllegalArgumentException();
            return applicationId;
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(
                    "Der PEM-Dateiname muss der Application-ID entsprechen, zum Beispiel "
                            + "18977c78-a7e5-4462-9ffa-9bfbcef29127.pem");
        }
    }
}
