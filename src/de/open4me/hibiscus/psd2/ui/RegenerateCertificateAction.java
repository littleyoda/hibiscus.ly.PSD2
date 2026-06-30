package de.open4me.hibiscus.psd2.ui;

import de.open4me.hibiscus.psd2.Psd2Runtime;
import de.open4me.hibiscus.psd2.security.CallbackCertificateStore;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;

public class RegenerateCertificateAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            if (!Application.getCallback().askUser(
                    "Callback-Zertifikat wirklich erneuern? Der Browser zeigt danach erneut eine Zertifikatswarnung."))
                return;
            CallbackCertificateStore store = new CallbackCertificateStore(Psd2Runtime.secrets());
            store.regenerate();
            UiSupport.info("Callback-Zertifikat erneuert. SHA-256: " + store.getSha256Fingerprint());
        }
        catch (Exception e)
        {
            throw new ApplicationException("Zertifikat konnte nicht erneuert werden: " + e.getMessage(), e);
        }
    }
}
