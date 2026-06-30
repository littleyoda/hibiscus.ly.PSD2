package de.open4me.hibiscus.psd2.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.LinkInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class ConnectionPrerequisitesDialog extends AbstractDialog<Boolean>
{
    private static final int WINDOW_WIDTH = 700;
    private static final String INSTRUCTIONS_URL = "https://enablebanking.com/docs/api/linked-accounts";

    private boolean accepted;

    ConnectionPrerequisitesDialog()
    {
        super(POSITION_CENTER);
        setTitle("Neue Bankverbindung: Voraussetzungen");
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addText(
                "Bevor Sie eine neue Bankverbindung einrichten, muss in Hibiscus bereits ein Konto "
                        + "mit der passenden IBAN angelegt sein.\n\n"
                        + "Waehlen Sie fuer dieses Konto als Zugangsweg \"PSD2 via Enable Banking\".\n\n"
                        + "Ausserdem muss das Bankkonto im Enable-Banking-Control-Panel ueber "
                        + "\"Activate by linking accounts\" mit der Application verknuepft sein.",
                true);

        LinkInput instructions = new LinkInput("<a href=\"" + INSTRUCTIONS_URL + "\">" + INSTRUCTIONS_URL + "</a>");
        instructions.setName("Anleitung");
        instructions.addListener(event -> Program.launch(INSTRUCTIONS_URL));
        container.addInput(instructions);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Fortfahren", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                accepted = true;
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    @Override
    protected Boolean getData()
    {
        return accepted;
    }
}
