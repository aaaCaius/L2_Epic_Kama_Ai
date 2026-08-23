# Living Aden — technical design

> Companion to [living-economy-concept.md](living-economy-concept.md). That document says what the
> world does; this one says how the code does it.
>
> Scope: **full mechanical detail for stages 0 and 1**, plus the algorithms and integration map the
> later stages build on. Every file path and line number below was checked against this tree.

---

## Two findings that shape everything

### 1. Finite stock needs no change to the purchase path

`L2TradeList` is a plain public class and `decreaseCount(int, int)` is `public synchronized`
(`model/L2TradeList.java:11,77`). `RequestBuyItem` calls it polymorphically, and its merchant gate at
`RequestBuyItem.java:93` accepts **any subclass** of `L2MerchantInstance`.

So a subclass gets the hook for free:

```java
public class EcoTradeList extends L2TradeList
{
    private final int settlementId;

    @Override
    public boolean countDecrease(final int itemId)
    {
        return true;                       // economy stock is always finite
    }

    @Override
    public synchronized boolean decreaseCount(final int itemId, final int count)
    {
        final Settlement s = SettlementManager.getInstance().get(settlementId);
        if (s == null || !s.reserveStock(itemId, count))
            return false;                  // sold out - client is told, nothing is charged

        if (!super.decreaseCount(itemId, count))
        {
            s.releaseStock(itemId, count); // keep the mirror honest
            return false;
        }
        return true;
    }
}
```

Register these lists in `TradeController` under a reserved shop-id range at boot, and
`showBuyWindow` (`L2MerchantInstance.java:92`) and `RequestBuyItem` both work **unmodified**. The
Interlude client already renders remaining counts and hides sold-out lines (`BuyList.java:57-69`), so
there is no client work either.

### 2. But there is one real bug we must fix first

`RequestBuyItem` **charges the player before it checks stock**:

| Line | What happens |
|---|---|
| `RequestBuyItem.java:311` | `player.reduceAdena("Buy", subTotal + tax, ...)` |
| `RequestBuyItem.java:339` | `list.countDecrease(itemId)` |
| `RequestBuyItem.java:341` | `list.decreaseCount(itemId, count)` → **can fail here** |
| `RequestBuyItem.java:351` | `player.getInventory().addItem("Buy", ...)` |

If stock runs out between display and purchase, **the player pays and receives nothing.**

This is pre-existing and already affects the 1,729 limited-stock rows in `merchant_buylists` today, but
it is rare enough there to go unnoticed. Once every shelf is finite it becomes constant, and players
lose adena to it daily.

**Fix it before anything depends on finite stock**: move the stock decrement above the adena charge, and
refund on failure. Roughly ten lines, a genuine bug fix that also repairs existing behaviour, and the
**only invasive change in stage 0 and 1.**

A second latent bug in the same area: `L2TradeList.decreaseCount` returns `true` when the item id is not
in the list at all — the loop simply falls through (`L2TradeList.java:77-96`). `EcoTradeList` tightens
this by checking its own stockpile first.

---

## The mechanics

### The cycle

One `EconomyTickManager` on the general pool, following the manor's proven shape
(`CastleManorManager.java:302`):

```java
ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new EconomyTask(), firstDelay, 60000);
```

The task fires every **60 seconds**, but a *cycle* — consumption, evaluation, restock — runs every
`CycleMinutes` (config, default **60**). The minute tick exists so the manager can do cheap work often
(price drift, contract expiry) and expensive work rarely.

**Consumption scales with player activity**, so a quiet server does not starve:

```
activity = clamp(playersOnlineInRegion / ExpectedPlayers, MinActivity, 1.0)
```

with `MinActivity` default `0.35` — the world keeps breathing when nobody is watching, but slowly.

### Consumption and fulfilment

Per cycle, for each unlocked need `N`:

```
required(N)   = population × rate(N, tier) × activity
available(N)  = Σ stock of every item serving N
consumed(N)   = min(required, available)
fulfilment(N) = consumed / required          →  [0 … 1]
```

