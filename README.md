# hibiscus.ly.PSD2

`hibiscus.ly.PSD2` erweitert [Hibiscus](https://www.willuhn.de/products/hibiscus/) um den Zugriff auf Bankkonten über die [Enable Banking API](https://enablebanking.com/docs/api/reference/). Das Plugin ruft Kontosalden sowie gebuchte und vorgemerkte Umsätze ab und übernimmt sie in Hibiscus.

Die Freigabe eines Bankzugangs erfolgt im Systembrowser direkt über das jeweilige Kreditinstitut. Vorhandene Hibiscus-Konten können anhand ihrer IBAN zugeordnet werden; alternativ legt das Plugin für die freigegebenen Bankkonten neue Konten an.


Jede Bank liefert unterschiedliche Daten. 
Details könnten über https://enablebanking.com/cp/data-insights abgerufen werden.

Die meisten Banken liefern brauchbare Informationen. Einige Negativbeispiele sind jedoch:

| Bank  | Anmerkungen |
| ------------- | ------------- |
| Trade Republic | Es wird nur der Betrag des Umsatzes geliefert. Informationen wie Verwendungszweck, Empfänger, Sender, Saldo fehlen. |
| Paypal | Betrag und Empfänger bzw. Sender werden geliefert; kein Verwendungszweck; kein Saldo; Zahlungsweg (Guthaben, Lastschrift, Kreditkarte) nicht ersichtlich |



## Funktionsumfang

- Abruf von Kontosalden und Umsätzen über Enable Banking
- Einbindung in die normale Hibiscus-Kontosynchronisierung
- Übernahme gebuchter und vorgemerkter Umsätze
- Zuordnung zu vorhandenen Hibiscus-Konten oder automatische Kontoanlage
- Verwaltung und Erneuerung der Bankfreigaben

Das Plugin dient ausschließlich dem Kontoinformationsabruf. Überweisungen werden nicht unterstützt.

## Unterstützte Banken und Konten

Welche Banken und Kontotypen verfügbar sind, hängt von Enable Banking und dem jeweiligen Kreditinstitut ab. Eine aktuelle Übersicht bietet Enable Banking unter [Open Banking APIs](https://enablebanking.com/open-banking-apis).


## Installation über den Jameica-Plugin-Manager

1. Jameica starten.
2. **Datei → Plugin online suchen** öffnen.
3. **Verfügbare Plugins** auswählen.
4. In der Quelle **www.open4me.de** das Plugin **hibiscus.ly.PSD2** auswählen und installieren.
5. Jameica nach der Installation neu starten.

Nach dem Neustart steht in Jameica das Menü **PSD2** zur Verfügung.

## Enable Banking vorbereiten

Vor der Einrichtung im Plugin muss eine Anwendung bei Enable Banking registriert werden:

1. Unter [enablebanking.com](https://enablebanking.com/) einen Account erstellen.
2. Im Enable-Banking-Control-Panel die Seite [Applications](https://enablebanking.com/cp/applications) öffnen.
3. Eine neue Applikation mit folgenden Einstellungen registrieren:
   - **Environment:** Production
   - **Key generation:** Generate in the browser
   - **Application name:** frei wählbar
   - **Allowed redirect URLs:** `https://127.0.0.1:18443/callback`
   - **Application description:** `Used by Hibiscus Plugin`
   - **Email:** eigene E-Mail-Adresse
   - **Privacy URL:** `https://example.com/`
   - **Terms URL:** `https://example.com/`
4. Die anschließend heruntergeladene PEM-Datei sicher aufbewahren. Sie wird beim Einrichten des Plugins benötigt.
5. Danach müssen über Link Accounts alle Konten hinzugefügt werden, auf die später zugegriffen werden soll.

## Bankverbindung einrichten

1. Unter **PSD2 → PEM-Datei importieren …** den privaten Anwendungsschlüssel importieren. Der vorgeschaltete Dialog verlinkt die Konto- und Application-Anlage und zeigt die erforderlichen Einstellungen.
2. Unter **PSD2 → Neue Bankverbindung …** festlegen, ob vorhandene Hibiscus-Konten verwendet oder neue Konten angelegt werden sollen.
3. Land, Kreditinstitut und – falls angeboten – Kontotyp und Authentifizierungsmethode auswählen.
4. Der Systembrowser wird geöffnet. Dort ist eine Authentifizierung erforderlich. Beim Zugriff auf 127.0.0.1 erscheint möglicherweise eine Warnung des Browsers. Die Webseite muss trotz dieser Warnung aufgerufen werden.
5. Bei vorhandenen Konten die vorgeschlagene Zuordnung prüfen. Eindeutige IBANs ordnet das Plugin automatisch zu.

Das Plugin setzt den Zugangsweg der verbundenen Konten auf **PSD2 via Enable Banking**. Danach werden Salden und Umsätze über die normale Hibiscus-Synchronisierung abgerufen. Ist eine Bankfreigabe abgelaufen, startet das Plugin bei der nächsten Synchronisierung erneut die Browser-Autorisierung.

Gespeicherte Freigaben lassen sich unter **PSD2 → Verbindungen verwalten …** entfernen. Dabei bleiben die Hibiscus-Konten und bereits importierte Umsätze erhalten.

## Rechtlicher Hintergrund des Kontoabrufs über PSD2

Die zweite EU-Zahlungsdiensterichtlinie [PSD2 (Richtlinie (EU) 2015/2366)](https://eur-lex.europa.eu/eli/dir/2015/2366/oj?locale=de) schafft den rechtlichen Rahmen für sogenannte Kontoinformationsdienste. Damit dürfen entsprechend registrierte oder zugelassene Dienstleister nach Zustimmung des Kontoinhabers Informationen von online zugänglichen Zahlungskonten abrufen. In Deutschland ist dies insbesondere in [§ 51 Zahlungsdiensteaufsichtsgesetz (ZAG)](https://www.gesetze-im-internet.de/zag_2018/__51.html) umgesetzt.

Für den Abruf gelten unter anderem folgende Grundsätze:

- Der Kontoinhaber muss dem Zugriff ausdrücklich zustimmen.
- Der Zugriff ist auf die vom Kontoinhaber ausgewählten Zahlungskonten und die damit verbundenen Zahlungsvorgänge beschränkt.
- Kontodaten dürfen nur für den ausdrücklich angeforderten Kontoinformationsdienst verwendet werden.
- Der Kontoinformationsdienstleister muss sich gegenüber der kontoführenden Bank identifizieren und über sichere Schnittstellen kommunizieren.
- Die Bank darf den Zugriff nicht ohne sachlichen Grund benachteiligen, kann ihn aber insbesondere bei einem begründeten Verdacht auf unbefugten oder betrügerischen Zugriff verweigern.

`hibiscus.ly.PSD2` ist selbst kein Kontoinformationsdienstleister. Das Plugin stellt die lokale technische Verbindung zwischen Hibiscus und Enable Banking her. [Enable Banking Oy](https://enablebanking.com/data-sharing-consents/) ist nach eigenen Angaben ein bei der finnischen Finanzaufsicht registrierter Kontoinformationsdienstleister. Die Bankanmeldung und Freigabe erfolgen im Systembrowser; das Plugin speichert keine Onlinebanking-Zugangsdaten wie Benutzerkennung, PIN oder TAN.

Die Freigabe wird durch eine starke Kundenauthentifizierung bei der Bank bestätigt. Für den reinen Abruf von Kontostand und bestimmten Umsatzinformationen sieht die [Delegierte Verordnung (EU) 2022/2360](https://eur-lex.europa.eu/legal-content/de/TXT/?uri=CELEX%3A32022R2360) eine erneute starke Kundenauthentifizierung spätestens nach mehr als 180 Tagen vor. Eine erneute Freigabe kann dennoch früher erforderlich werden, etwa wenn die Bank oder Enable Banking die Sitzung beendet, sich der Umfang der freigegebenen Konten ändert oder Sicherheitsgründe vorliegen.

Das Plugin ruft ausschließlich Kontoinformationen ab. Es löst keine Überweisungen oder sonstigen Zahlungen aus. Eine gespeicherte Verbindung kann unter **PSD2 → Verbindungen verwalten …** entfernt werden. Zusätzlich kann die Freigabe gegebenenfalls im Onlinebanking der jeweiligen Bank verwaltet oder widerrufen werden.


## Datenschutz und Sicherheit

Für den Kontozugriff werden Daten zwischen Hibiscus, Enable Banking und dem gewählten Kreditinstitut verarbeitet. Vor der Nutzung sollten daher die Dokumente von Enable Banking gelesen werden:

- [Terms of Service](https://enablebanking.com/terms/)
- [Privacy Policy](https://enablebanking.com/privacy/)

Der private PEM-Schlüssel und die Enable-Banking-Sitzungen werden verschlüsselt im Jameica-Wallet gespeichert. Die zum Import ausgewählte PEM-Datei wird weder verändert noch an einen anderen Ort kopiert.

## Lizenz

Dieses Projekt wird unter der [GNU General Public License v3.0](LICENSE) veröffentlicht.
