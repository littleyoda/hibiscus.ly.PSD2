package de.open4me.hibiscus.psd2;

import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.SystemMessage;

public class Psd2MenuStateInitializer implements MessageConsumer
{
    @Override
    public Class<?>[] getExpectedMessageTypes()
    {
        return new Class<?>[] { SystemMessage.class };
    }

    @Override
    public void handleMessage(Message message)
    {
        SystemMessage systemMessage = (SystemMessage) message;
        if (systemMessage.getStatusCode() == SystemMessage.SYSTEM_STARTED)
            Psd2MenuState.refresh();
    }

    @Override
    public boolean autoRegister()
    {
        return true;
    }
}
