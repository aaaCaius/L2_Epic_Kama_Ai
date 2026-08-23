# Reworking the database

> Prerequisite for the Living Aden economy — but worth doing regardless. Every finding below was
> verified against this tree on 2026-08-23.

---

## The core problem

**This project has no migration path.** Every schema change ever made was applied by hand and left no
record. There is no way to answer "what schema is the live database at?" except by inspecting it.

Adding four economy tables the same way makes it worse. Fix the foundation first — it is a day of work
and it protects everything after it.

---

## What is actually wrong

### 1. The installer would delete every character

`sql/installer_db.bat` loops `install/` and pipes each file into mysql. **107 of the 108 files begin
with `DROP TABLE IF EXISTS`** — including:

| File | What it destroys |
|---|---|
| `characters.sql` | every character |
| `items.sql` | every item in the game |
| `clan_data.sql` | every clan |
| `character_skills.sql` | every learned skill |

It is a **fresh-install tool**, it is named as though it were routine, it ends with `pause` as if it
were safe to double-click, and nothing in it warns otherwise. This is the single most dangerous file in
the repository.

### 2. It never applies `customs/`

```bat
for /r install/ %%f in (*.sql) do ( ... )
```

`customs/` is not iterated. All six files there — including `caravan_faction.sql` and
`caravan_ondemand.sql`, which the caravan feature genuinely depends on — must be run by hand, every
time, on every environment, from memory.

### 3. Its mysql path is wrong for this machine

```bat
set PATH=%PATH%;C:\xampp\mysql\bin
```

The client here is at `D:\company\web_dev\mysql\bin`. The installer cannot run as written — which is
the only reason the previous point has never caused a disaster.

### 4. Nothing records what has been applied

No `schema_version` table, no migration log, nothing. Changes applied by hand and now invisible include:

- `character_skills_reuse_delay` — created and back-filled during this session
- `caravan_faction.sql` — applied by hand; the escort AI depends on it
- `caravan_ondemand.sql` — applied by hand

A rebuilt environment silently lacks all of them.

### 5. `dist/` and `Deployemnt/` have drifted

```
character_skills_reuse_delay.sql   differs
character_skills_save.sql          differs
clan_privs.sql                     differs
```

Two sources of truth, disagreeing, with no way to tell which is right.

---

## The rework

### Step 1 — Reclassify the folders

`install/` currently conflates three things that must be handled completely differently:

| New folder | Contents | Safe to run on a live DB? |
|---|---|---|
| `baseline/` | Full schema for a **fresh** install — today's `install/`, unchanged | **No. Never.** |
| `migrations/` | Ordered, additive, idempotent changes | **Yes — this is the only thing that runs on live** |
| `seed/` | Datapack reference data reloaded on install | Yes, for datapack tables only |
| `customs/` | This server's additions — converted into migrations over time | becomes migrations |

### Step 2 — A version table

```sql
CREATE TABLE IF NOT EXISTS `db_migration` (
  `id`         VARCHAR(64) NOT NULL,          -- '0004_eco_schema'
  `applied_at` DATETIME    NOT NULL,
  `checksum`   CHAR(32)    NULL,              -- detects edited-after-apply
  `ok`         TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
);
```

Four columns answer every question worth asking: what ran, when, whether the file changed since, and
whether it succeeded.

### Step 3 — A runner that is safe by construction

`AI_Tools/scripts/db-migrate.ps1`, following the pattern of the existing `start-servers.ps1`:

1. **Refuse to run without a reachable database**, and print which schema it is about to touch.
   Sibling schemas (`gameserver_beta`, `l2jdb`) exist on the same instance — naming the target out
   loud is a real safeguard.
2. **Take a `mysqldump` backup first**, timestamped, every time, no flag to skip it. `DBExport/DB.sql`
   is a 2020 snapshot, not a backup.
3. **Apply only unapplied migrations**, in filename order, recording each in `db_migration`.
4. **Stop at the first failure** and report which file, rather than continuing.
5. **`-WhatIf` mode** listing what would run without touching anything.
6. **Never reference `baseline/`.** The runner physically cannot perform a destructive install.

### Step 4 — Baseline the live database

The live `frozen` schema already has hand-applied changes. So migration `0001` is a **no-op that simply
records the current state as the starting point** — it asserts nothing and changes nothing.

Migrations `0002` and `0003` then re-express the hand-applied changes **idempotently**
(`CREATE TABLE IF NOT EXISTS`, guarded `UPDATE`s), so they are correct whether or not they already ran.
That converts undocumented live state into recorded, reproducible history without touching a row.

### Step 5 — Retire the destructive installer

Rename `installer_db.bat` to `fresh_install_DESTRUCTIVE.bat`, fix its mysql path, and add a typed
confirmation naming the schema. Keep it — a fresh install is a legitimate need — but make it impossible
to run by accident.

---

## The migration sequence to write

| Id | What | Notes |
|---|---|---|
| `0001_baseline` | Creates `db_migration`, records the starting point | No schema change |
| `0002_character_skills_reuse_delay` | The table created by hand this session, plus the 59-row copy from `character_skill_effects` | Idempotent — already applied live |
| `0003_caravan` | `caravan_faction.sql` + `caravan_ondemand.sql` as one recorded change | Idempotent — already applied live |
| `0004_eco_schema` | `eco_settlement`, `eco_stock`, `eco_indicator`, `eco_txn` | The first genuinely new one |
| `0005_eco_seed` | Settlement rows for the three Gludio-domain towns | Data, idempotent |

After `0003`, the live database and the repository finally agree, and every future change is one
numbered file.

---

## Rules going forward

**Never `DROP` or `TRUNCATE` a player-data table in a migration.** `characters`, `items`, `accounts`,
`clan_data` and their kin are live state. A migration that touches them must `ALTER`.

**Every schema change is a numbered migration file.** No exceptions, including "just one quick column"
— that is precisely how the current mess accumulated.

**Write every migration idempotently.** `CREATE TABLE IF NOT EXISTS`, `INSERT … ON DUPLICATE KEY
UPDATE`, guarded `ALTER`s. Migrations get re-run in practice; assume it.

**`dist/` is canonical, `Deployemnt/` is a copy.** Close the three drifted files by promoting
`Deployemnt`'s corrected versions into `dist/` and copying back, so there is one source of truth. This
is Known Issue 1 in CLAUDE.md, and it stays open until this is done.

**Back up before every apply.** Automatically, in the runner, with no way to skip it.

---

## The economy's own tables

Once the above exists, the economy schema is unremarkable — one migration file creating four tables, as
specified in the [technical design](living-economy-technical-design.md). Its only special requirements:

- **Write-through for money and stock.** `TradeController` today persists stock only on clean shutdown,
  so a power cut restores every shop to full. Do not repeat that.
- **Explicit `qty` and `target` columns**, no `-1` sentinels — the manor's schema shape, not the
  buylist's.
- **Snapshot tables grow forever.** `eco_indicator` and `eco_txn` need a retention policy from day one;
  a migration that prunes rows older than N days is easier to add now than after a million rows.

---

## Order of work

1. `db_migration` table and the runner script — nothing else can be done safely first
2. Baseline plus the two catch-up migrations, so live and repo agree
3. Fix the folder layout and neuter the destructive installer
4. Close the `dist` ↔ `Deployemnt` drift
5. Only then, `0004_eco_schema`

Steps 1 to 4 are roughly a day, touch no game code, and remove a standing risk of total data loss.
