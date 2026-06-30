package de.open4me.hibiscus.psd2.ui;

import java.net.InetAddress;
import java.net.URI;

public final class CallbackUrlValidator
{
    private CallbackUrlValidator()
    {
    }

    public static String validate(String value) throws Exception
    {
        String normalized = value == null ? "" : value.trim();
        URI uri = URI.create(normalized);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPath().isBlank())
            throw new IllegalArgumentException("Es ist eine vollstaendige HTTPS-URL mit Pfad erforderlich.");
        if (!InetAddress.getByName(uri.getHost()).isLoopbackAddress())
            throw new IllegalArgumentException("Die Callback-URL muss auf localhost/Loopback zeigen.");
        if (uri.getRawQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Query und Fragment sind nicht erlaubt.");
        return normalized;
    }
}
