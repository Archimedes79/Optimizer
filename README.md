<p align="center">
  <img src="docs/icon-512.png" width="128" alt="Portfolio Optimizer Classic">
</p>

<h1 align="center">Portfolio Optimizer Classic</h1>

<p align="center">
  Eine Android-App, die ein privates Wertpapierdepot nach den klassischen
  Verfahren der Portfoliotheorie umschichtet &ndash; lokal, ohne Konto,
  ohne Tracking.
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Neuestes Release" src="https://img.shields.io/github/v/release/Archimedes79/Portfolio_Optimizer_Classic?label=Download%20APK"></a>
  <a href="../../actions/workflows/build.yml"><img alt="Build" src="https://github.com/Archimedes79/Portfolio_Optimizer_Classic/actions/workflows/build.yml/badge.svg"></a>
</p>

---

## Was die App macht

Du trägst dein Depot ein &ndash; Ticker oder ISIN, Stückzahl oder Eurobetrag &ndash;
und die App lädt dazu die vollständige Kurshistorie von Yahoo Finance, rechnet
alles in Euro um und legt die Reihen auf ein gemeinsames Zeitfenster.

Auf dem Optimierungs-Screen mischst du mit drei Reglern drei klassische
Zielfunktionen und siehst sofort, wie sich dein Portfolio verändern würde:

| Regler | Verfahren | Ziel |
| --- | --- | --- |
| **Minimum Variance** | Global Minimum Variance auf einer regularisierten Kovarianzmatrix; negative Gewichte werden auf 0 geklemmt und der Rest neu normiert (long-only-Näherung) | geringste Schwankung |
| **Max Sharpe Ratio** | Tangentialportfolio `Σ⁻¹μ` (risikoloser Zins = 0), long-only geklemmt &ndash; und gegen jede Einzelposition sowie die Gleichgewichtung geprüft; es gewinnt die tatsächlich beste Sharpe Ratio | bestes Rendite-Risiko-Verhältnis |
| **Min Drawdown** | ableitungsfreie BOBYQA-Optimierung auf den maximalen Drawdown | kleinster zwischenzeitlicher Verlust |

Schlägt ein Verfahren numerisch fehl, fällt es auf Gleichgewichtung zurück.

Der nicht verteilte Rest bleibt dein Ist-Portfolio, sodass du stufenlos
zwischen „alles so lassen“ und „voll optimiert“ mischen kannst. Die Tabelle
zeigt dir für jede Position, wie viele Anteile du kaufen oder verkaufen
müsstest.

**Weitere Eigenschaften**

- Alle Verfahren sind **long-only** &ndash; keine Leerverkäufe.
- Positionen lassen sich als **fixiert** markieren; sie bleiben unangetastet,
  ihr Wert wird aus der Optimierung herausgerechnet.
- Der Gesamtwert des Depots soll bei jeder Umschichtung erhalten bleiben. Eine
  Kontrollrechnung mit 0,1 % Toleranz protokolliert Abweichungen lediglich, sie
  korrigiert nichts.
- Die Optimierung läuft immer auf dem **sichtbaren Zeitfenster** des Charts:
  zoomst du den Graphen, wird neu gerechnet. Die Reihen werden dabei auf
  höchstens 256 äquidistante Stützstellen abgetastet. Gezoomt wird durch
  horizontales Ziehen – Pinch-Zoom ist im Code ausdrücklich deaktiviert.
- Bis zu 24 Positionen.
- Alle Daten bleiben lokal auf dem Gerät (`portfolio.json`): kein Konto, kein
  Backend, keine Analytics. Das Manifest setzt allerdings
  `android:allowBackup="true"` mit den leeren Standard-Backup-Regeln, sodass
  Androids systemweites Auto-Backup die Datei mitsichern kann.

## Installation

1. Unter [**Releases**](../../releases/latest) die aktuelle `.apk` herunterladen.
2. Beim Öffnen fragt Android einmalig nach der Erlaubnis, Apps aus dieser
   Quelle zu installieren (Browser bzw. Dateimanager) &ndash; bestätigen.
3. Installieren. Voraussetzung: **Android 7.0 (API 24)** oder neuer.

> Solange noch kein Signaturschlüssel hinterlegt ist, wird die APK mit dem
> Android-Debug-Key signiert. Sie lässt sich normal installieren, kann aber
> keine anders signierte Installation aktualisieren. Siehe
> [docs/RELEASING.md](docs/RELEASING.md).

## Bedienung

