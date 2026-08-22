# L2Epic — modded Lineage 2 Interlude server

A custom **L2JFrozen 1.5** private server (Interlude / C6, protocol 740–746). Java source built with
Ant, run against MariaDB.

- [AI_Tools/docs/architecture-review.md](AI_Tools/docs/architecture-review.md) — project structure,
  the datapack model, known defects
- [AI_Tools/docs/server-internals.md](AI_Tools/docs/server-internals.md) — reverse-engineered
  reference: database layer, netcore, encryption, protocol, threading, world grid, spawning, buffs,
  items, mobs and drops

---

## Read this first — four things that cause wrong answers

### 1. The server is "Server 2 : Sieghardt", and it runs from `Deployemnt/`

That is the live instance. `Server/Sorces/` is where you *edit and build*; `Deployemnt/` is where the
result *runs*. Two processes:

| | Path | Port |
|---|---|---|
| LoginServer | `Deployemnt/loginserver/` | 9014 |
| GameServer | `Deployemnt/gameserver/` | 7777 |

Database: MariaDB, schema **`frozen`**, shared by both. User `root`, empty password, `127.0.0.1`.
See [Database](#database) — there are sibling schemas on the same instance, so confirm you are on
`frozen` before running anything.

### 2. `dist/` → `Deployemnt/` is the datapack model

`Server/Sorces/dist/` is the datapack produced from source. `Deployemnt/` is that same datapack
**integrated with the other server elements** — geodata, pathnode, `lib/*.jar`, `hexid.txt`, logs —
so the server can actually run. **Where they overlap they should stay identical.**

Because the `data/` trees are byte-identical, git tracks `dist/`'s copy as canonical and **ignores
`Deployemnt/gameserver/data/`**. A fresh clone therefore cannot run the server until you:

1. copy `Server/Sorces/dist/gameserver/data/` → `Deployemnt/gameserver/data/`
2. restore `data/geodata/` and `data/pathnode/` (652 MB) from `Development.7z`
3. restore `Deployemnt/*/lib/` dependency jars from `Development.7z` (only the 2 jars the server
   runs on are tracked)
4. recreate `hexid.txt` (gitignored — it is the login↔game shared secret)

`config/` and `sql/` **are** tracked on both sides, because they have drifted apart. See Known Issues.

### 3. The mods engine is open source and lives in-tree

`Server/Sorces/L2jEngine_Mods/` — 107 Java files, author *fissban*, free and open source. It
implements AIO, VIP, offline shops, vote rewards, scheme buffer, PvP reward, spree kills, anti-bot,
PvP colour, subclass accumulatives and the community board — everything configured by the 16 property
files in `Deployemnt/gameserver/config/engine/`.

These features **are modifiable**. Do not assume otherwise because they ship as `L2J_EngineMods.jar`.

*Caveat:* this source is Feb 2020 vintage and has not been recompiled against the current core. It
matches the running jar at symbol level (identical 156-class set), but a first rebuild may need small
fixes. Its `.classpath` expects a sibling Eclipse project named `L2jFrozen_15`.

### 4. AI-generated material goes in `AI_Tools/`

> Anything an AI writes that is **not** server code goes under `AI_Tools/` —
> `scripts/` for helpers, `docs/` for Markdown.

Server code, config, datapack and client files stay where they belong. **This file is the single
deliberate exception** — `CLAUDE.md` must sit at the repo root or Claude Code will not load it. See
[AI_Tools/README.md](AI_Tools/README.md).

---

## Layout

| Folder | Role |
|---|---|
| `Server/Sorces/head-src/` | 1390 Java files — the L2JFrozen core |
| `Server/Sorces/L2jEngine_Mods/` | 107 Java files — the mods engine |
| `Server/Sorces/dist/` | canonical datapack (scripts, html, xml, multisell, csv, sql) |
| `Deployemnt/` | the running server |
| `Client/system/` | hand-patched client files, pasted over a clean Interlude client |
| `Addons&Mods/` | intended home for 3rd-party components (empty) |
| `Tools/` | 3rd-party editors (`l2_fileedit`) |
| `AI_Tools/` | AI-generated scripts and docs |

---

## Build

**Java ≥ 11 is a hard gate** — both Ant targets fail outright below it. Currently runs on JDK 21.

```bash
cd Server/Sorces
ant -f build-core.xml     # core jar only — fast iteration
ant -f build.xml          # core + dist/ datapack -> build/L2JFrozen_15.zip
```

Deployment into `Deployemnt/` is **manual**. There is no automation, and that is the direct cause of
the version-skew issues below.

> **Deploying a jar is never just a file copy.** Check whether the build's
> `sql/install/` differs from the deployed one; if it does, the live `frozen` database needs
> migrating in the same operation.

---

## Database

MariaDB, running from `d:\company\web_dev\mysql\` (XAMPP-style), listening on `127.0.0.1:3306`.
Credentials `root` / no password. HeidiSQL is the usual GUI; the CLI is at
`d:\company\web_dev\mysql\bin\mysql.exe` (not on PATH).

```bash
"/d/company/web_dev/mysql/bin/mysql.exe" -h 127.0.0.1 -u root frozen -e "SHOW TABLES;"
```

**`frozen` is the live schema** — 123 tables, 22.9 MB, used by *both* the login and game server.

| | |
|---|---|
| Static game data | `spawnlist` 37k rows, `droplist` 27k, `npcskills` 25k, `territories` 18k, `npc` 7.4k |
| Live player data | 38 accounts, 20 characters, 2 clans, 1018 items |

**Sibling schemas on the same instance — do not confuse them for `frozen`:**

| Schema | Tables | Note |
|---|---|---|
| `gameserver_beta` | 132 | separate beta pair |
| `loginserver_beta` | 3 | |
| `l2jdb` | 92 | older/unrelated L2J database |

Two classes of table live side by side and behave differently:

- **Datapack tables** (`npc`, `spawnlist`, `droplist`, …) are reloaded from SQL on install. Edits
  belong in `dist/gameserver/sql/`, not made directly in HeidiSQL, or they are lost on reinstall.
- **`custom_*` tables** (`custom_npc`, `custom_spawnlist`) hold this server's additions and are the
  right place for new NPCs and spawns.
- **Player tables** (`characters`, `items`, `accounts`, `clan_data`) are live state — never
  reinstall over them. `DBExport/DB.sql` is a 2020 snapshot, not a current backup.

A caution learned the hard way: **adding a row to `custom_npc`/`npc` with a `type` that has no
matching Java class aborts spawn loading at startup** — see Known Issue 3.

## Where to add a mod

Reuse the existing extension points rather than inventing new ones:

| Want | Where |
|---|---|
| A player chat command (`.mycmd`) | `head-src/…/handler/voicedcommandhandlers/` — see `AwayCmd`, `BankingCmd` |
| An HTML link action | `head-src/…/handler/custom/CustomBypassHandler.java` |
| Item / skill / admin / user-command behaviour | sibling packages under `head-src/…/handler/` |
| An event | `head-src/…/model/entity/event/` — see `TownWar`, `Lottery` |
| NPC / quest / AI logic | `dist/gameserver/data/scripts/{ai,quests,custom,teleports,village_master,cron}/` (Jython) |
| AIO, VIP, buffer, vote reward, offline shop | `Server/Sorces/L2jEngine_Mods/src/main/engine/` |
| Rates, enchant, drops, siege, olympiad | `Deployemnt/gameserver/config/` — **and mirror into `dist/`** |

---

## Server identity

Mid-rate PvP. Changing these changes the server's character — confirm before touching.

- **Rates** — XP/SP/Party ×10, Adena ×10, Items/Spoil/Manor ×20, Quest drops ×30, quest reward ×20,
  Boss items ×30, Raid ×30
- **Enchant** — weapon/armor/jewel max **+16** (stock +10)
- **QoL** — AutoLoot on, weight limit ×4, 40 buff slots (stock 20), 10 debuff slots (stock 6)
- **Custom spawns** — GM Shops and Global Gatekeepers in every town

---

## Known issues

**1. Outstanding DB migration — one missing table.**
The Oct 2024 build was deployed on 2026-08-22 without its schema migration. Verified against the live
`frozen` database, most of it is already correct:

| Table | Live state | Action |
|---|---|---|
| `clan_privs` | already has `party` as PRI | none — already migrated |
| `character_skills_save` | exists, 3 rows | none |
| `character_skills_reuse_delay` | **missing** | **create it** |
| `character_skill_reuse_delays` | old name, 0 rows | drop once the new one exists |
| `character_skill_effects` | old name, 59 rows, identical columns to `character_skills_save` | copy the 59 rows across, then drop |

Symptom: `Table 'frozen.character_skills_reuse_delay' doesn't exist`. Saving works (the `_save` table
is present); the reuse-delay lookup throws. Schema for the new table is in
`Server/Sorces/dist/gameserver/sql/install/character_skills_reuse_delay.sql`. Also update
`Deployemnt/gameserver/sql/install/` to match `dist/` so the drift does not resurface.

