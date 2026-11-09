# Win Launcher

Launcher Android in stile Windows 11 per **TV, tablet e smartphone**, verticale e orizzontale.

- Desktop con sfondo, icone, taskbar centrata con Start, Cerca, app aperte, batteria e orologio
- Menu Start con app bloccate, elenco di tutte le app e ricerca
- Finestre integrate (Browser, Note, Calcolatrice, Impostazioni): trascina, ridimensiona dai bordi,
  **minimizza**, **massimizza/ripristina** (doppio tocco sulla barra del titolo), chiudi,
  snap a sinistra/destra/alto trascinando sul bordo
- Su schermi stretti (telefono) le finestre si aprono a tutto schermo
- Telecomando/tastiera: frecce per navigare; sulla barra del titolo OK alterna spostamento e ridimensionamento; tasto Menu apre Start
- App di terze parti: si aprono in finestra libera se il sistema la supporta (Android 14+ desktop mode, DeX...), altrimenti a schermo intero

## Build

GitHub Actions (`.github/workflows/build.yml`) compila a ogni push e carica `WinLauncher.apk` tra gli artifact.
Con un tag `v*` crea anche una release. Manualmente: `gradle assembleDebug` (Gradle 8.7, JDK 17).

Dopo l'installazione: Impostazioni > App > App predefinite > App Home, e scegli Win Launcher.
