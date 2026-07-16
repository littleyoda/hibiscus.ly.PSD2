package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;

final class CurrencySelectionDialog extends AbstractDialog<AccountMapper.CurrencyOption>
{
    static final String WARNING = "Hibiscus unterstützt nur Konten in Euro. "
            + "Fremdwährungen werden nicht unterstützt und können  falsch dargestellt werden. "
            + "Wenn ein PSD2-Konto mehrere Salden in verschiedenen Waehrungen liefert, kann Hibiscus "
            + "nur einen davon als Kontosaldo anzeigen.";

    private static final int WINDOW_WIDTH = 620;

    private final SelectInput currency;
    private final String accountLabel;
    private AccountMapper.CurrencyOption result;

    CurrencySelectionDialog(String accountLabel, List<AccountMapper.CurrencyOption> options,
            AccountMapper.CurrencyOption preferred)
    {
        super(POSITION_CENTER);
        this.accountLabel = accountLabel;
        setTitle("Waehrung fuer PSD2-Konto auswaehlen");
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
        currency = new SelectInput(options, preferred);
        currency.setName("Waehrung");
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addText("Enable Banking liefert fuer " + accountLabel
                + " Salden in mehreren Währungen. Bitte wählen Sie, welcher Saldo in Hibiscus "
                + "als Kontosaldo verwendet werden soll.", true);
        container.addText(WARNING, true);
        container.addInput(currency);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Uebernehmen", new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                result = (AccountMapper.CurrencyOption) currency.getValue();
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    @Override
    protected AccountMapper.CurrencyOption getData()
    {
        return result;
    }
}
