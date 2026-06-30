package de.open4me.hibiscus.psd2.ui;

import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

final class UiSupport
{
    private UiSupport()
    {
    }

    static void info(String message)
    {
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(message, StatusBarMessage.TYPE_INFO));
    }

    static void error(String message, Throwable error)
    {
        Logger.error(message, error);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                message + (error.getMessage() == null ? "" : ": " + error.getMessage()),
                StatusBarMessage.TYPE_ERROR));
    }
}
