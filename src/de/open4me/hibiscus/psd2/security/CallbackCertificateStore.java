package de.open4me.hibiscus.psd2.security;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CallbackCertificateStore
{
    private static final char[] KEY_PASSWORD = "hibiscus.ly.PSD2.callback".toCharArray();
    private final SecretStore secrets;

    public CallbackCertificateStore(SecretStore secrets)
    {
        this.secrets = secrets;
    }

    public synchronized X509Certificate getCertificate() throws Exception
    {
        ensureMaterial();
        byte[] der = Base64.getDecoder().decode(secrets.getCallbackCertificate());
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    public synchronized SSLContext createSslContext() throws Exception
    {
        ensureMaterial();
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(secrets.getCallbackPrivateKey())));
        X509Certificate certificate = getCertificate();

        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEY_PASSWORD);
        keyStore.setKeyEntry("callback", key, KEY_PASSWORD, new X509Certificate[] { certificate });
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, KEY_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    public synchronized void regenerate() throws Exception
    {
        secrets.deleteCallbackMaterial();
        ensureMaterial();
    }

    public String getSha256Fingerprint() throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(getCertificate().getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte value : digest)
        {
            if (result.length() > 0)
                result.append(':');
            result.append(String.format("%02X", value));
        }
        return result.toString();
    }

    private void ensureMaterial() throws Exception
    {
        if (secrets.getCallbackPrivateKey() != null && secrets.getCallbackCertificate() != null)
            return;

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=localhost,O=hibiscus.ly.PSD2");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                new BigInteger(128, new SecureRandom()).abs(),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(10 * 365L, ChronoUnit.DAYS)),
                subject,
                pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                new GeneralName(GeneralName.iPAddress, "::1")
        }));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);
        certificate.verify(pair.getPublic());

        secrets.setCallbackMaterial(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(certificate.getEncoded()));
    }
}
