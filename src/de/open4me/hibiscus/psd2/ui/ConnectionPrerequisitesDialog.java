package de.open4me.hibiscus.psd2.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.LinkInput;
import de.willuhn.jameica.gui.input.RadioInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class ConnectionPrerequisitesDialog extends AbstractDialog<AccountSetupMode>
{
    private static final int WINDOW_WIDTH = 700;
    private static final String INSTRUCTIONS_URL = "https://enablebanking.com/docs/api/linked-accounts";
    private static final String ACCOUNT_MODE_GROUP = "hibiscus.ly.psd2.account-mode";

    private final RadioInput useExisting;
    private final RadioInput createNew;
    private AccountSetupMode result;

    ConnectionPrerequisitesDialog()
    {
        super(POSITION_CENTER);
        setTitle("Neue Bankverbindung: Voraussetzungen");
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
        useExisting = new RadioInput(ACCOUNT_MODE_GROUP, AccountSetupMode.USE_EXISTING);
        useExisting.setName("Vorhandene Hibiscus-Konten verwenden");
        createNew = new RadioInput(ACCOUNT_MODE_GROUP, AccountSetupMode.CREATE_NEW);
        createNew.setName("Neue Hibiscus-Konten automatisch anlegen");
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addText(
                "1. Die gewünschten Bankkonten müssen im Enable-Banking-Control-Panel über "
                        + "\"Activate by linking accounts\" mit der Anwendung verknüpft sein.",
                true);

        LinkInput instructions = new LinkInput("<a href=\"" + INSTRUCTIONS_URL + "\">" + INSTRUCTIONS_URL + "</a>");
        instructions.setName("Anleitung");
        instructions.addListener(event -> Program.launch(INSTRUCTIONS_URL));
        container.addInput(instructions);

        container.addText(
                "2. Wählen Sie, ob freigegebene Bankkonten vorhandenen Hibiscus-Konten zugeordnet "
                        + "oder als neue Hibiscus-Konten angelegt werden sollen.",
                true);
        container.addInput(useExisting);
        container.addText(
                "Vorhandene Konten werden anhand der IBAN zugeordnet. Falls keine eindeutige Zuordnung "
                        + "möglich ist, werden Sie nach dem passenden Konto gefragt.",
                true);
        container.addInput(createNew);
        container.addText(
                "Für jedes freigegebene Bankkonto wird ein neues Hibiscus-Konto angelegt. Der Zugangsweg "
                        + "wird automatisch auf \"PSD2 via Enable Banking\" gesetzt.",
                true);

        RadioInput.select(ACCOUNT_MODE_GROUP, AccountSetupMode.USE_EXISTING);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Fortfahren", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                result = (AccountSetupMode) RadioInput.getValue(ACCOUNT_MODE_GROUP);
                if (result == null)
                    throw new ApplicationException("Bitte waehlen Sie aus, wie die Konten eingerichtet werden sollen.");
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    @Override
    protected AccountSetupMode getData()
    {
        return result;
    }
}