Items serving a need are drained **cheapest first**, so luxuries are not eaten as staples.

### Tier

```
overall = Σ weight(N) × fulfilment(N)  /  Σ weight(N)      over unlocked needs

tier up    when overall ≥ TierUpThreshold   (0.90) for TierUpCycles   (12) consecutively
              and sustainedSupply ≥ cost(tier + 1)
tier down  when overall < TierDownThreshold (0.50) for TierDownCycles (3)  consecutively
```

`cost(tier) = TierBaseCost × TierGrowth^tier`, `TierGrowth` default **2.8**.

Asymmetric on purpose: **twelve good cycles to rise, three bad ones to fall.** Prosperity is earned
slowly and lost fast, which is what makes a raid feel like it mattered.

The starter settlement floors at tier 2 (`TierFloor` per settlement).

### Satisfaction

```
base        = 100 × overall
L           = luxuryConsumed / population
bonus       = LuxuryAmplitude × (L / LuxuryOptimum) × (2 − L / LuxuryOptimum)
satisfaction = clamp(base + bonus, 0, 100)
output       = clamp(0.75 + satisfaction / 100, 0.5, 1.25)
```

Defaults: `LuxuryAmplitude = 25`, `LuxuryOptimum = 1.0`. Past twice the optimum `bonus` goes negative on
its own — no special case needed.

**The feedback loop:** when `L > LuxuryOptimum`, medicine demand rises by
`SicknessFactor × (L − LuxuryOptimum)`. A town drinking too much visibly starts needing medicine, which
surfaces on the board as a run the players can diagnose.

### Prices

Both directions move with scarcity, from the item's own reference price:

```
scarcity(N)  = 1 − fulfilment(N)

townBuys  = base × (1 + BuyScarcityK  × scarcity)      capped ×BuyCap   (default 4.0)
townSells = base × (1 + SellScarcityK × scarcity) × (1 + taxRate)
```

Two hard bounds on buying, both essential:

- Never spend the treasury below `TreasuryReserve` — the town must always afford wages and the garrison
- Never buy a good already at or above its target stock — no infinite sink

A town that is broke or full simply **stops buying**. Prices fall, and players sell elsewhere. That is
the graceful degradation the concept depends on.

---

## Data model

Follows the manor's shape — an explicit current/target pair, no `-1` sentinels.

```sql
CREATE TABLE `eco_settlement` (
  `settlement_id` INT          NOT NULL,          -- castle id from MapRegionTable.getAreaCastle
  `name`          VARCHAR(40)  NOT NULL,
  `tier`          TINYINT      NOT NULL DEFAULT 1,
  `population`    INT          NOT NULL DEFAULT 0,
  `treasury`      BIGINT       NOT NULL DEFAULT 0,
  `satisfaction`  SMALLINT     NOT NULL DEFAULT 50,
  `tier_floor`    TINYINT      NOT NULL DEFAULT 1,
  `last_cycle`    BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`settlement_id`)
);

CREATE TABLE `eco_stock` (
  `settlement_id` INT NOT NULL,
  `item_id`       INT NOT NULL,
  `qty`           INT NOT NULL DEFAULT 0,
  `target`        INT NOT NULL DEFAULT 0,          -- desired holding; drives buy/stop-buy
  PRIMARY KEY (`settlement_id`,`item_id`)
);

CREATE TABLE `eco_indicator` (                     -- periodic snapshots, for tuning
  `ts`            BIGINT   NOT NULL,
  `settlement_id` INT      NOT NULL,
  `tier`          TINYINT  NOT NULL,
  `population`    INT      NOT NULL,
  `treasury`      BIGINT   NOT NULL,
  `satisfaction`  SMALLINT NOT NULL,
  `fulfilment`    SMALLINT NOT NULL,               -- ×100
  KEY `k_ts` (`ts`), KEY `k_set` (`settlement_id`,`ts`)
);

CREATE TABLE `eco_txn` (                           -- transaction telemetry
  `ts`            BIGINT      NOT NULL,
  `kind`          VARCHAR(16) NOT NULL,            -- buy | sell | contract | export | site_tax
  `settlement_id` INT         NOT NULL,
  `char_id`       INT         NOT NULL DEFAULT 0,
  `item_id`       INT         NOT NULL DEFAULT 0,
  `qty`           INT         NOT NULL DEFAULT 0,
  `adena`         BIGINT      NOT NULL DEFAULT 0,
  KEY `k_ts` (`ts`), KEY `k_kind` (`kind`,`ts`)
);
```

