package de.open4me.hibiscus.psd2.ui;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.UUID;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.Psd2MenuState;
import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.security.PemKeyReader;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.util.ApplicationException;

public class ImportPemAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.OPEN);
        dialog.setText("Enable-Banking-PEM-Datei importieren");
        dialog.setFilterExtensions(new String[] { "*.pem", "*.key", "*" });
        String filename = dialog.open();
        if (filename == null)
            return;

        try
        {
            Path pemFile = Path.of(filename);
            String applicationId = applicationIdFromFilename(pemFile);
            PrivateKey key = PemKeyReader.read(pemFile);
            EnableBankingClient client = new EnableBankingClient(applicationId, key);
            JsonNode application = client.getApplication();
            boolean callbackKnown = false;
            for (JsonNode redirect : application.path("redirect_urls"))
                callbackKnown |= Psd2Config.getCallbackUrl().equals(redirect.asText());
            if (!callbackKnown)
                throw new ApplicationException("Die konfigurierte Redirect-URL ist fuer diese Anwendung nicht registriert. "
                        + "Bitte tragen Sie sie bei enablebanking.com in den Applications-Einstellungen nach: "
                        + Psd2Config.getCallbackUrl());

            Psd2Runtime.secrets().setPrivateKey(key);
            Psd2Config.setApplicationId(applicationId);
            Psd2MenuState.refresh();
            UiSupport.info("PEM-Schluessel und Application-ID " + applicationId
                    + " wurden geprueft und gespeichert.");
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("PEM-Import fehlgeschlagen: " + e.getMessage());
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
