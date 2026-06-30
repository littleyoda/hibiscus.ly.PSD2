package de.open4me.hibiscus.psd2.ui;

import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.LinkInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class PemImportIntroductionDialog extends AbstractDialog<Boolean>
{
    private static final String ENABLE_BANKING_URL = "https://enablebanking.com/";
    private static final String APPLICATIONS_URL = "https://enablebanking.com/cp/applications";
    private static final String IMAGE_RESOURCE = "/img/AppSetup.png";
    private static final int WINDOW_WIDTH = 760;
    private static final int WINDOW_HEIGHT = 700;
    private static final int IMAGE_WIDTH = 560;

    private boolean proceed;

    PemImportIntroductionDialog()
    {
        super(POSITION_CENTER);
        setTitle("Enable-Banking-Anwendung und PEM-Datei einrichten");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        ScrolledComposite scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
        scrolled.setLayoutData(new GridData(GridData.FILL_BOTH));
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        Composite content = new Composite(scrolled, SWT.NONE);
        content.setLayout(new GridLayout(1, false));
        Container container = new SimpleContainer(content);
        container.addText(
                "Für den Zugriff benötigt das Plugin eine Enable-Banking-Anwendung und deren private PEM-Datei.",
                true);

        LinkInput accountLink = link("1. Account anlegen", ENABLE_BANKING_URL);
        container.addInput(accountLink);

        LinkInput applicationsLink = link("2. Neue Application anlegen", APPLICATIONS_URL);
        container.addInput(applicationsLink);
        container.addText(
                "Wählen Sie dabei Production und die Erzeugung des privaten Schlüssels im Browser. ",
                true);
        container.addText(
                    "Callback-URL: https://127.0.0.1:18443/callback",
                true);
        addSetupImage(container.getComposite());

        container.addText(
                "3. Fügen Sie über den Button \"Link Accounts\" alle Konten hinzu, auf die Sie später mit "
                        + "Hibiscus zugreifen möchten.",
                true);

        container.addText(
                "4. Laden Sie die erzeugte PEM-Datei herunter. Über die Schaltfläche unten wählen Sie die Datei "
                        + "aus und importieren sie in das verschlüsselte Jameica-Wallet.",
                true);

        scrolled.setContent(content);
        scrolled.setMinSize(content.computeSize(WINDOW_WIDTH - 50, SWT.DEFAULT));

        Container footer = new SimpleContainer(parent);
        ButtonArea buttons = new ButtonArea();
        buttons.addButton("PEM-Datei auswählen", new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                proceed = true;
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        footer.addButtonArea(buttons);
    }

    private static LinkInput link(String name, String url)
    {
        LinkInput input = new LinkInput("<a href=\"" + url + "\">" + url + "</a>");
        input.setName(name);
        input.addListener(event -> Program.launch(url));
        return input;
    }

    private static void addSetupImage(Composite parent) throws Exception
    {
        ImageData source;
        try (InputStream input = PemImportIntroductionDialog.class.getResourceAsStream(IMAGE_RESOURCE))
        {
            if (input == null)
                throw new ApplicationException("Das Bild zur Enable-Banking-Einrichtung wurde nicht gefunden.");
            source = new ImageData(input);
        }
        int height = Math.max(1, source.height * IMAGE_WIDTH / source.width);
        Image image = new Image(parent.getDisplay(), source.scaledTo(IMAGE_WIDTH, height));
        Label label = new Label(parent, SWT.BORDER);
        GridData layout = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        label.setLayoutData(layout);
        label.setImage(image);
        label.addDisposeListener(event -> image.dispose());
    }

    @Override
    protected Boolean getData()
    {
        return proceed;
    }
}
