# Bleep (Minecraft mod)

A Minecraft mod by Hook Development: small robots on tracks with a display for a face. They are pets with a character, and useful too. Stand-alone: no link with Setpoint (`../setpoint`). Site: https://hookdev.io (site repo `../hook-development`, see its CLAUDE.md).

## Working with the owner
- Talk to the owner in **Dutch**. All mod text (item names, thoughts, the info screen, the Bleepedia, README, changelogs) is **English**.
- The owner directs the work and the design; they do not write Java. Do the technical work, explain in plain language, one clear step at a time.
- **The design document** is `bleepdd.md` next to this file (Dutch, in-game texts in English). Read it before building anything. It and the reference pictures in `ref/` stay out of git (.gitignore): look in `ref/` before building something the owner describes.
- **Privacy:** this repository is public. Never add the owner's name or personal details anywhere (code, docs, metadata, CurseForge pages, commit messages, file paths). The author is "Hook Development".
- **Secrets (owner, 30 Oct 2026: locked down exactly like Setpoint):** never commit a token, key, password or .env file. .gitignore blocks .env, keys and secrets/; a local pre-commit hook (.git/hooks/pre-commit, not in git: install it again on a new computer, copy it from Setpoint) refuses secret-looking files and lines and the owner's name; .github/workflows/secret-scan.yml runs gitleaks over the whole history on every push. A CurseForge token will live only in a repository secret. If a secret ever gets out, revoke it at once: removing the commit is not enough.
- Ask the owner before every release. CurseForge only, not Modrinth (Modrinth refuses projects that are primarily AI-made). Be open about the mod being made with AI.
- Ideas go in the backlog below instead of being built all at once.
- Do not build or run the game while the owner's Minecraft is open.

## Technical
- NeoForge, Java 25, MIT. Mod id `bleep` (permanent after the first release), package `io.hookdev.bleep`.
- Minecraft 26.3 and 26.1.2, one build per version, set up the same way as Setpoint (`versions/<mc>/gradle.properties`, `-Pmc=26.1`); every change must build and pass the game tests on both.
- Jade and JEI support, like Setpoint.
- Animations: Minecraft's own entity animation system, no extra library, so players need nothing else installed.

## Backlog
- [ ] v0.1 (design doc, roadmap): Bleep Core and parts, assembly with a start-up animation, driving / jumping / swimming / picking up items, the display with six emotions plus tired and asleep, battery and homebase, name above the head, relationship meter and info screen, follow / stay / go home, obsession, mob thoughts, fear of llamas. Build it in steps: first a bleep that drives and pulls faces.
- [ ] Check the mob list in the design doc against the 26.3 game files.
- [ ] Open question: does giving redstone count as a gift (+3), as charging (v0.2), or both?
- [ ] Open questions in the design doc (part locations, bleeps per player, display resolution, sounds, recipes, numbers).
