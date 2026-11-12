# Orbital Mace (Fabric, Minecraft 1.21.11)

Neues Item: **Orbital Mace** (Kreativ-Tab "Kampf" oder craftbar).
Jeder Treffer (normal oder Smash) ruft vom Himmel eine **Orbital-Nuke** (Power 9) plus 10-16 **Stab-Shots** herbei.
Die Stab-Shots kommen zu unterschiedlichen Zeiten, mit unterschiedlicher Staerke, Fallgeschwindigkeit
und Position (manche setzen Feuer). Abklingzeit: 8 Sekunden. Textur: Vanilla-Mace + animierte Explosions-Ebene. Der Besitzer ist waehrend des Strikes unverwundbar.

Rezept:  T N T / _ M _ / T N T   (T = TNT, N = Netherstern, M = Mace)

Einstellungen: oben in `OrbitalStrike.java`.

## Jar bauen
**Variante A - GitHub (ohne Installation):** Ordner als Repo hochladen -> Tab "Actions" -> "build" -> Artifact `orbital-mace-jar`.
**Variante B - lokal:** JDK 21 + Gradle 9.x installieren, dann `gradle build` -> `build/libs/orbital-mace-1.0.0.jar`
(nicht die `-sources.jar`).

Falls Gradle meckert: aktuelle Werte fuer `loader_version`, `loom_version`, `fabric_api_version` in
`gradle.properties` von https://fabricmc.net/develop (1.21.11) uebernehmen.

## Modrinth
Neues Projekt -> Version hochladen -> Jar, Loader: Fabric, Game Version: 1.21.11, Dependency: Fabric API.
