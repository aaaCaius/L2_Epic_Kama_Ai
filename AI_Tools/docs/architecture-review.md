# L2Epic — Architecture Review

*Written 2026-08-22. Records the state of the tree on that date; verify paths and table names before
acting on anything here.*

L2Epic is a **modded Lineage 2 Interlude private server** built on **L2JFrozen 1.5**
(upstream: sourceforge.net/p/l2jfrozen, build stamp `20-01-2020`). Worked on since Jan 2020, last
built 2024-10-15.

---

## 1. Folder roles

| Folder | Role | Size |
|---|---|---|
| `Server/Sorces/` | **Source of truth.** Java source + Ant build + Eclipse project. | 924 MB |
| `Deployemnt/` | **The server.** Build output from `dist/` integrated with the runtime elements needed to actually run and test. | 746 MB |
| `Client/system/` | Modified L2 client files, pasted over a clean Interlude client. | 57 MB |
| `Addons&Mods/` | Intended home for 3rd-party components. Currently empty. | 0 |
| `Tools/` | 3rd-party editors — `l2_fileedit interlude.exe` + a tools checklist. | 445 KB |
| `Server/output/` | Last packaged release. Not tracked in git. | 904 MB |
| `Development.7z` | Backup archive. Not tracked in git. | 778 MB |
| `AI_Tools/` | AI-generated scripts and docs. See its `README.md`. | — |

---

## 2. The server — "Server 2 : Sieghardt"

The live instance is the standalone one in `Deployemnt/`. Two-process L2J topology, Java 11+ source
running on **JDK 21**.

- **LoginServer** — `Deployemnt/loginserver/`, port `9014`. Authenticates and hands off to game
  servers registered via `hexid.txt`.
- **GameServer** — `Deployemnt/gameserver/`, port `7777`. Registers on login as
  **Server 2 : Sieghardt**. Started by `startGameServer.bat` with `-Xms1024m -Xmx1024m`.
- **Database** — MariaDB, schema `frozen`, shared by both servers. User `root`, empty password,
  `127.0.0.1`. Bootstrap: 108 files in `sql/install/`, 4 in `sql/customs/`, plus a 13 MB 2020
  snapshot at `DBExport/DB.sql`.
- **Networking** — `ExternalHostname = 25.75.151.11` (Hamachi VPN),
  `InternalHostname = 192.168.100.10` (LAN). Protocol revisions 740–746 = Interlude C6.

---

## 3. The datapack model

`dist/` is the datapack produced from source. `Deployemnt/` is that same datapack **integrated with
the other server elements** — geodata, pathnode, `lib/*.jar`, `hexid.txt`, logs — so the server can
run and be tested. **Where they overlap they should be identical.**

Measured against that invariant on 2026-08-22:

| Area | State |
|---|---|
| `data/` (scripts, html, xml, multisell, csv, geodata, pathnode) | **identical** — only 6 runtime artifacts differ: `clans/`, `crests/`, an error log, 2 compiled `.py.class`, a stray `dist/.../task/temp.txt` |
| `sql/customs/` | identical |
| `config/` | **drifted** — 8 files (Finding D) |
| `sql/install/` | **drifted** — 3 files, a schema generation mismatch (Finding B) |

Because the `data/` trees are byte-identical, git tracks `dist/`'s copy as canonical and ignores
`Deployemnt/gameserver/data/`. A fresh clone must therefore copy `dist/gameserver/data/` into
`Deployemnt/`, plus restore `geodata/` and `pathnode/` from `Development.7z`, before the server runs.

**Inconsistency worth knowing:** `Server/Sorces/launcher/Gameserver.launch` sets
`WORKING_DIRECTORY = ${workspace_loc:L2jFrozen_15}/dist/gameserver/`, so an Eclipse debug run
executes against `dist/` — which carries stock ×1 rates, not the server's real tuning. Harmless once
the config drift is closed; until then, Eclipse debugging tests different settings than Server 2 runs.

---

## 4. Source layout — 1390 Java files

`Server/Sorces/head-src/com/l2jfrozen/`:

