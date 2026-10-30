<p align="center">
  <img src=".github/readme/banner.png" width="100%" alt="Bleep: a robot pet with an attitude. BLEEP in pixel letters next to a blue bleep in a top hat, a red one in a party hat and a lime one with an antenna.">
</p>

<p align="center">
  <a href="https://github.com/ParadiseArchive/bleep/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/ParadiseArchive/bleep/build.yml?branch=main&style=flat-square&label=build&labelColor=0a0c13&color=2de2e6" alt="Build"></a>
  <img src="https://img.shields.io/badge/minecraft-26.1.2%20%7C%2026.3-2de2e6?style=flat-square&labelColor=0a0c13" alt="Minecraft 26.1.2 and 26.3">
  <img src="https://img.shields.io/badge/neoforge-26.1.2%20%7C%2026.3-2de2e6?style=flat-square&labelColor=0a0c13" alt="NeoForge 26.1.2 and 26.3">
  <img src="https://img.shields.io/badge/version-0.1.0-ffb547?style=flat-square&labelColor=0a0c13" alt="Version 0.1.0">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-ff4f8b?style=flat-square&labelColor=0a0c13" alt="License: MIT"></a>
</p>

<p align="center">
  <a href="https://hookdev.io/projects/minecraft-mods/bleep/"><b>Read the docs</b></a> &nbsp;·&nbsp;
  <a href="https://hookdev.io/projects/minecraft-mods/"><b>Devlog</b></a> &nbsp;·&nbsp;
  <a href="https://hookdev.io"><b>hookdev.io</b></a>
</p>

---

Bleep is a Minecraft mod about little robots on tracks with a display for a face. A bleep is not a worker but a pet
with a character: it shows how it feels on its screen, thinks out loud and is a little bit naughty. It never dies: it
only runs out of battery.

**A pet that feels alive.** Look after it and it likes you more: pet it, bring it treats, find out its favourite
thing. A new bleep sometimes dawdles over what you ask; a best friend hardly ever does.

## The heart of it: build your own

A bleep is made of four parts. The most important one is the core, made of bedrock: find one deep down, or craft it
if you can spare thirteen diamonds.

1. Find a **Bleep Core** in the chest of a dungeon or an abandoned mineshaft, or craft one.
2. Craft its **Display**, its **Tracks** and its **Arms** from iron, redstone, black glass and dried kelp.
3. Right-click the ground with the core, the other three in your inventory. Your bleep is put together part by part,
   clunk by clunk, until its screen comes on for the very first time.

From then on it is yours. It follows you, drives circles round you when you hold its favourite item, drives over to
every new kind of mob for a good look, and drives off in a hurry when it sees a llama.

<p align="center">
  <img src=".github/readme/shelf-faces.png" width="100%" alt="Every face on the display: happy, excited, sad, angry, bored, surprised, tired, asleep, stubborn, glitching and starting up.">
</p>

## Talk to it

Right-click your bleep for its info screen: its face as on its display, its mood and what it is doing, its battery,
your bond, its favourite item (??? until you find out what it is), what it is thinking, and the Follow, Stay and Go
Home buttons. Sneak-right-click to pet it. It talks back in blips, a sound for every mood.

<p align="center">
  <img src=".github/readme/info-screen.png" width="49%" alt="The info screen: a happy face, the name Bolt, mood Happy, Following you, battery 73 %, bond Friend, favourite ???, a thought, the Follow, Stay and Go Home buttons, and a book with a ! for its Bleepedia.">
</p>

## What is in it

### The bleep
Eleven faces on a display that glows in the dark, arms and a head that move with its mood, and a voice of its own. A
battery instead of health: driving and damage drain it, and an empty bleep switches off with a sleepy face. Charge it on
its homebase, with redstone or a power bank, or let lightning overcharge it. A bond that
grows with care and teaches it new things: a Friend carries a torch that lights the way, a Buddy fights at your side and
brings you what it finds. A favourite item, a dry remark about every mob in the game, naughty tricks when it is bored,
and a fear of llamas nobody can explain.

<img src=".github/readme/shelf-bleep.png" width="100%" alt="A bleep being put together, happy, excited, switched off and charging at home.">

### Dress it up
Any dye colours its shell. It wears a hat (a party hat, a top hat or an antenna with a bow) and a sticker on its front,
and its display has tricks: its name as a ticker, tic-tac-toe when it is bored, an arrow home.

<img src=".github/readme/shelf-dress.png" width="100%" alt="Six bleeps in six colours, in party hats, a top hat and antennas, with stickers.">

### The Bleepedia and the world
Every bleep keeps a book: the Bleepedia, behind the book on its info screen. Every mob it met (with its face and its
opinion), every biome it drove through, the special blocks it spotted, its stats and a diary in its own words; a ! on
the book when there is something new. Out in the world it dances when a record plays, sleeps next to your bed, digs up
bits and bobs and adores iron golems. Now and then you find an old, abandoned bleep: a block of redstone wakes it up.

<img src=".github/readme/bleepedia.png" width="420" alt="The Bleepedia: tabs for mobs, biomes, finds, about me and its diary, with a creeper it has met between the ones it has not.">

<img src=".github/readme/shelf-world.png" width="100%" alt="An abandoned bleep in faded paint, an orange bleep dancing in a party hat, and a bleep asleep.">

### Parts and home
The core and the three parts a bleep is built from, a power bank, and the homebase: a charging pad it goes home to when it runs empty, and that Go
Home sends it to. Its light burns while it charges.

<img src=".github/readme/shelf-parts.png" width="100%" alt="Bleep Core, Bleep Display, Bleep Tracks, Bleep Arms and the homebase.">

## Get it

Bleep is not out yet: the first release, **0.1.0**, is on its way to CurseForge. Until then you can build it yourself
(see below).

- Minecraft 26.3 or 26.1.2 (the version of All the Mods 11) with NeoForge, Java 25: pick the file for your Minecraft version
- In-game help: tooltips on every part, and in JEI every recipe and where the core is found
- Works with Jade (battery, mood and bond when you look at a bleep) and JEI
- Stand-alone: no other mod needed

The full documentation, with every face, part, recipe and the info screen, is on
**[hookdev.io](https://hookdev.io/projects/minecraft-mods/bleep/)**.

## Building

```
./gradlew build               # jar for Minecraft 26.3 in build/libs
./gradlew build -Pmc=26.1     # jar for Minecraft 26.1.2 in build-26.1/libs
./gradlew runClient           # start a development client
./gradlew runGameTestServer   # run the automated in-game tests
```

Every push is built and tested by GitHub Actions. The pictures in this README come from `tools/readme-art.mjs`.

## License

MIT, see [LICENSE](LICENSE). The ideas, the character and the gameplay are Hook Development's; the code and the
textures are written with Claude, an AI assistant.
Minecraft names used in code come from Mojang's official mappings, see https://github.com/NeoForged/NeoForm/blob/main/Mojang.md.

<sub>NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.</sub>

<p align="center"><a href="https://hookdev.io"><sub>made by Hook Development</sub></a></p>
