package de.open4me.hibiscus.psd2.model;

public class AuthMethod
{
    public String name;
    public String title;
    public String psuType;
    public String approach;
    public boolean hidden;

    public String getName()
    {
        return name;
    }

    public String getTitle()
    {
        return title == null || title.isBlank() ? name : title;
    }

    public String getApproach()
    {
        return approach;
    }
}