| Package | Files | Purpose |
|---|---|---|
| `gameserver/` | 1304 | ai, cache, communitybbs, controllers, datatables, geo, handler, idfactory, managers, model, network, script, scripting, skills, taskmanager, templates, thread, updaters, util |
| `loginserver/` | 39 | login/auth |
| `util/` | 22 | |
| `netcore/` | 14 | MMO networking layer |
| `crypt/` | 6 | |
| `gsregistering/` | 1 | |

Eclipse project `L2jFrozen_15` — `.classpath`, `.project`, `.settings/`, `launcher/*.launch`.

### Extension points — reuse these rather than inventing new ones

- **Voiced commands** — `handler/voicedcommandhandlers/` — `.away`, `.bank`, `.online`, `.repair`,
  `.time`, `.stats`, `.wedding`
- **Custom bypasses** — `handler/custom/CustomBypassHandler.java`
- **Item / skill / admin / user-command handlers** — sibling packages under `handler/`
- **Java events** — `model/entity/event/` — `TownWar`, `Lottery`, `ChristmasPresents`
- **Jython datapack scripts** — `data/scripts/{ai,quests,custom,teleports,village_master,cron}/`

---

## 5. The mods engine — `L2J_EngineMods`

A second codebase supplies most of what makes this server "modded": AIO, VIP, offline shops, vote
rewards (Hopzone/Topzone/Network), scheme buffer, PvP reward, spree kills, anti-bot, PvP colour,
subclass accumulatives, community board. Configured by the 16 property files in
`Deployemnt/gameserver/config/engine/`.

It is **free and open source**, by author *fissban* (`fissban.l2devsadmins.net`). See Finding A for
where the source lives and why it was initially thought to be closed.

---

## 6. The server's identity (config deltas vs. stock L2JFrozen)

A **mid-rate PvP server**:

- **Rates** — XP/SP/Party ×10, Adena ×10, Items/Spoil/Manor ×20, Quest drops ×30, quest reward ×20,
  Boss items ×30, Raid ×30
- **Enchant** — weapon/armor/jewel max **+16** (stock +10)
- **QoL** — AutoLoot on, weight limit ×4, 40 buff slots (stock 20), 10 debuff slots (stock 6)
- Also modified: `GM.xml`, `boss.properties`, `other.properties`, `sevensigns.properties`
- Custom SQL: GM Shops + Global Gatekeepers in every town, auto-restart, Olympiad hero appearance,
  skill enchanter NPC

---

## 7. Build → deploy pipeline

```
head-src/  --ant build-core.xml-->  build-core/l2jfrozen-core.jar   (core only, fast)
head-src/  --ant build.xml------->  build/L2JFrozen_15.zip          (core + dist/ datapack)
                                     └─ MANUALLY integrated into Deployemnt/   ← no automation
```

Both targets **hard-fail unless Java ≥ 11**. The manual integration step is the direct cause of
Finding B.

---

## 8. Related trees on this machine (not part of this repo)

