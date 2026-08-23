# Craftmetry

Craftmetry adds a small clicks-per-second counter to the Minecraft HUD. `L`
tracks the mapped attack control and `R` tracks the mapped use control over a
rolling one-second window.

Craftmetry runs only on the client. It does not save click data or send anything
over the network.

## Requirements

This alpha is tested with:

- Minecraft: Java Edition 26.2
- Fabric Loader 0.19.3
- Fabric API 0.157.0+26.2
- Java 25

## Install

1. Install Fabric Loader for Minecraft 26.2 using the
   [official Fabric installer](https://fabricmc.net/use/installer/).
2. Download Fabric API 0.157.0+26.2 and the Craftmetry JAR from the latest
   release.
3. Put both JARs in the Minecraft `mods` folder.
4. Select the Fabric 26.2 profile in the Minecraft Launcher and start the game.

Fabric documents the `mods` folder location for Windows, macOS, and Linux in
its [player guide](https://docs.fabricmc.net/players/installing-mods).

Enter a world and click. The counter appears in the upper-left corner and drops
back to zero one second after the last click. Press `H` to show or hide it. You
can change the binding under **Options > Controls > Key Binds > Craftmetry**.

## Build

Use JDK 25:

```bash
./gradlew build
```

The mod JAR is written to `build/gradle/libs/`.

To launch a development client:

```bash
./gradlew runClient
```

To launch the packaged JAR in a production-style client:

```bash
./gradlew runProductionClient
```

## Status

This is an early alpha. Test it in a separate Minecraft profile and use a test
world for the first run.

## License

Craftmetry is licensed under the Apache License 2.0.
