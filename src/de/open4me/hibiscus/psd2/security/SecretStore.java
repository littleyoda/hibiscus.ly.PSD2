package de.open4me.hibiscus.psd2.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.willuhn.jameica.security.Wallet;
import de.willuhn.jameica.security.crypto.AESEngine;

public final class SecretStore
{
    private static final String PRIVATE_KEY = "enable-banking.private-key";
    private static final String CONNECTION_PREFIX = "connection.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Wallet wallet;

    public SecretStore() throws Exception
    {
        this.wallet = new Wallet(SecretStore.class, new AESEngine());
    }

    public void setPrivateKey(PrivateKey key) throws Exception
    {
        wallet.set(PRIVATE_KEY, Base64.getEncoder().encodeToString(key.getEncoded()));
    }

    public boolean hasPrivateKey()
    {
        return wallet.get(PRIVATE_KEY) != null;
    }

    public PrivateKey getPrivateKey() throws Exception
    {
        String encoded = (String) wallet.get(PRIVATE_KEY);
        if (encoded == null)
            throw new IllegalStateException("Es wurde noch keine PEM-Datei importiert.");
        byte[] der = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public void saveConnection(ConnectionState connection) throws Exception
    {
        wallet.set(CONNECTION_PREFIX + connection.id, MAPPER.writeValueAsString(connection));
    }

    public ConnectionState getConnection(String id) throws Exception
    {
        String json = (String) wallet.get(CONNECTION_PREFIX + id);
        return json == null ? null : MAPPER.readValue(json, ConnectionState.class);
    }

    public List<ConnectionState> getConnections() throws Exception
    {
        List<ConnectionState> result = new ArrayList<>();
        for (String key : wallet.getAll(CONNECTION_PREFIX))
        {
            String json = (String) wallet.get(key);
            if (json != null)
                result.add(MAPPER.readValue(json, ConnectionState.class));
        }
        return result;
    }

    public void deleteConnection(String id) throws Exception
    {
        wallet.delete(CONNECTION_PREFIX + id);
    }

    public void setCallbackMaterial(String privateKey, String certificate) throws Exception
    {
        wallet.set("callback.private-key", privateKey);
        wallet.set("callback.certificate", certificate);
    }

    public String getCallbackPrivateKey()
    {
        return (String) wallet.get("callback.private-key");
    }

    public String getCallbackCertificate()
    {
        return (String) wallet.get("callback.certificate");
    }

    public void deleteCallbackMaterial() throws Exception
    {
        wallet.delete("callback.private-key");
        wallet.delete("callback.certificate");
    }
}
