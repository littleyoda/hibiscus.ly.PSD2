package de.open4me.hibiscus.psd2.sync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.open4me.hibiscus.psd2.Psd2Config;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

final class TransactionDebugExporter
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TransactionDebugExporter()
    {
    }

    static void saveIfEnabled(Konto konto, LocalDate dateFrom, List<JsonNode> pages)
    {
        if (!Psd2Config.isTransactionDebugExportEnabled())
            return;

        try
        {
            String filename = chooseFilename();
            if (filename == null)
                return;

            ObjectNode document = MAPPER.createObjectNode();
            document.put("retrieved_at", Instant.now().toString());
            document.put("date_from", dateFrom.toString());
            document.put("hibiscus_account", konto.getBezeichnung());
            ArrayNode responses = document.putArray("responses");
            pages.forEach(page -> responses.add(page.deepCopy()));
            Files.writeString(Path.of(filename),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document),
                    StandardCharsets.UTF_8);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                    "Transaktions-JSON gespeichert: " + filename, StatusBarMessage.TYPE_INFO));
        }
        catch (Exception e)
        {
            Logger.error("Transaktions-JSON konnte nicht gespeichert werden", e);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                    "Transaktions-JSON konnte nicht gespeichert werden: " + e.getMessage(),
                    StatusBarMessage.TYPE_ERROR));
        }
    }

    private static String chooseFilename()
    {
        String[] filename = { null };
        GUI.getDisplay().syncExec(() -> {
            FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
            dialog.setText("Enable-Banking-Transaktionen als JSON speichern");
            dialog.setFilterExtensions(new String[] { "*.json", "*" });
            dialog.setFilterNames(new String[] { "JSON-Dateien", "Alle Dateien" });
            dialog.setFileName("enablebanking-transactions-"
                    + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".json");
            dialog.setOverwrite(true);
            filename[0] = dialog.open();
        });
        return filename[0];
    }
}
