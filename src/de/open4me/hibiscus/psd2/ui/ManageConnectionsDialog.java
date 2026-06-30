package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import de.open4me.hibiscus.psd2.model.ConnectionState;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.parts.Column;
import de.willuhn.jameica.gui.parts.TablePart;
import de.willuhn.jameica.gui.parts.table.FeatureSummary;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class ManageConnectionsDialog extends AbstractDialog<ConnectionState>
{
    private static final int WINDOW_WIDTH = 750;
    private static final int WINDOW_HEIGHT = 420;

    private final List<ConnectionState> connections;
    private ConnectionState selected;
    private TablePart table;

    ManageConnectionsDialog(List<ConnectionState> connections)
    {
        super(POSITION_CENTER);
        this.connections = connections;
        setTitle("PSD2-Verbindungen verwalten");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true);
        container.addText(
                "Dieser Dialog zeigt die gespeicherten Enable-Banking-Verbindungen. Beim Loeschen werden "
                        + "die gespeicherte Session, die Bankfreigabe und die Zuordnung zu den Hibiscus-Konten entfernt. "
                        + "Die Konten und bereits importierten Umsaetze bleiben erhalten. Fuer einen weiteren "
                        + "Abruf muss anschliessend eine neue Bankverbindung eingerichtet werden.",
                true);
        container.addPart(getTable());

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Verbindung loeschen...", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                selected = (ConnectionState) getTable().getSelection();
                if (selected == null)
                    throw new ApplicationException("Bitte waehlen Sie zuerst eine Verbindung aus.");
                close();
            }
        }, null, false, "user-trash-full.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private TablePart getTable()
    {
        if (table != null)
            return table;
        table = new TablePart(connections, new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                selected = (ConnectionState) context;
                close();
            }
        });
        table.addColumn(new Column("aspspCountry", "Land"));
        table.addColumn(new Column("aspspName", "Institut"));
        table.addColumn(new Column("validUntil", "Gueltig bis"));
        table.setMulti(false);
        table.removeFeature(FeatureSummary.class);
        return table;
    }

    @Override
    protected ConnectionState getData()
    {
        return selected;
    }
}
