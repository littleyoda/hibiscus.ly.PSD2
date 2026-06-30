package de.open4me.hibiscus.psd2.model;

import java.util.ArrayList;
import java.util.List;

public class Aspsp
{
    public String name;
    public String country;
    public long maximumConsentValidity;
    public List<String> psuTypes = new ArrayList<>();
    public List<AuthMethod> authMethods = new ArrayList<>();

    public String getName()
    {
        return name;
    }

    public String getCountry()
    {
        return country;
    }

    public String getDisplayName()
    {
        return country + " - " + name;
    }

    @Override
    public String toString()
    {
        return getDisplayName();
    }
}
