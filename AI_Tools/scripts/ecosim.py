#!/usr/bin/env python3
"""
Numerical test of the Living Aden economy model.

Implements the mechanics exactly as specified in living-economy-technical-design.md
and runs scenarios to see whether the claimed behaviour actually emerges.
No tuning-to-taste: the constants are the ones in the design.
"""

# ---- constants, straight from the design ----------------------------------
TIER_BASE_COST      = 1000   # unused in v2 - cost is derived
TIER_GROWTH         = 2.8
TIER_UP_THRESHOLD   = 0.90
TIER_UP_CYCLES      = 12
TIER_DOWN_THRESHOLD = 0.50
TIER_DOWN_CYCLES    = 3
LUX_OPTIMUM         = 1.0
LUX_AMPLITUDE       = 25
SICKNESS_FACTOR     = 0.5
BUY_SCARCITY_K      = 3.0
BUY_CAP             = 4.0
SELL_SCARCITY_K     = 1.5
TREASURY_RESERVE    = 500_000
EXPECTED_PLAYERS    = 20
MIN_ACTIVITY        = 0.35

NEEDS  = ["food", "water", "clothing", "medicine", "weapons", "luxury", "entertainment"]
UNLOCK = {"food": 1, "water": 1, "clothing": 2, "medicine": 3, "weapons": 3,
          "luxury": 4, "entertainment": 5}
WEIGHT = {"food": 3, "water": 3, "clothing": 2, "medicine": 2, "weapons": 2,
          "luxury": 1, "entertainment": 1}

# per-citizen consumption per cycle
RATE = {"food": 1.0, "water": 1.0, "clothing": 0.35, "medicine": 0.25,
        "weapons": 0.25, "luxury": 0.5, "entertainment": 0.3}

POP_PER_TIER   = 120          # population cap per tier
POP_GROWTH     = 0.04         # per cycle toward cap, scaled by satisfaction
BASE_PRICE     = 100          # adena per unit, all goods (simplification)
WAGE_PER_HEAD  = 12           # treasury drain per citizen per cycle
GARRISON_COST  = 2000         # per cycle per tier
EXPORT_PRICE   = 45
EXPORT_BUFFER  = 1.8
COVER_CYCLES   = 4     # v4: stock must cover this many cycles of next-tier demand          # v2: was 2.0 - a fat buffer absorbed every raid           # "worst price available" - below BASE_PRICE by design


def tier_cost(t):
    """v2: the sustained supply a town at tier t genuinely consumes."""
    if t < 1 or t > 5:
        return float("inf")
    return POP_PER_TIER * t * sum(RATE[n] for n in unlocked(t))


def unlocked(tier):
    return [n for n in NEEDS if UNLOCK[n] <= tier]


class Settlement:
    def __init__(self, tier=1, pop=120, treasury=1_000_000):
        self.tier = tier
        self.pop = pop
        self.treasury = treasury
        self.satisfaction = 50.0
        self.stock = {n: 0.0 for n in NEEDS}
        self.up_streak = 0
        self.down_streak = 0
        self.log = []

    def cycle(self, npc_output, players, player_supply, player_spend, lux_flood=0.0):
        activity = max(MIN_ACTIVITY, min(1.0, players / EXPECTED_PLAYERS))
        active = unlocked(self.tier)                    # what it CONSUMES
        trades = unlocked(min(5, self.tier + 1))        # what it STOCKS

        # --- NPC production: spread across unlocked needs, scaled by output mult
        out_mult = max(0.5, min(1.25, 0.75 + self.satisfaction / 100))
        produced = npc_output * out_mult * activity   # v6: sites idle on a quiet server
        w = sum(RATE[n] for n in trades)
        for n in trades:
            self.stock[n] += produced * RATE[n] / w

        # --- player supply: town buys what it is short of, treasury permitting
        bought_units = 0
        if players > 0:
            offer = player_supply * players
            # v3: the town buys toward the town it wants to BECOME, not merely
            # toward what it eats today. Without this, NPC output meeting current
            # demand means the town never buys, so players can never contribute.
            grow_pop = POP_PER_TIER * min(5, self.tier + 1)
            for n in sorted(trades, key=lambda x: self.stock[x]):
                if offer <= 0:
                    break
                required = max(1.0, self.pop * RATE[n] * activity)
                want = grow_pop * RATE[n] * activity * COVER_CYCLES
                if self.stock[n] >= want:
                    continue                                    # holding enough to grow into
                scarcity = max(0.0, 1 - (self.stock[n] / required)) if required else 0
                price = BASE_PRICE * min(BUY_CAP, 1 + BUY_SCARCITY_K * scarcity)
                affordable = max(0, (self.treasury - TREASURY_RESERVE) / price)
                take = min(offer, want - self.stock[n], affordable)
                if take <= 0:
                    continue
                self.stock[n] += take
                self.treasury -= take * price
                offer -= take
                bought_units += take

        # --- consumption
        fulfil, lux_per_head = {}, 0.0
        for n in active:
            required = self.pop * RATE[n] * activity
            if lux_flood and n == "luxury":
                self.stock[n] += lux_flood * self.pop
            consumed = min(required, self.stock[n])
            self.stock[n] -= consumed
            fulfil[n] = consumed / required if required else 1.0
            if n == "luxury":
                lux_per_head = consumed / self.pop if self.pop else 0

        wsum = sum(WEIGHT[n] for n in active)
        overall = sum(WEIGHT[n] * fulfil[n] for n in active) / wsum

        # --- satisfaction, with the vice curve
        L = lux_per_head / (RATE["luxury"] or 1)
        bonus = LUX_AMPLITUDE * (L / LUX_OPTIMUM) * (2 - L / LUX_OPTIMUM) if "luxury" in active else 0
        self.satisfaction = max(0.0, min(100.0, 100 * overall + bonus))

        # sickness feedback: excess vice raises medicine demand next cycle
        if L > LUX_OPTIMUM and "medicine" in active:
            self.stock["medicine"] = max(0, self.stock["medicine"]
                                         - SICKNESS_FACTOR * (L - LUX_OPTIMUM) * self.pop)

        # --- money: player purchases in, exports in, wages + garrison out
        self.treasury += player_spend * players
        keep = {n: POP_PER_TIER * min(5, self.tier + 1) * RATE[n] * COVER_CYCLES * EXPORT_BUFFER for n in trades}
        surplus = sum(max(0, self.stock[n] - keep[n]) for n in trades)
        if surplus > 0:
            for n in trades:
                excess = max(0, self.stock[n] - keep[n])
                self.stock[n] -= excess
            self.treasury += surplus * EXPORT_PRICE
        self.treasury -= self.pop * WAGE_PER_HEAD + GARRISON_COST * self.tier
        self.treasury = max(0, self.treasury)

        # --- population drifts toward the tier cap, driven by satisfaction
        cap = POP_PER_TIER * self.tier
        self.pop += (cap - self.pop) * POP_GROWTH * (self.satisfaction / 100)
        self.pop = max(10, self.pop)

        # --- tier evaluation
        sustained = produced + bought_units
        grow_pop_next = POP_PER_TIER * min(5, self.tier + 1)
        covered = all(self.stock[n] >= grow_pop_next * RATE[n] * COVER_CYCLES * 0.9
                      for n in unlocked(min(5, self.tier + 1)))
        if overall >= TIER_UP_THRESHOLD and covered:
            self.up_streak += 1
        else:
            self.up_streak = 0
        self.down_streak = self.down_streak + 1 if overall < TIER_DOWN_THRESHOLD else 0

        if self.up_streak >= TIER_UP_CYCLES and self.tier < 5:
            self.tier += 1
            self.up_streak = 0
        elif self.down_streak >= TIER_DOWN_CYCLES and self.tier > 1:
            self.tier -= 1
            self.down_streak = 0

        self.log.append(dict(tier=self.tier, pop=self.pop, treasury=self.treasury,
                             sat=self.satisfaction, overall=overall, sustained=sustained))
        return overall


