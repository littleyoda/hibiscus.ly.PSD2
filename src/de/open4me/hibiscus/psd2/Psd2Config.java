package de.open4me.hibiscus.psd2;

import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;

public final class Psd2Config
{
    public static final String DEFAULT_CALLBACK_URL = "https://127.0.0.1:18443/callback";

    private Psd2Config()
    {
    }

    private static Settings settings()
    {
        return Application.getPluginLoader().getPlugin(Plugin.class).getResources().getSettings();
    }

    public static String getApplicationId()
    {
        return settings().getString("application.id", "").trim();
    }

    public static void setApplicationId(String value)
    {
        settings().setAttribute("application.id", value == null ? "" : value.trim());
    }

    public static String getCallbackUrl()
    {
        return settings().getString("callback.url", DEFAULT_CALLBACK_URL).trim();
    }

    public static void setCallbackUrl(String value)
    {
        settings().setAttribute("callback.url", value == null ? DEFAULT_CALLBACK_URL : value.trim());
    }

    public static int getInitialHistoryDays()
    {
        return settings().getInt("transactions.initial.days", 90);
    }

    public static boolean isTransactionDebugExportEnabled()
    {
        return settings().getBoolean("transactions.debug.export", false);
    }

    public static void setTransactionDebugExportEnabled(boolean enabled)
    {
        settings().setAttribute("transactions.debug.export", enabled);
    }
}
