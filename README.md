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
| **Minimum Variance** | Global Minimum Variance, geschlossene Markowitz-Lösung `w = Σ⁻¹1 / 1ᵀΣ⁻¹1` | geringste Schwankung |
| **Max Sharpe Ratio** | Tangentialportfolio `w = Σ⁻¹μ / 1ᵀΣ⁻¹μ` (risikoloser Zins = 0) | bestes Rendite-Risiko-Verhältnis |
| **Min Drawdown** | ableitungsfreie BOBYQA-Optimierung auf den maximalen Drawdown | kleinster zwischenzeitlicher Verlust |

Der nicht verteilte Rest bleibt dein Ist-Portfolio, sodass du stufenlos
zwischen „alles so lassen“ und „voll optimiert“ mischen kannst. Die Tabelle
zeigt dir für jede Position, wie viele Anteile du kaufen oder verkaufen
müsstest.

**Weitere Eigenschaften**

- Alle Verfahren sind **long-only** &ndash; keine Leerverkäufe.
- Positionen lassen sich als **fixiert** markieren; sie bleiben unangetastet,
  ihr Wert wird aus der Optimierung herausgerechnet.
- Der Gesamtwert des Depots bleibt bei jeder Umschichtung erhalten (geprüft mit
  0,1 % Toleranz).
- Die Optimierung läuft immer auf dem **sichtbaren Zeitfenster** des Charts:
  zoomst du den Graphen, wird neu gerechnet.
- Bis zu 24 Positionen.
- Alle Daten liegen ausschließlich lokal auf dem Gerät (`portfolio.json`).
  Kein Konto, kein Backend, keine Analytics.

## Installation

1. Unter [**Releases**](../../releases/latest) die aktuelle `.apk` herunterladen.
2. Beim Öffnen fragt Android einmalig nach der Erlaubnis, Apps aus dieser
   Quelle zu installieren (Browser bzw. Dateimanager) &ndash; bestätigen.
3. Installieren. Voraussetzung: **Android 7.0 (API 24)** oder neuer.

> Solange noch kein Signaturschlüssel hinterlegt ist, wird die APK mit dem
> Android-Debug-Key signiert. Sie lässt sich normal installieren, kann aber
> keine anders signierte Installation aktualisieren. Siehe
> [docs/RELEASING.md](docs/RELEASING.md).

## Selbst bauen

```bash
git clone https://github.com/Archimedes79/Portfolio_Optimizer_Classic.git
cd Portfolio_Optimizer_Classic
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
```

Die fertige APK liegt danach unter `app/build/outputs/apk/release/`.
Benötigt werden JDK 21 und das Android SDK (Platform 36); Android Studio
bringt beides mit. Die Unit-Tests laufen mit `./gradlew testDebugUnitTest`.

Das App-Icon ist als Adaptive Icon in
`app/src/main/res/drawable/ic_launcher_*.xml` definiert; die Rastergrafiken für
ältere Android-Versionen erzeugt `python3 tools/generate_icons.py` aus derselben
Geometrie neu.

## Technik

Java, keine Compose-Abhängigkeit, drei Activities.

| | |
| --- | --- |
| Mathematik | [Apache Commons Math 3](https://commons.apache.org/proper/commons-math/) (Kovarianzmatrix, BOBYQA) |
| Charts | [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) |
| Persistenz | [Gson](https://github.com/google/gson) &rarr; `filesDir/portfolio.json` |
| Kursdaten | öffentliche Endpunkte von Yahoo Finance, Monatswerte, linear auf Tageswerte interpoliert |
| Währung | automatische Umrechnung in EUR über das jeweilige FX-Paar (inkl. GBp) |

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
erlaubt; Weiterverbreitung und kommerzielle Nutzung nicht. Details in
[LICENSE](LICENSE), Hinweise zu den verwendeten Bibliotheken in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
