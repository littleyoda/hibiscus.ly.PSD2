package de.open4me.hibiscus.psd2.ui;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.open4me.hibiscus.psd2.ui.ExpertSettingsDialog.Settings;
import de.willuhn.jameica.gui.Action;
import de.willuhn.util.ApplicationException;

public class ExpertSettingsAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            Settings settings = new ExpertSettingsDialog().open();
            if (settings == null)
                return;
            Psd2Config.setCallbackUrl(settings.callbackUrl());
            Psd2Config.setTransactionDebugExportEnabled(settings.debugExport());
            UiSupport.info("Experteneinstellungen wurden gespeichert.");
        }
        catch (Exception e)
        {
            throw new ApplicationException("Experteneinstellungen konnten nicht gespeichert werden: "
                    + e.getMessage(), e);
        }
    }
}