**Persistence strategy**, copying the manor's configurable approach: money and stock **write through on
every transaction**; tier, population and satisfaction save at cycle end; everything flushes on
shutdown. Never shutdown-only — that is exactly how `TradeController` loses all its stock on a power
cut today.

**Keying:** `settlement_id` is the castle id from `MapRegionTable.getAreaCastle`
(`datatables/csv/MapRegionTable.java:198`). Three overlapping town maps exist in this tree and they
disagree; use this one everywhere and never mix.

---

## Code changes

### New files — stage 0 and 1

```
com/l2jfrozen/gameserver/economy/
  EconomyConfig.java              load + reload config/economy/economy.properties
  model/Settlement.java           tier, population, treasury, satisfaction, stock ops
  model/Stockpile.java            per-settlement item quantities, reserve/release
  model/NeedDef.java              a need and the items that serve it
  model/ResourceDef.java          a resource: band, items, serves, weight, base value
  managers/SettlementManager.java load at boot, in-memory, write-through, lookup by region
  managers/EconomyTickManager.java the 60s task and the cycle
  datatables/ResourceTable.java   data/economy/resources.xml
  datatables/NeedTable.java       data/economy/needs.xml
  datatables/TierTable.java       data/economy/tiers.xml
  trade/EcoTradeList.java         L2TradeList subclass - the stock hook
  instance/L2EcoMerchantInstance.java   L2MerchantInstance subclass
  telemetry/EconomyLog.java       eco_txn + eco_indicator writers
handler/admincommandhandlers/AdminEconomy.java
```

The XML loaders follow `datatables/xml/RecipeTable.java:40-75` exactly — `DocumentBuilderFactory`,
`getElementsByTagName`, attribute reads. Database access follows
`managers/CastleManorManager.java:186-274` — `L2DatabaseFactory.getInstance().getConnection()`,
`PreparedStatement`, `DatabaseUtils.close(statement)`, `CloseUtil.close(con)`.

### Modified files — the entire list

Six files, and only one of them is a behaviour change:

| File | Change | Size |
|---|---|---|
| `GameServer.java:300` | Init the manager under the existing `Util.printSection("Economy")` — the section already exists | 1 line |
| `handler/AdminCommandHandler.java:94+` | `registerAdminCommandHandler(new AdminEconomy())` | 1 line |
| `handler/admincommandhandlers/AdminReload.java:96` | `else if (type.startsWith("economy"))` branch | 4 lines |
| `Config.java` | `loadEconomyConfig()` alongside the other 25 loaders | ~20 lines |
| `datatables/sql/ItemTable.java:490` | `case "material":` — currently falls through to `OTHER`, so `L2EtcItemType.MATERIAL` is never assigned anywhere despite 654 items carrying it | 1 line |
| `network/clientpackets/RequestBuyItem.java:311-351` | **The ordering fix.** Decrement stock before charging; refund on failure | ~10 lines |

Everything else is new code in its own package, behind `EnableEconomy`. With the flag off, the only
reachable change is the `RequestBuyItem` fix — which is a strict improvement on its own.

### Data and config files