- `D:\Linage_project\` — the **original Feb 2020 SVN workspace**, correctly structured with
  `Server/Sorces/L2jFrozen_15/` and `Server/Sorces/L2jEngine_Mods/` as sibling Eclipse projects.
  L2Epic's core has since diverged (345 differing/added files in `head-src`). Its value is the
  EngineMods source.
- `D:\Company\L2Epic\Development_b\` and `D:\Company\L2Epic\new\` — older snapshots.

---

# Findings

## Finding A — the mods engine source exists

`Server/Sorces/.classpath` carries this pointer:

```xml
<classpathentry kind="lib" path="lib/L2J_EngineMods.jar" sourcepath="/L2jEngine_Mods"/>
```

It references an Eclipse project `L2jEngine_Mods` that is **absent from this tree** — which is why
the jar initially appeared to be closed-source. The project is at:

**`D:\Linage_project\Server\Sorces\L2jEngine_Mods\`** — 107 Java files under `src/main/`, its own
`build.xml`, its own `dist/gameserver/{config/engine,data/{html,multisell,xml},sql}`, an SVN working
copy, and a `README.txt` declaring *"free and open source, Author: fissban"*.

Verified faithful to what actually runs, by class-set comparison:

| Jar | Date | Size | MD5 | Classes |
|---|---|---|---|---|
| `L2jEngine_Mods/build/…` | 2020-02-24 | 483325 | `dbc8038b…` | 156 |
| `Deployemnt/…/lib/` (until 2026-08-22) | 2023-11-02 | 479098 | `217a0940…` | 156 |
| `Server/Sorces/lib/` + `output/` | 2020-01-20 | 478787 | `ed5a78e4…` | 156 |

Three different binaries, **identical 156-class sets, zero symbol difference** — the same codebase
recompiled. The source is complete and authoritative.

**Consequence:** AIO, VIP, events, scheme buffer and vote rewards **are modifiable**.

**Caveat:** the source is Feb 2020 vintage and its `.classpath` expects an Eclipse project named
`/L2jFrozen_15`. The class-set match confirms it at symbol level only — it has not been recompiled
against L2Epic's later core, so a first rebuild may need small fixes.

## Finding B — the Oct 2024 build was deployed on 2026-08-22; the matching DB migration was not

Until that afternoon `Deployemnt/` was a coherent **Nov 2023** snapshot (`l2jfrozen-core.jar`
`0bf9107619…`, `L2J_EngineMods.jar` `217a0940…`). Between 13:49 and 14:11 those were replaced with
the **Oct 2024** build (`76d1b241…` and `ed5a78e4…`).

**What that fixed** — the `L2Multisell` package move landed, so
`data/scripts/custom/5001_NewbieCoupons/__init__.py:4` no longer throws `ImportError`. Its leftover
`.error.LOGGER` file is stamped 13:49 and is stale.

**What it broke** — the jar moved generations but the `frozen` database did not:

| Deployemnt / live DB (old) | dist / Oct 2024 jar expects (new) |
|---|---|
| `character_skill_reuse_delays` | `character_skills_reuse_delay` |
| `character_skill_effects` | `character_skills_save` |
| `clan_privs` PK `(clan_id, rank)` | `clan_privs` + `party` col, PK `(clan_id, rank, party)` |

Confirmed live in the 14:11 run:

```
Table 'frozen.character_skills_reuse_delay' doesn't exist
L2PcInstance.storeEffect: Could not store char effect data into character_skill_effects
L2PcInstance.restoreEffects : Could not restore active effect data
```

Skill persistence is broken in both directions, and an earlier attempt shows
`Failed reading: [C] 03 EnterWorld … Map.get(Object) is null` on a real login.

**Deploying a jar is not a file copy.** The three tables must be migrated in the live `frozen`
database in the same operation, and `Deployemnt/gameserver/sql/install/` updated to match `dist/`.
**This is the server's most pressing outstanding defect.**

## Finding C — two further startup errors

- **`ClassNotFoundException: L2EscortGardInstance`** at `SpawnTable.java:150` — still firing at
  14:11:25. A row in the live `custom_spawnlist` table names an NPC type present in no source and no
  jar, and not in `DBExport/DB.sql` — it was added straight to the live DB. Custom spawn loading
  aborts.
- **`data/xml/globalDrop.xml` is absent** (also missing from `dist/`, i.e. missing upstream). It did
  not raise an error in the 14:11 run, unlike earlier runs — worth a second look rather than
  assuming it is resolved.

## Finding D — 8 config files have drifted out of the dist↔Deployemnt invariant

`GM.xml`, `altsettings.properties`, `boss.properties`, `enchant.properties`, `other.properties`,
`rates.properties`, `sevensigns.properties`, `network/gameserver.properties`.

Every live customisation (×10 rates, +16 enchant, AutoLoot, 40 buffs) exists **only** in
`Deployemnt/` — `dist/` is still stock. A full `ant build` plus clean integration would silently
revert Server 2 to stock rates. The 2026-08-22 redeploy touched only `lib/`, so the customisations
survived — but that was luck, not design.

Both sides are tracked in git so the drift is diffable. Closing it means promoting the `Deployemnt/`
values into `dist/`.

## Finding E — secrets and host addresses sit in plain config

`hexid.txt` (the login↔game shared secret, in both `Deployemnt/gameserver/config/` and
`Deployemnt/loginserver/`), `DatabaseUser = root`, and the Hamachi/LAN IPs. `hexid.txt` is
gitignored; the rest are tracked, since they are ordinary server config.