Drei Bildschirme: das Portfolio mit dem Chart, die Werteverwaltung und der
Optimierer. Unten führen dich **Werte**, **Sync** und **Optimieren** dorthin.

### Positionen anlegen

Auf **Werte** trägst du dein Depot ein.

1. **Ticker oder ISIN** eingeben &ndash; `VOO`, `SAP.DE`, `IE00B4L5Y983`. Die App
   fragt Yahoo Finance und lädt die gesamte verfügbare Kurshistorie.
2. **Menge** eingeben. Standardmäßig ist das die Stückzahl. Legst du den Schalter
   **EUR** um, gibst du stattdessen einen Eurobetrag ein und die App rechnet ihn
   mit dem letzten Kurs in Anteile um &ndash; praktisch bei Fondssparplänen, wo du
   den Betrag kennst und nicht die krummen Anteile.
3. **Alias** ist optional. Ohne Alias zeigt die App den offiziellen Namen, der
   gerne mal `iShares Physical Metals PLC O` heißt; mit Alias steht überall dein
   eigener Name.
4. **Fixiert** nimmt die Position von der Optimierung aus (siehe unten).
5. **Hinzufügen**.

Findet die Suche mehrere Papiere &ndash; oder eines, das nicht genau dem entspricht,
was du getippt hast &ndash; fragt ein Dialog nach, welches gemeint ist, mit Name,
Kürzel und der Zahl der verfügbaren Kurspunkte. Übernommen ohne Rückfrage wird nur
ein Treffer, dessen Kürzel exakt deiner Eingabe entspricht. Das ist Absicht: Auf
`AAPL` antwortet Yahoo mit Apple *und* einer Reihe gehebelter Produkte darauf, und
davon soll keines unbemerkt in deinem Depot landen.

> **Achte auf die Zahl der Kurspunkte.** Alle Verfahren rechnen auf dem Zeitraum,
> den sich sämtliche Positionen teilen &ndash; und der ist nur so lang wie die
> *kürzeste* Reihe. Ein frisch aufgelegter ETF mit acht Monaten Historie verkürzt
> das Fenster für dein ganzes Depot auf acht Monate, egal wie weit die anderen
> zurückreichen. Kovarianzen und Drawdowns aus so wenigen Punkten sagen kaum etwas
> aus. Gibt es dasselbe Papier auch mit langer Historie &ndash; ein anderer
> Handelsplatz, eine ältere Anteilsklasse, der zugrunde liegende Index &ndash;
> nimm dieses. In der Liste ist die Position, die das Fenster begrenzt, mit
> *Limitierend* markiert.
>
> **Fixiert** ändert daran nichts: Das Fenster wird bewusst über *alle* Positionen
> gebildet, auch über die ausgeschlossenen &ndash; so zeigt der Chart genau den
> Zeitraum, auf dem auch gerechnet wird. Eine fixierte Position nimmt also nicht
> an der Optimierung teil, begrenzt das Fenster aber weiterhin. Länger wird es nur,
> wenn du die kurze Reihe durch eine längere ersetzt oder ganz entfernst.

Ist alles eingetragen, holt **Sync** für jede Position die aktuellen Kurse nach.

### Positionen ändern

- **Antippen** lädt die Position zurück in die Eingabefelder; der Knopf heißt dann
  **Aktualisieren**. Lässt du die Menge unangetastet, bleibt sie exakt erhalten.
- **Rotes ✕** löscht die Position.
- **Doppeltippen** auf dieselbe Zeile würfelt ihre Farbe neu &ndash; nützlich, wenn
  sich zwei Linien im Chart zu ähnlich sehen. Der schmale Streifen links an jeder
  Zeile zeigt die aktuelle Farbe, der Balken neben dem Eingabefeld die Farbe, die
  eine neue Position bekommen wird. Die wird aus dem Kürzel abgeleitet, ist also
  für dasselbe Papier immer dieselbe.

### Der Optimierer

Oben der Chart mit jeder Position und, in Schwarz, deinem Portfolio als Ganzes.
Darunter drei Regler, darunter die Tabelle mit **ΔAnteile** (wie viele Stücke du
kaufen oder verkaufen müsstest) und **ΔAllok.** (wie sich der Anteil am Depot
verschiebt).

Die drei Regler mischen stufenlos zwischen deinem heutigen Depot und drei
Zielportfolios:

| Regler | Sucht | Führt typischerweise zu |
| --- | --- | --- |
| **Minimale Varianz** | die ruhigste Mischung | Übergewicht für schwankungsarme Papiere |
| **Max. Sharpe Ratio** | den besten Ertrag *je Risiko* | einer Mischung, die Ertrag und Ruhe abwägt |
| **Min. Drawdown** | den kleinsten zwischenzeitlichen Einbruch | Übergewicht für Papiere, die nie tief fielen |

