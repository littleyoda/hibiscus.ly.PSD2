package de.open4me.hibiscus.psd2.ui;

import java.util.List;

import de.open4me.hibiscus.psd2.model.Aspsp;
import de.open4me.hibiscus.psd2.model.AuthMethod;

public final class AspspSelectionDialogTests
{
    private AspspSelectionDialogTests()
    {
    }

    public static void run()
    {
        testCountriesAndPreferredCountry();
        testInstitutionFilteringAndSorting();
        testHiddenAuthenticationMethods();
    }

    private static void testCountriesAndPreferredCountry()
    {
        List<AspspSelectionDialog.CountryOption> countries = AspspSelectionDialog.countryOptions(List.of(
                aspsp("Bank NL", "NL"),
                aspsp("Bank DE", "de"),
                aspsp("Bank FI", "FI"),
                aspsp("Duplicate DE", "DE"),
                aspsp("Without country", " ")));
        require(countries.stream().map(AspspSelectionDialog.CountryOption::code).toList()
                .equals(List.of("DE", "FI", "NL")), "Countries must be normalized, unique and sorted");
        require("DE".equals(AspspSelectionDialog.preferredCountry(countries).code()),
                "Germany must be preferred");

        List<AspspSelectionDialog.CountryOption> withoutGermany = AspspSelectionDialog.countryOptions(List.of(
                aspsp("Bank NL", "NL"), aspsp("Bank FI", "FI")));
        require(AspspSelectionDialog.preferredCountry(withoutGermany) == withoutGermany.get(0),
                "First sorted country must be the fallback");
        require(AspspSelectionDialog.preferredCountry(List.of()) == null, "Empty country list");
    }

    private static void testInstitutionFilteringAndSorting()
    {
        Aspsp zeta = aspsp("Zeta Bank", "DE");
        Aspsp alpha = aspsp("alpha Bank", "de");
        Aspsp foreign = aspsp("Finnish Bank", "FI");
        List<Aspsp> selected = AspspSelectionDialog.institutionsFor(List.of(zeta, foreign, alpha), "DE");
        require(selected.equals(List.of(alpha, zeta)), "Institutions must be filtered and sorted by name");
    }

    private static void testHiddenAuthenticationMethods()
    {
        Aspsp aspsp = aspsp("Testbank", "DE");
        AuthMethod visible = authMethod("visible", "personal", false);
        AuthMethod hidden = authMethod("hidden", "personal", true);
        AuthMethod business = authMethod("business", "business", false);
        aspsp.authMethods.addAll(List.of(hidden, business, visible));
        require(SelectionSupport.selectableAuthMethods(aspsp, "personal").equals(List.of(visible)),
                "Hidden and unrelated authentication methods must not be selectable");
    }

    private static AuthMethod authMethod(String name, String psuType, boolean hidden)
    {
        AuthMethod method = new AuthMethod();
        method.name = name;
        method.psuType = psuType;
        method.hidden = hidden;
        return method;
    }

    private static Aspsp aspsp(String name, String country)
    {
        Aspsp aspsp = new Aspsp();
        aspsp.name = name;
        aspsp.country = country;
        return aspsp;
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
