# L2Epic Server Internals — Reverse-Engineered Reference

*Reverse-documented from source on 2026-08-22. Every claim below is traced to a file and line in
`Server/Sorces/head-src/`. Line numbers drift as code changes — treat them as starting points, and
re-grep if one doesn't land where expected.*

Companion documents: [architecture-review.md](architecture-review.md) for project structure and
known defects, [../../CLAUDE.md](../../CLAUDE.md) for onboarding.

---

## Contents

1. [Layer map](#1-layer-map)
2. [Database layer](#2-database-layer)
3. [Low-level networking](#3-low-level-networking-netcore)
4. [Encryption](#4-encryption)
5. [Client↔server protocol](#5-clientserver-protocol)
6. [Async execution and threading](#6-async-execution-and-threading)
7. [World model and visibility](#7-world-model-and-visibility)
8. [Spawning](#8-spawning)
9. [Buffs and effects](#9-buffs-and-effects)
10. [Items and item transfer](#10-items-and-item-transfer)
11. [Mobs, AI and drops](#11-mobs-ai-and-drops)

---

## 1. Layer map

```
        L2 client (Interlude, protocol 740-746)
                    │  TCP :7777, little-endian, 2-byte length header
                    ▼
┌───────────────────────────────────────────────────────────┐
│  netcore — one NIO SelectorThread                         │  com/l2jfrozen/netcore/
│  accept · read · decrypt · frame · write                  │
└───────────────────────────────────────────────────────────┘
                    │  ReceivablePacket
                    ▼
┌───────────────────────────────────────────────────────────┐
│  L2GamePacketHandler — state × opcode → packet class      │  network/
│  211 client packets · 268 server packets                  │
└───────────────────────────────────────────────────────────┘
                    │  dispatched to a thread pool
                    ▼
┌───────────────────────────────────────────────────────────┐
│  Game model — L2Character, L2PcInstance, L2Attackable     │  model/
│  L2World grid · knownlist · effects · inventory           │
└───────────────────────────────────────────────────────────┘
                    │  updateDatabase() / store()
                    ▼
┌───────────────────────────────────────────────────────────┐
│  HikariCP → MariaDB `frozen`                              │  util/database/
└───────────────────────────────────────────────────────────┘
```

Two independent clocks drive everything: the **selector thread** (network I/O) and the **scheduled
pools** (effects, AI, general tasks). Nearly every bug in a server like this comes from work landing
on the wrong one.

---

## 2. Database layer

### Connection pool

`util/database/L2DatabaseFactory.java:30-64` — a single HikariCP pool over the MariaDB driver.

```java
config.setDriverClassName("org.mariadb.jdbc.Driver");
config.setJdbcUrl(Config.DATABASE_URL);
config.addDataSourceProperty("prepStmtCacheSize", "250");
config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("useServerPrepStmts", "true");
config.addDataSourceProperty("rewriteBatchedStatements", "true");
```

**If the pool fails to initialise the process calls `System.exit(1)`** (`:60-63`). No database, no
server — there is no degraded mode.

Statements are server-side prepared and cached, 250 per connection. That matters because the codebase
declares SQL as `static final String` constants (see `L2PcInstance.java:237-265`) — identical SQL text
across all callers, so the cache actually hits.

`getConnection()` at `:103` retries on failure rather than throwing immediately.

### Persistence patterns

Three distinct patterns, and mixing them up is a common source of data loss:

| Pattern | Used by | Trigger |
|---|---|---|
| **Write-through** | items | every mutation calls `updateDatabase()` immediately |
| **Periodic + on-event** | characters | `store()` on logout, teleport, level-up, shutdown |
| **Load-once** | datapack tables | read into memory at startup, never written back |

Character state is written by one enormous statement — `UPDATE_CHARACTER_BY_OBJ_ID`
(`L2PcInstance.java:265`) sets **59 columns** in a single `UPDATE`. Position, stats, class, karma,
clan, colours, punish state: all of it, every save. Cheap to call, but it means a character row is
only ever as fresh as the last `store()`.

`store()` is at `L2PcInstance.java:9117` (with a `force` flag) and `:9143`. It cascades into
`storeEffect()` (`:9288`) and the skills/reuse-delay writers.

### The three classes of table

Covered in detail in the [architecture review](architecture-review.md#3a-the-database). In short:
**datapack** tables (`npc`, `spawnlist`, `droplist`) are regenerated from `dist/gameserver/sql/` and
must be edited there; **`custom_*`** tables hold this server's additions; **player** tables
(`characters`, `items`, `accounts`) are live state with no current backup.

---

## 3. Low-level networking (netcore)

`com/l2jfrozen/netcore/` — 13 classes, a self-contained NIO reactor. Not Netty, not MINA; hand-rolled.

### The selector thread

`SelectorThread.java:24` — `public final class SelectorThread<T extends MMOClient<?>> extends Thread`.
**One thread** handles every socket for the whole server.

```java
private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;   // :28
private static final int HEADER_SIZE = 2;                              // :30
```

Every packet is framed by a **2-byte little-endian length prefix**, inclusive of the header itself.

Main loop at `:129`, with a fixed cycle:

```
run()  ──▶ finishConnection(:208)   pending connects
       ──▶ acceptConnection(:230)   new sockets, filtered by IPv4Filter
       ──▶ readPacket(:259)         ──▶ tryReadPacket(:346)
                                    ──▶ parseClientPacket(:437)
       ──▶ writePacket              drains send queues
       ──▶ sleep(SLEEP_TIME)
```

### Buffers

Three long-lived buffers plus a pool (`:45-51`):

| Buffer | Role |
|---|---|
| `DIRECT_WRITE_BUFFER` | off-heap staging for socket writes |
| `WRITE_BUFFER` | packet serialisation |
| `READ_BUFFER` | incoming bytes |
| `bufferPool` (`FastList<ByteBuffer>`) | per-connection buffers, recycled |
| `STRING_BUFFER` (`NioNetStringBuffer`) | reusable UTF-16 decode target |

The string buffer is the interesting one: L2 sends strings as null-terminated UTF-16LE, and decoding
them naively would allocate on every packet. `NioNetStringBuffer` is reused instead.

### Throttles

`SelectorConfig`, populated from `NetcoreConfig` (`config/protected/mmocore.properties`):

- `MAX_READ_PER_PASS` — packets read per connection per cycle
- `MAX_SEND_PER_PASS` — packets written per connection per cycle
- `SLEEP_TIME` — selector idle sleep
- `HELPER_BUFFER_COUNT` / `HELPER_BUFFER_SIZE` — pool sizing

Raising `SLEEP_TIME` lowers CPU and raises latency. The per-pass caps prevent one flooding client
from starving the rest — the loop moves on and picks it up next cycle.

Closes are deferred through `pendingClose` (`NioNetStackList<MMOConnection<T>>`, `:53`) so a socket is
never torn down mid-iteration.

---

## 4. Encryption

### Game server — XOR stream cipher

`network/GameCrypt.java`. Deliberately weak and very fast: a 16-byte rolling key, XORed byte by byte,
with each ciphertext byte feeding the next.

```java
for (int i = 0; i < size; i++) {
    final int temp2 = raw[offset + i] & 0xFF;
    raw[offset + i] = (byte) (temp2 ^ gcrypt.inKey[i & 15] ^ temp);
    temp = temp2;                       // chaining
}
```

After each packet, **bytes 8–11 of the key are reinterpreted as a little-endian int, incremented by
the packet size, and written back**:

```java
int old = gcrypt.inKey[8] & 0xff;
old |= gcrypt.inKey[9]  << 8    & 0xff00;
old |= gcrypt.inKey[10] << 0x10 & 0xff0000;
old |= gcrypt.inKey[11] << 0x18 & 0xff000000;
old += size;
```

So the key evolves with total bytes transferred, and **the two directions keep separate keys**
(`inKey`, `outKey`). Client and server must stay byte-for-byte in step — one desync and every
subsequent packet decodes to garbage.

**A quirk worth knowing:** `encrypt()` checks `isEnabled` and, if false, *sets it true and returns
without encrypting*. The first outbound packet goes out in plaintext by design — it is the one
carrying the key.

### Login server — real crypto

`com/l2jfrozen/crypt/` — `BlowfishEngine`, `NewCrypt`, `ScrambledKeyPair` (RSA), `LoginCrypt`. Account
credentials get genuine protection; the in-game stream does not. `nProtect.java` is the GameGuard hook.

---

## 5. Client↔server protocol

### Connection state machine

`network/L2GameClient.java:64-68`:

```java
public static enum GameClientState { CONNECTED, AUTHED, IN_GAME }
```

- **CONNECTED** — socket open, nothing proven. Misbehaviour gets an immediate kick (`:997`).
- **AUTHED** — login handed off a valid session, no character selected.
- **IN_GAME** — character in the world.

### Dispatch

`network/L2GamePacketHandler.java:108` switches on **state first, then opcode** — the same opcode
means different things in different states, and a packet valid only in `IN_GAME` cannot be reached
from `CONNECTED`. That is the protocol's main security boundary.

```java
switch (state) {
    case CONNECTED: switch (opcode) { case 0x00: ... case 0x08: ... }   // :110
    case AUTHED:    switch (opcode) { case 0x09: ... }                  // :124
    case IN_GAME:   switch (opcode) { case 0x01: 0x03: 0x04: 0x09: }    // :155
}
```

**211 client packet classes** in `network/clientpackets/`, **268 server packet classes** in
`network/serverpackets/`.

Each client packet is a `ReceivablePacket` with `readImpl()` (parse) and `runImpl()` (act). Parsing
happens on the selector thread; execution is handed to a pool via `execute()` (`:988`).

### Flood protection

`network/ClientStats.java` tracks a rolling window per client:

| Check | Config key |
|---|---|
| packets in one second (`:208`) | `CLIENT_PACKET_QUEUE_MAX_PACKETS_PER_SECOND` |
| floods per minute (`:167`) | `..._MAX_FLOODS_PER_MIN` |
| rolling average (`:172`) | `..._MAX_AVERAGE_PACKETS_PER_SECOND` |

Recovery is hysteretic (`:188`): once flagged, a client must drop below **half** the limit before the
flag clears — preventing oscillation at the threshold. Tuned in `config/protected/flood.properties`
and `packets.properties`.

---

## 6. Async execution and threading

`gameserver/thread/ThreadPoolManager.java:92-116` — **six pools**, deliberately separated so one
subsystem stalling cannot freeze another.

| Pool | Type | Default size | Config key | Carries |
|---|---|---|---|---|
| `effectsScheduledThreadPool` | Scheduled | 6 | `ThreadPoolSizeEffects` | buff ticks, HP/MP regen |
| `generalScheduledThreadPool` | Scheduled | 15 | `ThreadPoolSizeGeneral` | respawns, timers, most game logic |
| `aiScheduledThreadPool` | Scheduled | `AI_MAX_THREAD` | — | NPC AI think cycles |
| `ioPacketsThreadPool` | Cached | 2 core, **unbounded max** | `UrgentPacketThreadCoreSize` | urgent packets |
| `generalPacketsThreadPool` | Cached | 4 core, +2 max | `GeneralPacketThreadCoreSize` | normal packets |
| `generalThreadPool` | Cached | 4 core, +2 max | `GeneralThreadCoreSize` | fire-and-forget work |

Defaults are in `config/functions/developer.properties`.

Two design details:

**Packet pools run at elevated priority.** `Thread.NORM_PRIORITY + 1` for both I/O and packet pools
(`:113-114`) — responsiveness to players outranks background work.

**The I/O pool is unbounded** (`Integer.MAX_VALUE` max, `:113`) with a 5-second keep-alive. It will
grow a thread rather than queue an urgent packet. The general pools cap at core+2 and let work queue.

### Scheduling API

```java
ThreadPoolManager.getInstance().scheduleGeneral(task, delay);
ThreadPoolManager.getInstance().scheduleEffect(task, delay);
ThreadPoolManager.getInstance().scheduleAiAtFixedRate(task, delay, period);
ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(task, initial, period);
```

Pick the pool that matches the work. Putting a long database write on the effects pool delays every
buff tick on the server.

### TaskManager

`gameserver/taskmanager/` is a separate, DB-backed scheduler for *recurring global* jobs — distinct
from the pools above. `TaskTypes` supports fixed-delay, daily and startup schedules; `TaskManager`
persists them so they survive restarts. Alongside it sit dedicated managers with their own cadence:

- `DecayTaskManager` — corpse decay
- `AttackStanceTaskManager` — combat-stance timeout
- `KnownListUpdateTaskManager` — visibility refresh (see §7)
- `ExclusiveTask` — a task that cannot overlap itself

---

## 7. World model and visibility

### The region grid

`model/L2World.java:37-62`:

```java
public static final int SHIFT_BY = 12;              // 2^12 = 4096 world units per region
public static final int MAP_MIN_X = Config.WORLD_SIZE_MIN_X;   // -131072
public static final int MAP_MAX_X = Config.WORLD_SIZE_MAX_X;   //  228608
public static final int MAP_MIN_Y = Config.WORLD_SIZE_MIN_Y;   // -262144
public static final int MAP_MAX_Y = Config.WORLD_SIZE_MAX_Y;   //  262144
```

The world is a 2D array of **4096×4096-unit regions**. `OFFSET_X/Y` shift the negative coordinate
space into positive array indices. Objects register with their region; proximity queries scan only
that region and its neighbours, never the whole world.

### Knownlist — who can see whom

`model/actor/knownlist/CharKnownList.java`. Every character keeps a map of objects currently within
its awareness radius:

```java
getKnownPlayers()                  // :164  Map<Integer, L2PcInstance>
getKnownPlayersInRadius(radius)    // :184
```

`KnownListUpdateTaskManager` refreshes these periodically. **This is the server's core scalability
mechanism** — nothing iterates all online players.

### Broadcasting

`model/L2Character.java:490-515`:

```java
sendPacket(mov);                                            // :490 to self
for (final L2PcInstance player : getKnownList().getKnownPlayers().values())
    player.sendPacket(mov);                                 // :515 to observers
```

A server→client update goes only to clients whose knownlist contains the actor. Consequence worth
internalising: **if a player isn't in your knownlist, they will never see your action** — the usual
cause of "my custom effect is invisible to others".

---

## 8. Spawning

### Reflection-based instantiation

`model/spawn/L2Spawn.java:170` — the single most important line for anyone adding NPCs:

```java
constructor = Class.forName(
    "com.l2jfrozen.gameserver.model.actor.instance." + implementationName + "Instance"
).getConstructor(parameters);
```

The `type` column in `npc` / `custom_npc` is concatenated with `"Instance"` and resolved by name.
Type `L2Guard` → class `L2GuardInstance`.

**A type with no matching class throws `ClassNotFoundException` and aborts spawn table loading** —
not just that NPC, the whole pass. This is exactly [Known Issue 3](../../CLAUDE.md#known-issues):
npc `100204` "Soldier" typed `L2EscortGard` with no `L2EscortGardInstance` class.

Constructor signature is fixed: `(int objectId, L2NpcTemplate template)`.

### Respawn cycle

```
NPC dies ──▶ decreaseCount() (:390)
             │  guard: doRespawn && scheduledCount + currentCount < maximumCount
             ▼
          ThreadPoolManager.scheduleGeneral(new SpawnTask(oldNpc), respawnDelay)   // :397
             ▼
          respawnNpc(oldNpc) (:103) ──▶ doSpawn() (:459)
```

Fields: `respawnDelay` (`:68`), `respawnMinDelay` (`:71`), `respawnMaxDelay` (`:74`) — min/max give
randomised respawn windows. The guard at `:390` is explicitly a **lag defence**: without it, two
death events could each queue a respawn and duplicate the mob.

`SpawnListener` allows hooking every spawn — useful for events without patching `L2Spawn`.

---

## 9. Buffs and effects

### Effect lifecycle

`model/L2Effect.java` — a three-state machine (`:37-38`):

```
SCHEDULED ──▶ ACTING ──▶ FINISHING
```

Subclasses implement three hooks:

| Method | When | Line |
|---|---|---|
| `onStart()` | effect applied | — |
| `onActionTime()` | each tick; **return false to end the effect** | `:449` |
| `onExit()` | expiry or removal | `:437` |

Timing runs on the effects pool:

```java
currentFuture = ThreadPoolManager.getInstance().scheduleEffect(currentTask, duration * 1000);        // :239
currentFuture = ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(currentTask, delay, rate); // :350
```

One-shot effects use `scheduleEffect`; periodic ones (poison, regen) use the fixed-rate variant.
`rescheduleEffect()` (`:451`) re-arms only while `ACTING` — a guard against resurrecting a finished
effect.

### Slot limits

`Config.java:858-859, 968-969`:

```java
BUFFS_MAX_AMOUNT   = Byte.parseByte(altSettings.getProperty("MaxBuffAmount", "24"));
DEBUFFS_MAX_AMOUNT = Byte.parseByte(altSettings.getProperty("MaxDebuffAmount", "6"));
```

**This server runs 40 buff / 10 debuff slots.** Note the type: `byte`. Values above 127 overflow.

Enforcement at `model/L2Character.java:3512-3524` — on overflow the **first** (oldest) buff is
removed, FIFO. The condition is narrower than it looks:

```java
if (getBuffCount() >= getMaxBuffCount() && !doesStack(tempskill)
    && (type == BUFF || type == REFLECT || type == HEAL_PERCENT || type == MANAHEAL_PERCENT)
    && !(tempskill.getId() > 4360 && tempskill.getId() < 4367))
```

Skill IDs **4361–4366 are exempt** — those are dances and songs, which have their own accounting.
Effects that stack (`doesStack`) replace rather than consume a slot.

### Persistence

`L2PcInstance.java:237-243` — buffs and cooldowns are saved across logout:

```java
SELECT skill_id,skill_level,effect_count,effect_cur_time
  FROM character_skills_save WHERE char_obj_id=? ORDER BY buff_index ASC
INSERT INTO character_skills_reuse_delay (char_obj_id,skill_id,skill_level,reuse_delay,systime,class_index)
```

`ORDER BY buff_index ASC` preserves buff *order*, which matters because eviction is FIFO.

> **Live defect.** The current code targets `character_skills_save` and
> `character_skills_reuse_delay`. The `frozen` database has the former but **not** the latter, so
> `restoreEffects` throws on login. The 13:51 log line naming `character_skill_effects` came from the
> older jar; after the 14:11 redeploy the only failure is the missing reuse-delay table. See
> [Known Issue 1](../../CLAUDE.md#known-issues).

---

## 10. Items and item transfer

### Identity and location

Every item is a distinct world object with a unique `object_id` — even stacked ones share a row, not
an identity. `model/actor/instance/L2ItemInstance.java:58-68`:

```java
public static enum ItemLocation {
    VOID, INVENTORY, PAPERDOLL, WAREHOUSE, CLANWH, PET, PET_EQUIP, LEASE, FREIGHT
}
```

An item moves between containers by changing `ownerId` + `ItemLocation` — it is never destroyed and
recreated. `loc_data` disambiguates within a location (equipped slot, pet id, warehouse slot).

Table (`dist/gameserver/sql/install/items.sql`):

```sql
CREATE TABLE `items` (
  `owner_id` INT,                 -- player or clan object id
  `object_id` INT NOT NULL DEFAULT 0,
  `item_id` INT, `count` INT, `enchant_level` INT,
  `loc` VARCHAR(10), `loc_data` INT,
  `price_sell` INT, `price_buy` INT, `time_of_use` INT,
  `custom_type1` INT DEFAULT 0, `custom_type2` INT DEFAULT 0,
  `mana_left` decimal(3,0) NOT NULL default -1,
  PRIMARY KEY (`object_id`),
  KEY `key_owner_id` (`owner_id`), KEY `key_loc` (`loc`), ...
);
```

`loc` is a **VARCHAR of the enum name**, not an int — readable in HeidiSQL, but string-compared on
every load.

### Container operations

`model/ItemContainer.java` is the base for `PcInventory`, `PcWarehouse`, `ClanWarehouse`, `PetInventory`:

| Method | Line |
|---|---|
| `addItem(process, item, actor, reference)` | `:168` |
| `addItem(process, itemId, count, actor, reference)` | `:228` |
| `transferItem(process, objectId, count, target, actor, reference)` | `:348` |
| `destroyItem(...)` | — |

The `process` string is an audit tag ("Trade", "Drop", "Consume") that flows into item logging.

**Every mutation writes through immediately** — `item.updateDatabase()` appears at `:181, :190, :195,
:209, :244, :249, :282, :409, :412`. A transfer updates both sides (`:409` source, `:412` target).
There is no deferred flush, so an item is never lost to a crash — at the cost of a DB round trip per
operation.

`updateDatabase()` (`L2ItemInstance.java:966`) dispatches to `insertIntoDb()` (`:1221`) or
`updateInDb()` (`:1172`).

### Transfer paths

| Path | Implementation |
|---|---|
| Player trade | `model/TradeList.java` — mutual confirm, then atomic exchange |
| Drop / pickup | `L2ItemInstance` → world, `DropProtection` gates who may take it |
| Warehouse | `transferItem` with `ItemLocation.WAREHOUSE` / `CLANWH` |
| Shop | `merchant_buylists` + `TradeController` |
| Mail / freight | `ItemLocation.FREIGHT` |

`TradeList` holds `TradeItem` snapshots (`:26-56`) — item, count, price — captured at offer time and
**re-validated against live inventory before the exchange** (`getAvailableItems`, `:206`). That
re-check is what blocks the classic dupe of offering an item and moving it before confirming.

---

## 11. Mobs, AI and drops

### Intention system

`ai/CtrlIntention.java` — NPC and player AI both run on ten intentions:

```
AI_INTENTION_IDLE · ACTIVE · REST · ATTACK · CAST
MOVE_TO · FOLLOW · PICK_UP · INTERACT · MOVE_TO_IN_A_BOAT
```

AI is event-driven (`onEvtThink`, `onEvtAttacked`, `onEvtDead`) rather than polled — but with a
**1-second heartbeat** (`ai/L2AttackableAI.java:298`):

```java
aiTask = ThreadPoolManager.getInstance().scheduleAiAtFixedRate(this, 1000, 1000);
```

So mob reaction granularity is 1 second. `onEvtThink()` (`:1030`) branches to `thinkActive()`
(`:397` — idle, wander, look for targets) or `thinkAttack()` (`:631` — pursue, strike, use skills).

### Death and reward

`model/L2Attackable.java:483` — `calculateRewards(lastAttacker)` runs XP/SP distribution, then at
`:566`:

```java
doItemDrop(maxDealer != null && maxDealer.isOnline() ? maxDealer : lastAttacker);
```

**Loot ownership goes to the highest damage dealer**, not the killer — falling back to the last
attacker if the top dealer went offline.

### Drop chance

`calculateRewardItem()` (`:1106`) is where every rate multiplier lands. Base chance comes from
`droplist.chance` on a **1,000,000 scale**.

Category multipliers (`:1215-1262`) — note each splits three ways by mob class:

| Drop type | Raid | Boss | Minion | Normal |
|---|---|---|---|---|
| Adena | `ADENA_RAID` | `ADENA_BOSS` | `ADENA_MINON` | `RATE_DROP_ADENA` |
| Spoil | `SPOIL_RAID` | `SPOIL_BOSS` | `SPOIL_MINON` | `RATE_DROP_SPOIL` |
| Items | `ITEMS_RAID` | `ITEMS_BOSS` | `ITEMS_MINON` | `RATE_DROP_ITEMS` |

Several boss accessories bypass the table entirely with hardcoded chances (`:1132-1172`) —
`CORE_RING_CHANCE`, `ORFEN_EARRING_CHANCE`, `ZAKEN_EARRING_CHANCE`, `QA_RING_CHANCE`.

**Deep Blue penalty** (`:1184-1207`), when `DEEPBLUE_DROP_RULES` is on:

```java
dropChance = (drop.getChance() - drop.getChance() * levelModifier / 100) / deepBlueDrop;
```

Killing far-below-level mobs scales drops down — the anti-farming rule. `:1194` shows the divisor
itself scaling with `RATE_DROP_ITEMS`, so raising drop rates partly offsets the penalty.

### Loot delivery

`doItemDrop()` (`:1705`) chooses per item:

- **`Config.AUTO_LOOT`** (`:1803`) — straight to inventory. **On for this server.**
- **`AUTO_LOOT_BOSS`** (`:1815`) — raid/grand boss drops are gated separately; without it they hit
  the ground even with autoloot on.
- **`AUTO_LOOT_HERBS`** (`:1865+`) — herbs handled independently again.
- Otherwise the item spawns on the ground under `DropProtection`, reserved for the owner until it
  expires.

Autoloot still respects `validateCapacity` (`:1815`) — a full inventory drops to ground rather than
silently voiding the item.

---

## Cross-cutting notes

**Where does my code belong?**

| Work | Thread | Why |
|---|---|---|
| Packet reaction | packet pool (automatic) | keep the selector thread free |
| Buff tick | effects pool | isolated from game logic stalls |
| NPC behaviour | AI pool | 1s granularity is inherent |
| Respawn, timers | general scheduled | the default |
| DB write | general pool | **never** on effects or AI |

**Three invariants worth remembering:**

1. An NPC `type` with no `<type>Instance` class **aborts all spawn loading** — not just that NPC.
2. Anything not in a player's knownlist is invisible to them, no matter what you broadcast.
3. Item mutations write through to the database immediately; character state does not.
