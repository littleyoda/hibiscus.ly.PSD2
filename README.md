# hibiscus.ly.PSD2

`hibiscus.ly.PSD2` erweitert [Hibiscus](https://www.willuhn.de/products/hibiscus/) um den Zugriff auf Bankkonten über die [Enable Banking API](https://enablebanking.com/docs/api/reference/). Das Plugin ruft Kontosalden sowie gebuchte und vorgemerkte Umsätze ab und übernimmt sie in Hibiscus.

Die Freigabe eines Bankzugangs erfolgt im Systembrowser direkt über das jeweilige Kreditinstitut. Vorhandene Hibiscus-Konten können anhand ihrer IBAN zugeordnet werden; alternativ legt das Plugin für die freigegebenen Bankkonten neue Konten an.

> **Hinweis:** Das Plugin befindet sich in einer Beta-Phase. Vor einem produktiven Einsatz empfiehlt sich eine aktuelle Sicherung der Hibiscus-Datenbank.

## Funktionsumfang

- Abruf von Kontosalden und Umsätzen über Enable Banking
- Einbindung in die normale Hibiscus-Kontosynchronisierung
- Übernahme gebuchter und vorgemerkter Umsätze
- Zuordnung zu vorhandenen Hibiscus-Konten oder automatische Kontoanlage
- Verwaltung und Erneuerung der Bankfreigaben

Das Plugin dient ausschließlich dem Kontoinformationsabruf. Überweisungen werden nicht unterstützt.

## Unterstützte Banken und Konten

Welche Banken und Kontotypen verfügbar sind, hängt von Enable Banking und dem jeweiligen Kreditinstitut ab. Eine aktuelle Übersicht bietet Enable Banking unter [Open Banking APIs](https://enablebanking.com/open-banking-apis).

Der Dateiname des Schlüssels muss der Application-ID entsprechen, zum Beispiel `18977c78-a7e5-4462-9ffa-9bfbcef29127.pem`.

## Installation über den Jameica-Plugin-Manager

1. Jameica starten.
2. **Datei → Plugin online suchen** öffnen.
3. **Verfügbare Plugins** auswählen.
4. In der Quelle **www.open4me.de** das Plugin **hibiscus.ly.PSD2** auswählen und installieren.
5. Jameica nach der Installation neu starten.

Nach dem Neustart steht in Jameica das Menü **PSD2** zur Verfügung.

## Enable Banking vorbereiten

1. Im [Enable-Banking-Control-Panel](https://enablebanking.com/cp/) eine Anwendung anlegen.
2. Als Redirect-URL `https://127.0.0.1:18443/callback` registrieren.
3. Über den Button **Link Accounts** alle Konten hinzufügen, auf die später mit Hibiscus zugegriffen werden soll. Enable Banking beschreibt den Ablauf in der Dokumentation zu [Linked Accounts](https://enablebanking.com/docs/api/linked-accounts).
4. Den privaten Anwendungsschlüssel herunterladen. (Die Datei darf nicht umbenannt werden und muss der Application-ID entsprechen).

Die Redirect-URL muss im Control Panel exakt mit der im Plugin konfigurierten URL übereinstimmen. Der lokale Callback-Server lauscht ausschließlich auf der Loopback-Schnittstelle. Wegen des lokal erzeugten, selbstsignierten HTTPS-Zertifikats kann der Browser beim ersten Aufruf eine Zertifikatswarnung anzeigen.

## Bankverbindung einrichten

1. Unter **PSD2 → PEM-Datei importieren …** den privaten Anwendungsschlüssel importieren. Der vorgeschaltete Dialog verlinkt die Konto- und Application-Anlage und zeigt die erforderlichen Einstellungen.
2. Unter **PSD2 → Neue Bankverbindung …** festlegen, ob vorhandene Hibiscus-Konten verwendet oder neue Konten angelegt werden sollen.
3. Land, Kreditinstitut und – falls angeboten – Kontotyp und Authentifizierungsmethode auswählen.
4. Die Autorisierung im Systembrowser abschließen.
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
