package de.open4me.hibiscus.psd2.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.open4me.hibiscus.psd2.model.Aspsp;
import de.open4me.hibiscus.psd2.model.AuthMethod;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.dialogs.ListDialog;
import de.willuhn.jameica.hbci.rmi.Konto;

final class SelectionSupport
{
    private SelectionSupport()
    {
    }

    static String chooseCountry(List<Aspsp> aspsps) throws Exception
    {
        List<CountryOption> countries = aspsps.stream()
                .map(aspsp -> aspsp.country)
                .filter(country -> country != null && !country.isBlank())
                .distinct()
                .map(CountryOption::new)
                .sorted(Comparator.comparing(CountryOption::label))
                .toList();
        CountryOption germany = countries.stream()
                .filter(country -> "DE".equals(country.code))
                .findFirst()
                .orElse(countries.isEmpty() ? null : countries.get(0));
        if (germany == null)
            return null;
        CountryOption selected = new ChoiceDialog<>(
                "Land auswaehlen", "Land", countries, germany).open();
        return selected == null ? null : selected.code;
    }

    static Aspsp chooseAspsp(List<Aspsp> aspsps) throws Exception
    {
        ListDialog dialog = new ListDialog(aspsps, AbstractDialog.POSITION_CENTER);
        dialog.setTitle("ASPSP auswaehlen");
        dialog.addColumn("Land", "country");
        dialog.addColumn("Institut", "name");
        return (Aspsp) dialog.open();
    }

    static String choosePsuType(Aspsp aspsp) throws Exception
    {
        Set<String> values = new LinkedHashSet<>();
        aspsp.psuTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(values::add);
        aspsp.authMethods.stream()
                .map(method -> method.psuType)
                .filter(value -> value != null && !value.isBlank())
                .forEach(values::add);
        if (values.isEmpty())
            throw new IllegalStateException("Enable Banking hat fuer dieses Institut keine Kontotypen geliefert.");
        if (values.size() == 1)
            return values.iterator().next();

        List<PsuTypeOption> options = new ArrayList<>();
        values.forEach(value -> options.add(new PsuTypeOption(value)));
        PsuTypeOption preferred = options.stream()
                .filter(option -> "personal".equals(option.value))
                .findFirst()
                .orElse(options.get(0));
        PsuTypeOption selected = new ChoiceDialog<>(
                "Kontotyp auswaehlen", "Kontotyp", options, preferred).open();
        return selected == null ? null : selected.value;
    }

    static Konto chooseAccount(List<Konto> accounts, String iban) throws Exception
    {
        ListDialog dialog = new ListDialog(accounts, AbstractDialog.POSITION_CENTER);
        dialog.setTitle("Hibiscus-Konto fuer " + (iban == null || iban.isBlank() ? "PSD2-Konto" : iban) + " auswaehlen");
        dialog.addColumn("Bezeichnung", "bezeichnung");
        dialog.addColumn("Konto", "longName");
        dialog.addColumn("IBAN", "iban");
        return (Konto) dialog.open();
    }

    static String chooseAuthMethod(Aspsp aspsp, String psuType) throws Exception
    {
        List<AuthMethod> methods = aspsp.authMethods.stream()
                .filter(method -> psuType.equals(method.psuType))
                .filter(method -> !method.hidden || method.name != null)
                .toList();
        if (methods.isEmpty())
            return null;
        if (methods.size() == 1)
            return methods.get(0).name;
        ListDialog dialog = new ListDialog(methods, AbstractDialog.POSITION_CENTER);
        dialog.setTitle("Authentifizierungsmethode auswaehlen");
        dialog.addColumn("Methode", "title");
        dialog.addColumn("Verfahren", "approach");
        AuthMethod selected = (AuthMethod) dialog.open();
        return selected == null ? null : selected.name;
    }

    private static final class PsuTypeOption
    {
        private final String value;

        private PsuTypeOption(String value)
        {
            this.value = value;
        }

        public String getLabel()
        {
            return "business".equals(value) ? "Geschaeftlich" : "Privat";
        }

        @Override
        public String toString()
        {
            return getLabel();
        }
    }

    private static final class CountryOption
    {
        private final String code;

        private CountryOption(String code)
        {
            this.code = code;
        }

        private String label()
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
