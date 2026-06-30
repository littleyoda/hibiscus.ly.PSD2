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
import de.willuhn.util.ApplicationException;

final class ChoiceDialog<T> extends AbstractDialog<T>
{
    private static final int WINDOW_WIDTH = 500;

    private final SelectInput selection;
    private T result;

    ChoiceDialog(String title, String fieldName, List<T> choices, T preselected)
    {
        super(POSITION_CENTER);
        setTitle(title);
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
        selection = new SelectInput(choices, preselected);
        selection.setName(fieldName);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addInput(selection);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Uebernehmen", new Action()
        {
            @SuppressWarnings("unchecked")
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                result = (T) selection.getValue();
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    @Override
    protected T getData()
    {
        return result;
    }
}
