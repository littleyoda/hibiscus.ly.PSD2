package de.open4me.hibiscus.psd2.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

public final class CallbackServer implements AutoCloseable
{
    private final URI callbackUri;
    private final String expectedState;
    private final CompletableFuture<CallbackResult> result = new CompletableFuture<>();
    private final HttpsServer server;

    public CallbackServer(String callbackUrl, String expectedState, SSLContext sslContext) throws Exception
    {
        this.callbackUri = URI.create(callbackUrl);
        this.expectedState = expectedState;
        validateUri(callbackUri);
        InetAddress address = InetAddress.getByName(callbackUri.getHost());
        if (!address.isLoopbackAddress())
            throw new IllegalArgumentException("Die Callback-Adresse muss auf Loopback zeigen.");
        int port = callbackUri.getPort() > 0 ? callbackUri.getPort() : 443;
        this.server = HttpsServer.create(new InetSocketAddress(address, port), 0);
        this.server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        this.server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PSD2 callback");
            thread.setDaemon(true);
            return thread;
        }));
        this.server.createContext(callbackUri.getPath(), this::handle);
    }

    public void start()
    {
        server.start();
    }

    public CallbackResult await(Duration timeout) throws Exception
    {
        return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        CallbackResult callback = null;
        int status = 200;
        String message;
        if (!callbackUri.getPath().equals(exchange.getRequestURI().getPath()))
        {
            status = 404;
            message = "Unbekannter Callback-Pfad.";
        }
        else if (!"GET".equals(exchange.getRequestMethod()))
        {
            status = 405;
            message = "Nur GET ist erlaubt.";
        }
        else
        {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if (!constantTimeEquals(expectedState, query.get("state")))
            {
                status = 400;
                message = "Ungueltiger Autorisierungsstatus.";
            }
            else
            {
                callback = new CallbackResult(query.get("code"), query.get("error"), query.get("error_description"));
                message = callback.isSuccessful()
                        ? "Die Bankautorisierung wurde abgeschlossen. Dieses Fenster kann geschlossen werden."
                        : "Die Bankautorisierung wurde abgebrochen oder abgelehnt.";
            }
        }

        byte[] body = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>PSD2</title></head>"
                + "<body><h1>hibiscus.ly.PSD2</h1><p>" + message + "</p></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
        if (callback != null)
            result.complete(callback);
    }

    @Override
    public void close()
    {
        server.stop(0);
        result.cancel(false);
    }

    private static void validateUri(URI uri)
    {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPath().isBlank())
            throw new IllegalArgumentException("Die Callback-URL muss eine vollstaendige HTTPS-URL sein.");
        if (uri.getRawQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Die Callback-URL darf keine Query oder Fragment enthalten.");
    }

    private static Map<String, String> parseQuery(String rawQuery)
    {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null)
            return values;
        for (String pair : rawQuery.split("&"))
        {
            String[] parts = pair.split("=", 2);
            values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return values;
    }

    private static String decode(String value)
    {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String expected, String actual)
    {
        if (expected == null || actual == null)
            return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