Zusammen ergeben die drei höchstens 100 %; ziehst du einen hoch, weichen die
anderen automatisch zurück. Was an 100 % fehlt, bleibt dein jetziges Depot. Alle
Regler auf 0 heißt also „nichts ändern", ein Regler auf 100 heißt „ganz auf dieses
Ziel". Der Gesamtwert des Depots bleibt dabei in jeder Stellung gleich &ndash;
umgeschichtet, nicht ein- oder ausgezahlt.

**Der Zoom wählt den Zeitraum, über den gerechnet wird.** Ziehe im Chart
waagerecht: nach links verkürzt das Fenster, nach rechts verlängert es. Das
Fenster endet immer am aktuellen Rand, du wählst also nur, wie weit zurück
geschaut wird. Nach jedem Zoom rechnet die App neu &ndash; ein Papier kann über
zehn Jahre glänzend und über sechs Monate schwach aussehen, und der Optimierer
zeigt dir genau das.

### Wenn dich ein Papier nicht interessiert

Der Schalter **Fixiert** nimmt eine Position aus allen drei Optimierungen heraus:
Sie behält ihre Stückzahl, ihr Wert wird nicht umverteilt, und sie beeinflusst
auch die Rechnung der anderen nicht. Sinnvoll für alles, was du ohnehin nicht
anfassen willst, oder für einen Geldmarktfonds &ndash; der gewinnt sonst fast
jeden Vergleich, weil er kaum schwankt und nie einbricht.

Der Schalter gilt für die laufende Sitzung und wird bewusst nicht gespeichert:
Nach einem Neustart der App nehmen wieder alle Positionen teil.

## Selbst bauen

```bash
git clone https://github.com/Archimedes79/Portfolio_Optimizer_Classic.git
cd Portfolio_Optimizer_Classic
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
```

Die fertige APK liegt danach unter `app/build/outputs/apk/release/`.
Benötigt werden JDK 21 zum Bauen und das Android SDK, Platform 36 mit
Minor-API-Level 36.1; Android Studio bringt beides mit. Die Unit-Tests laufen
mit `./gradlew testDebugUnitTest`.

Das App-Icon ist als Adaptive Icon in
`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` definiert, seine Ebenen
liegen in `app/src/main/res/drawable/ic_launcher_*.xml`. Die Rastergrafiken für
ältere Android-Versionen erzeugt `python3 tools/generate_icons.py` aus derselben
Geometrie neu; das Skript benötigt Pillow und schreibt zusätzlich
`docs/icon-512.png`, erzeugt aber kein `ic_launcher_monochrome.xml`.

## Technik

Java, keine Compose-Abhängigkeit, drei Activities.

| | |
| --- | --- |
| Mathematik | [Apache Commons Math 3](https://commons.apache.org/proper/commons-math/) (Kovarianzmatrix, BOBYQA) |
| Charts | [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) |
| Persistenz | [Gson](https://github.com/google/gson) &rarr; `filesDir/portfolio.json` |
| Kursdaten | öffentliche Endpunkte von Yahoo Finance, Monatswerte, linear auf Tageswerte interpoliert; Werte vor dem ersten und nach dem letzten Stützpunkt werden geklemmt, nicht extrapoliert |
| Währung | automatische Umrechnung in EUR über das jeweilige FX-Paar (inkl. GBp); schlägt der Abruf des Wechselkurses fehl, wird der Preis unverändert übernommen |

## Hintergrund

Entstanden als Vibe-Coding-Experiment &ndash; eines, das tatsächlich funktioniert.

## Haftungsausschluss

Diese App ist ein Lern- und Analysewerkzeug. Ihre Ergebnisse sind **keine
Anlageberatung**, keine Empfehlung und kein Angebot zum Kauf oder Verkauf von
Finanzinstrumenten. Die Kursdaten stammen aus einer inoffiziellen, öffentlich
erreichbaren Quelle, können verzögert, unvollständig oder falsch sein und
jederzeit ausfallen. Jede Anlageentscheidung und deren Folgen liegen allein bei
dir.

## Lizenz

Proprietär, Quellcode einsehbar. Lesen, private Nutzung und Selbstbauen sind
erlaubt; Weiterverbreitung und kommerzielle Nutzung nicht ohne vorherige
schriftliche Genehmigung. Details in [LICENSE](LICENSE), Hinweise zu den
verwendeten Bibliotheken in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