**2. Config drift between `dist/` and `Deployemnt/`.**
8 files differ: `GM.xml`, `altsettings`, `boss`, `enchant`, `other`, `rates`, `sevensigns`,
`network/gameserver.properties`. Every customisation above exists **only** in `Deployemnt/` — `dist/`
is still stock. A full `ant build` plus clean integration would silently revert the server to ×1
rates. Closing this means promoting the `Deployemnt/` values into `dist/`.

**3. `ClassNotFoundException: L2EscortGardInstance`** at `SpawnTable.java:150`. Traced to a single
NPC added straight to the live DB, absent from `DBExport/DB.sql`:

```
npc id 100204  "Soldier"  type = L2EscortGard   (in `npc` table)
└── 2 spawn rows in custom_spawnlist
```

The core resolves an NPC type by appending `Instance`, so it looks for `L2EscortGardInstance` — which
exists in no source file.

**The blast radius is bigger than one NPC.** `SpawnTable.fillSpawnTable` wraps the whole
`while (rset.next())` loop in a single `try/catch`, so the throw kills **every remaining row**. Live
on 2026-08-22 15:52: `custom_spawnlist` holds **16** rows and only **11** spawns loaded — and the
server still logged `CustomSpawnTable: Loaded 11 Npc Spawn Locations` and started normally. The loss
is silent.

Fix by writing the class, retyping to an existing one (`L2Guard`), or deleting the 2 spawn rows
(`id 223856`, `226346`).

**4. `data/xml/globalDrop.xml` is missing**, upstream too. It stopped erroring after the Oct 2024
deploy; worth confirming rather than assuming resolved.

**5. Eclipse debug runs test the wrong config.** `launcher/Gameserver.launch` sets its working
directory to `dist/gameserver/`, which carries stock rates. Harmless once issue 2 is closed.

---

## Conventions

- Do not commit build output, logs, compiled classes, or dependency jars — see `.gitignore`.
  Two deliberate exceptions: all of `Client/system/` and the two jars the server runs on, both
  because they cannot be regenerated.
- `.gitattributes` forces LF on `*.sh`. The machine has `core.autocrlf=true` globally; without that
  rule the startup scripts break on a Linux host.
- `hexid.txt` is a secret and is gitignored. `DatabaseUser = root` with an empty password is tracked
  as ordinary local config — do not carry that to a public host.
