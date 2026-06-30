package de.open4me.hibiscus.psd2.security;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public final class PemKeyReader
{
    private PemKeyReader()
    {
    }

    public static PrivateKey read(Path path) throws IOException
    {
        try (Reader reader = Files.newBufferedReader(path); PEMParser parser = new PEMParser(reader))
        {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (object instanceof PrivateKeyInfo info)
                return converter.getPrivateKey(info);
            if (object instanceof PEMKeyPair pair)
            {
                KeyPair converted = converter.getKeyPair(pair);
                return converted.getPrivate();
            }
            throw new IOException("Die Datei enthaelt keinen unterstuetzten privaten PKCS#1-/PKCS#8-Schluessel.");
        }
    }
}
