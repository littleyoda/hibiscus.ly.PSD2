package de.open4me.hibiscus.psd2;

import java.security.PrivateKey;
import java.util.Set;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.MenuItem;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

public final class Psd2MenuState
{
    private static final Set<String> PROTECTED_ITEMS = Set.of(
            "hibiscus.ly.psd2.connect",
            "hibiscus.ly.psd2.manage");

    private Psd2MenuState()
    {
    }

    public static void refresh()
    {
        boolean enabled = hasValidCredentials();
        try
        {
            Plugin plugin = Application.getPluginLoader().getPlugin(Plugin.class);
            update(plugin.getManifest().getMenu(), enabled);
        }
        catch (Exception e)
        {
            Logger.error("PSD2-Menuezustand konnte nicht aktualisiert werden", e);
        }
    }

    private static boolean hasValidCredentials()
    {
        if (Psd2Config.getApplicationId().isBlank())
            return false;
        try
        {
            PrivateKey key = Psd2Runtime.secrets().getPrivateKey();
            return key != null && "RSA".equalsIgnoreCase(key.getAlgorithm());
        }
        catch (Exception e)
        {
            Logger.warn("Kein gueltiger Enable-Banking-PEM-Schluessel gespeichert");
            return false;
        }
    }

    private static void update(Item item, boolean enabled) throws Exception
    {
        if (item == null)
            return;
        if (PROTECTED_ITEMS.contains(item.getID()))
            item.setEnabled(enabled, false);

        GenericIterator<?> children = item.getChildren();
        while (children != null && children.hasNext())
            update((Item) children.next(), enabled);
    }
}
