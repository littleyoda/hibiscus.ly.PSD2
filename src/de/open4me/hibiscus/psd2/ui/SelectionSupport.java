package de.open4me.hibiscus.psd2.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    static Aspsp chooseAspsp(List<Aspsp> aspsps) throws Exception
    {
        return new AspspSelectionDialog(aspsps).open();
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

    static AccountSelectionDialog.Result chooseAccount(List<Konto> accounts, String iban) throws Exception
    {
        return new AccountSelectionDialog(accounts, iban).open();
    }

    static AuthMethodSelection chooseAuthMethod(Aspsp aspsp, String psuType) throws Exception
    {
        List<AuthMethod> methods = selectableAuthMethods(aspsp, psuType);
        if (methods.isEmpty())
            return new AuthMethodSelection(null, false);
        if (methods.size() == 1)
            return new AuthMethodSelection(methods.get(0).name, false);
        ListDialog dialog = new ListDialog(methods, AbstractDialog.POSITION_CENTER);
        dialog.setTitle("Authentifizierungsmethode auswaehlen");
        dialog.addColumn("Methode", "title");
        dialog.addColumn("Verfahren", "approach");
        AuthMethod selected = (AuthMethod) dialog.open();
        return selected == null
                ? new AuthMethodSelection(null, true)
                : new AuthMethodSelection(selected.name, false);
    }

    static List<AuthMethod> selectableAuthMethods(Aspsp aspsp, String psuType)
    {
        return aspsp.authMethods.stream()
                .filter(method -> psuType.equals(method.psuType))
                .filter(method -> !method.hidden)
                .toList();
    }

    record AuthMethodSelection(String name, boolean cancelled)
    {
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

}
