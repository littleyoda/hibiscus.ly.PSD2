package de.open4me.hibiscus.psd2.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import de.open4me.hibiscus.psd2.model.Aspsp;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.parts.Column;
import de.willuhn.jameica.gui.parts.TablePart;
import de.willuhn.jameica.gui.parts.table.FeatureSummary;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class AspspSelectionDialog extends AbstractDialog<Aspsp>
{
    private static final int WINDOW_WIDTH = 750;
    private static final int WINDOW_HEIGHT = 440;

    private final List<Aspsp> aspsps;
    private final SelectInput country;
    private Aspsp result;
    private TablePart institutions;

    AspspSelectionDialog(List<Aspsp> aspsps) throws ApplicationException
    {
        super(POSITION_CENTER);
        this.aspsps = aspsps == null ? List.of() : List.copyOf(aspsps);
        List<CountryOption> countries = countryOptions(this.aspsps);
        CountryOption preferred = preferredCountry(countries);
        if (preferred == null)
            throw new ApplicationException("Enable Banking hat keine Institute mit gueltigem Land geliefert.");
        country = new SelectInput(countries, preferred);
        country.setName("Land");
        country.addListener(event -> refreshInstitutions());
        setTitle("Land und Institut auswaehlen");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true);
        container.addInput(country);
        container.addPart(getInstitutions());

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                Aspsp selected = (Aspsp) getInstitutions().getSelection();
                if (selected == null)
                    throw new ApplicationException("Bitte waehlen Sie zuerst ein Institut aus.");
                result = selected;
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private TablePart getInstitutions()
    {
        if (institutions != null)
            return institutions;
        CountryOption selectedCountry = (CountryOption) country.getValue();
        institutions = new TablePart(institutionsFor(aspsps, selectedCountry.code()), new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                result = (Aspsp) context;
                close();
            }
        });
        institutions.addColumn(new Column("name", "Institut"));
        institutions.setMulti(false);
        institutions.removeFeature(FeatureSummary.class);
        return institutions;
    }

    private void refreshInstitutions()
    {
        try
        {
            CountryOption selectedCountry = (CountryOption) country.getValue();
            TablePart table = getInstitutions();
            table.removeAll();
            for (Aspsp aspsp : institutionsFor(aspsps, selectedCountry.code()))
                table.addItem(aspsp);
        }
        catch (Exception e)
        {
            UiSupport.error("Institutsliste konnte nicht aktualisiert werden", e);
        }
    }

    static List<CountryOption> countryOptions(List<Aspsp> aspsps)
    {
        return aspsps.stream()
                .map(aspsp -> normalizeCountry(aspsp.country))
                .filter(country -> country != null)
                .distinct()
                .map(CountryOption::new)
                .sorted(Comparator.comparing(CountryOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static CountryOption preferredCountry(List<CountryOption> countries)
    {
        return countries.stream()
                .filter(country -> "DE".equals(country.code()))
                .findFirst()
                .orElse(countries.isEmpty() ? null : countries.get(0));
    }

    static List<Aspsp> institutionsFor(List<Aspsp> aspsps, String country)
    {
        return aspsps.stream()
                .filter(aspsp -> country.equals(normalizeCountry(aspsp.country)))
                .sorted(Comparator.comparing(aspsp -> aspsp.name == null ? "" : aspsp.name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String normalizeCountry(String country)
    {
        if (country == null || country.isBlank())
            return null;
        return country.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    protected Aspsp getData()
    {
        return result;
    }

    record CountryOption(String code)
    {
        String label()
        {
            String name = new Locale("", code).getDisplayCountry(Locale.GERMAN);
            return name == null || name.isBlank() ? code : name + " (" + code + ")";
        }

        @Override
        public String toString()
        {
            return label();
        }
    }
}
