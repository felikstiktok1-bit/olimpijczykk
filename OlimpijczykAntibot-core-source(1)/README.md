# OlimpijczykAntibot-core — Paper 1.21.4

Plugin weryfikacji graczy przez GUI kowadła.

## Wymagania
- Paper 1.21.4
- Java 21
- Maven 3.9+ do zbudowania projektu

## Budowanie
W folderze projektu uruchom:
```bash
mvn clean package
```
Gotowy plik będzie w `target/OlimpijczykAntibot-core-1.0.0.jar`.

## Instalacja
1. Skopiuj JAR do folderu `plugins` serwera.
2. Uruchom serwer.
3. Zmień ustawienia w `plugins/FireAntiBot/config.yml`.
4. Zrestartuj serwer.

## Działanie
- Nowy losowy kod przy każdym wejściu.
- Kod widoczny w tytule GUI kowadła.
- Czerwony barwnik anuluje i wyrzuca gracza.
- Zielony barwnik zatwierdza wpisany kod.
- Kartka pokazuje instrukcję.
- BossBar odlicza czas.
- Do weryfikacji gracz nie może chodzić ani używać komend/czatu.
- `preventClose()` blokuje zamknięcie GUI klawiszem ESC.

Gracz nadal może rozłączyć się z serwerem (np. zamknąć grę); tego nie da się wiarygodnie zablokować pluginem.
