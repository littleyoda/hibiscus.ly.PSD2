package de.open4me.hibiscus.psd2.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.eclipse.swt.program.Program;

import com.fasterxml.jackson.databind.JsonNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.model.Aspsp;
import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.open4me.hibiscus.psd2.security.CallbackCertificateStore;
import de.open4me.hibiscus.psd2.security.SecretStore;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.util.ApplicationException;

public class AuthorizationService
{
    public record AuthorizationResult(ConnectionState connection, JsonNode accounts)
    {
    }

    public AuthorizationResult authorize(Aspsp aspsp, String psuType, String authMethod, String existingId) throws Exception
    {
        SecretStore secrets = Psd2Runtime.secrets();
        EnableBankingClient client = Psd2Runtime.client();
        String callbackUrl = Psd2Config.getCallbackUrl();
        String state = randomState();
        CallbackCertificateStore certificateStore = new CallbackCertificateStore(secrets);

        try (CallbackServer callback = new CallbackServer(
                callbackUrl, state, certificateStore.createSslContext()))
        {
            callback.start();
            JsonNode authorization = client.startAuthorization(aspsp, psuType, authMethod, state, callbackUrl);
            openBrowser(authorization.path("url").asText());
            CallbackResult returned = callback.await(Duration.ofMinutes(10));
            if (!returned.isSuccessful())
                throw new ApplicationException("Bankautorisierung fehlgeschlagen: "
                        + (returned.errorDescription() == null ? returned.error() : returned.errorDescription()));

            JsonNode session = client.authorizeSession(returned.code());
            ConnectionState connection = new ConnectionState();
            connection.id = existingId == null ? UUID.randomUUID().toString() : existingId;
            connection.aspspName = aspsp.name;
            connection.aspspCountry = aspsp.country;
            connection.psuType = psuType;
            connection.authMethod = authMethod;
            connection.sessionId = session.path("session_id").asText();
            connection.validUntil = session.path("access").path("valid_until").asText();
            for (JsonNode account : session.path("accounts"))
            {
                String hash = account.path("identification_hash").asText();
                String uid = account.path("uid").asText();
                if (!hash.isBlank() && !uid.isBlank())
                    connection.accountUids.put(hash, uid);
            }
            secrets.saveConnection(connection);
            return new AuthorizationResult(connection, session.path("accounts"));
        }
    }

    private static void openBrowser(String url) throws ApplicationException
    {
        if (url == null || url.isBlank())
            throw new ApplicationException("Enable Banking hat keine Autorisierungs-URL geliefert.");
        final boolean[] opened = { false };
        Runnable launch = () -> opened[0] = Program.launch(url);
        if (GUI.getDisplay().getThread() == Thread.currentThread())
            launch.run();
        else
            GUI.getDisplay().syncExec(launch);
        if (!opened[0])
            throw new ApplicationException("Der Systembrowser konnte nicht gestartet werden: " + url);
    }

    private static String randomState()
    {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
