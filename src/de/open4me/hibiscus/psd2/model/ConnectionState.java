package de.open4me.hibiscus.psd2.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConnectionState
{
    public String id;
    public String aspspName;
    public String aspspCountry;
    public String psuType;
    public String authMethod;
    public String sessionId;
    public String validUntil;
    public Map<String, String> accountUids = new LinkedHashMap<>();

    public ConnectionState()
    {
    }

    public String getAspspName()
    {
        return aspspName;
    }

    public String getAspspCountry()
    {
        return aspspCountry;
    }

    public String getValidUntil()
    {
        return validUntil;
    }
}
