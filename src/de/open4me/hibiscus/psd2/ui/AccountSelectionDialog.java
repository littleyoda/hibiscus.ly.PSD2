package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.parts.Column;
import de.willuhn.jameica.gui.parts.TablePart;
import de.willuhn.jameica.gui.parts.table.FeatureSummary;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.util.ApplicationException;

final class AccountSelectionDialog extends AbstractDialog<AccountSelectionDialog.Result>
{
    private static final int WINDOW_WIDTH = 750;
    private static final int WINDOW_HEIGHT = 420;

    record Result(Konto account, boolean skipped)
    {
    }

    private final List<Konto> accounts;
    private Result result;
    private TablePart table;

    AccountSelectionDialog(List<Konto> accounts, String iban)
    {
        super(POSITION_CENTER);
        this.accounts = accounts;
        setTitle("Hibiscus-Konto fuer "
                + (iban == null || iban.isBlank() ? "PSD2-Konto" : iban) + " auswaehlen");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true);
        container.addPart(getTable());

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                Konto selected = (Konto) getTable().getSelection();
                if (selected == null)
                    throw new ApplicationException("Bitte waehlen Sie zuerst ein Hibiscus-Konto aus.");
                result = new Result(selected, false);
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton("Überspringen", new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                result = new Result(null, true);
                close();
            }
        }, null, false, "go-next.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private TablePart getTable()
    {
        if (table != null)
            return table;
        table = new TablePart(accounts, new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                result = new Result((Konto) context, false);
                close();
            }
        });
        table.addColumn(new Column("bezeichnung", "Bezeichnung"));
        table.addColumn(new Column("longName", "Konto"));
        table.addColumn(new Column("iban", "IBAN"));
        table.setMulti(false);
        table.removeFeature(FeatureSummary.class);
        return table;
    }

    @Override
    protected Result getData()
    {
        return result;
    }
}