```
Deployemnt/gameserver/config/economy/economy.properties     every tunable number
Deployemnt/gameserver/data/economy/resources.xml            the resource catalogue
Deployemnt/gameserver/data/economy/needs.xml                need → items, rates per tier
Deployemnt/gameserver/data/economy/tiers.xml                costs, thresholds, unlocked services
Server/Sorces/dist/gameserver/sql/customs/eco_schema.sql    the four tables
```

Mirror config and data into `dist/` — Known Issue 2 in CLAUDE.md is exactly this drift, and a full
build would otherwise revert them.

### Shape of the config

```properties
EnableEconomy            = False     # the kill switch
CycleMinutes             = 60
ExpectedPlayers          = 20
MinActivity              = 0.35
TierGrowth               = 2.8
TierBaseCost             = 1000
TierUpThreshold          = 0.90
TierUpCycles             = 12
TierDownThreshold        = 0.50
TierDownCycles           = 3
LuxuryOptimum            = 1.0
LuxuryAmplitude          = 25
SicknessFactor           = 0.5
BuyScarcityK             = 3.0
BuyCap                   = 4.0
SellScarcityK            = 1.5
TreasuryReserve          = 500000
SaveAllActions           = True
```

Every one of these is reachable through `//reload economy`. **No economic constant lives in Java.**

### Admin commands

```
//eco                          summary of every settlement
//eco <id>                     full state: tier, population, treasury, satisfaction, stock, prices
//eco set <id> <field> <value> force a value
//eco stock <id> <item> <qty>  set a stockpile line
//eco cycle <id>               run one cycle immediately
//eco snapshot                 write an indicator row now
```

The forcing commands are what make a starving town or a maxed city testable in a minute rather than
across days of play.

---

## Integration map

Where the economy touches the existing server, and how:

| Existing code | How we attach | Invasive? |
|---|---|---|
| `L2MerchantInstance.showBuyWindow:92` | subclass; inherited unchanged | no |
| `RequestBuyItem` merchant gate `:93` | accepts any `L2MerchantInstance` subclass | no |
| `TradeController.getBuyListByNpcId` | register `EcoTradeList` under a reserved shop-id range | no |
| `L2TradeList.decreaseCount:77` | override in `EcoTradeList` | no |
| `BuyList` packet `:57-69` | already renders finite counts | no |
| `RequestBuyItem:311` adena ordering | **fix the pre-existing bug** | **yes — the only one** |
| `L2Spawn:170` type reflection | new NPC types resolve by `type` column, no code change | no |
| `ItemContainer.addItem(..., null, null)` | headless item movement, proven at `CastleManorManager.java:352` | no |
| `HtmCache` (LazyCache = True) | all UI is `.htm`, editable with no restart | no |

---

## Build order for stage 0

1. **`RequestBuyItem` ordering fix**, deployed and verified on its own. It stands alone as a bug fix and
   everything else assumes it.
2. **Telemetry** — `eco_txn`, `EconomyLog`, and the writes from the existing buy and sell paths. No
   gameplay change. Let it run a fortnight and answer *what does a player actually produce per hour?*
3. **`ItemTable` material fix** — one line, gives a raw-material predicate over 654 items.
4. **Config, schema, data files** and their loaders, with `EnableEconomy = False`.
5. **`SettlementManager` + `Settlement`**, populated for the three Gludio-domain towns.
6. **`AdminEconomy`** — because nothing after this is testable without it.
7. **`EconomyTickManager`**, consumption and tier only. Still no player-facing surface.

Stage 1 then adds `EcoTradeList`, `L2EcoMerchantInstance` and the citizen crowd on top of a model that
is already ticking and already inspectable.

---

## Deployment reminders

Every Java change here means: build the core, copy `l2jfrozen-core.jar` into **both**
`Deployemnt/gameserver/lib/` and `Deployemnt/loginserver/lib/`, restart both servers.
`AI_Tools/scripts/start-servers.ps1` handles the restart half.

Config, `.xml`, `.htm` and SQL changes do **not** need a build — which is exactly why every tunable
number belongs in them.