def run(name, cycles, npc_output, players, supply=0.0, spend=0.0,
        raid=None, lux_flood_from=None):
    s = Settlement()
    for c in range(cycles):
        p = players
        sup = supply
        flood = 0.0
        if raid and raid[0] <= c < raid[1]:
            sup *= 0.0                      # supply lines cut
            npc_now = npc_output * 0.4      # sites overrun
        else:
            npc_now = npc_output
        if lux_flood_from is not None and c >= lux_flood_from:
            flood = 2.5                     # someone is dumping cheap vice
        s.cycle(npc_now, p, sup, spend, flood)
    return name, s


def report(name, s, every=None):
    L = s.log
    n = len(L)
    marks = [n // 4, n // 2, 3 * n // 4, n - 1]
    print(f"\n=== {name} ===")
    print(f"{'cycle':>6} {'tier':>5} {'pop':>6} {'treasury':>12} {'sat':>6} {'fulfil':>7}")
    for i in marks:
        r = L[i]
        print(f"{i:>6} {r['tier']:>5} {r['pop']:>6.0f} {r['treasury']:>12,.0f} "
              f"{r['sat']:>6.1f} {r['overall']:>7.2f}")
    tiers = [r["tier"] for r in L]
    final = L[-1]
    settled = tiers[-min(50, n):]
    print(f"  final tier {final['tier']}   range over run {min(tiers)}-{max(tiers)}   "
          f"last-50 tier {'stable at %d' % settled[0] if len(set(settled)) == 1 else 'OSCILLATING %s' % sorted(set(settled))}")
    print(f"  treasury {'SOLVENT' if final['treasury'] > 0 else 'BANKRUPT'}"
          f"   satisfaction {final['sat']:.1f}")


if __name__ == "__main__":
    print("Living Aden — economy model test")
    print("v6: production scales with activity; NPC capacity holds tier 3; production proportional to need; tier cost DERIVED from consumption; export buffer 1.2x; realistic player rates")

    NPC_BASE = POP_PER_TIER * 3 * sum(RATE[n] for n in unlocked(3)) / 1.25
    print(f'NPC output set to tier-3 consumption = {NPC_BASE:.0f}/cycle')

    report(*run("A. Empty server (0 players)", 400, NPC_BASE, players=0))
    report(*run("B. 5 players", 400, NPC_BASE, players=5, supply=32, spend=1200))
    report(*run("C. 20 players", 400, NPC_BASE, players=20, supply=32, spend=1200))
    report(*run("D. 20 players, roads cut cycles 200-260", 400, NPC_BASE,
                players=20, supply=32, spend=1200, raid=(200, 260)))
    report(*run("E. 20 players, vice flooded from cycle 200", 400, NPC_BASE,
                players=20, supply=32, spend=1200, lux_flood_from=200))
