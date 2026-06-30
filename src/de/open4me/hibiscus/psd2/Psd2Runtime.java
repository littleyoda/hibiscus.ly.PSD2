package de.open4me.hibiscus.psd2;

import de.open4me.hibiscus.psd2.api.EnableBankingClient;
import de.open4me.hibiscus.psd2.security.SecretStore;

public final class Psd2Runtime
{
    private Psd2Runtime()
    {
    }

    public static SecretStore secrets() throws Exception
    {
        return new SecretStore();
    }

    public static EnableBankingClient client() throws Exception
    {
        String applicationId = Psd2Config.getApplicationId();
        if (applicationId.isBlank())
            throw new IllegalStateException("Bitte zuerst die nach der Application-ID benannte PEM-Datei importieren.");
        SecretStore secrets = secrets();
        return new EnableBankingClient(applicationId, secrets.getPrivateKey());
    }
}
