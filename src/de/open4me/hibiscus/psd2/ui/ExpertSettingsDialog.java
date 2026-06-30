package de.open4me.hibiscus.psd2.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class ExpertSettingsDialog extends AbstractDialog<ExpertSettingsDialog.Settings>
{
    private static final int WINDOW_WIDTH = 650;

    private final CheckboxInput debugExport;
    private final TextInput callbackUrl;
    private Settings result;

    record Settings(String callbackUrl, boolean debugExport)
    {
    }

    ExpertSettingsDialog()
    {
        super(POSITION_CENTER);
        setTitle("PSD2-Experteneinstellungen");
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
        callbackUrl = new TextInput(Psd2Config.getCallbackUrl());
        callbackUrl.setName("HTTPS-Callback-URI");
        debugExport = new CheckboxInput(Psd2Config.isTransactionDebugExportEnabled());
        debugExport.setName("Transaktionen und Bestände nach Abruf speichern");
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addText(
                "Die Callback-URI muss im Enable-Banking-Control-Panel exakt als Redirect-URL registriert sein. "
                        + "Host, Port und Pfad steuern den lokalen HTTPS-Callback-Server.",
                true);
        container.addInput(callbackUrl);
        container.addSeparator();

        container.addText(
                "Die JSON-Dateien enthalten sensible Kontodaten und werden unverschluesselt gespeichert. "
                        + "Aktivieren Sie diese Funktion nur voruebergehend zur Fehlersuche.",
                true);
        container.addInput(debugExport);
        container.addSeparator();

        container.addText(
                "Das lokale HTTPS-Zertifikat muss normalerweise nicht erneuert werden. Nach einer Erneuerung "
                        + "zeigt der Browser erneut eine Zertifikatswarnung.",
                true);
        ButtonArea certificateButtons = new ButtonArea();
        certificateButtons.addButton("Callback-Zertifikat erneuern", new RegenerateCertificateAction(),
                null, false, "stock_keyring.png");
        container.addButtonArea(certificateButtons);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Uebernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                try
                {
                    String validatedUrl = CallbackUrlValidator.validate((String) callbackUrl.getValue());
                    result = new Settings(validatedUrl, (Boolean) debugExport.getValue());
                    close();
                }
                catch (Exception e)
                {
                    throw new ApplicationException("Callback-URI ungueltig: " + e.getMessage(), e);
                }
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    @Override
    protected Settings getData()
    {
        return result;
    }
}
