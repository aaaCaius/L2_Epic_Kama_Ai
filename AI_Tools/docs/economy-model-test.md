# Testing the economy model

> A numerical test of the Living Aden mechanics, run before any Java is written.
> Simulation: `AI_Tools/scripts/ecosim.py`. Six versions, 400 cycles each, five scenarios.

---

## Verdict

**The concept is sound. The mechanics as specified were not — they do not work.**

Run with the exact constants from the technical design, every scenario produced an **identical**
result: tier 3, fulfilment 1.00, satisfaction 100, treasury growing without bound. Twenty players
behaved exactly like zero players. A raid changed nothing. Flooding the town with vice changed nothing.

Six structural flaws came out of it. Four are fixed and verified; two remain open. None of them would
have been obvious from reading the design, and all of them would have cost weeks if found in Java.

---

## What the model is

A settlement with tier, population, treasury, satisfaction and a stockpile per need, run for 400
cycles under five scenarios: empty server, 5 players, 20 players, 20 players with the roads cut for 60
cycles, and 20 players with cheap vice flooding the market from cycle 200.

**Deliberate simplifications** — this tests the economic loop, not the game: one price for all goods,
needs aggregated rather than per-item, and no sites, caravans, workshops or player agency. Findings
about *structure* carry; the specific numbers do not.

---

## The flaws

### F1 — Tier costs and consumption were in different units

The design specified tier costs as an exponential series and consumption as per-capita rates, and
**never reconciled them**. They are not on the same scale:

| Tier | Design threshold | What the town actually eats | Error |
|---|---|---|---|
| 3 | 2,800 | 1,026 | 2.7× too high |
| 4 | 7,840 | 1,608 | 4.9× too high |
| 5 | 21,952 | 2,190 | **10× too high** |

The error compounds because the threshold grew at 2.8× per tier while real consumption grows about
1.4×. **Fix: derive tier cost from consumption** — `popCap(tier) × Σ rates(unlocked needs)` — rather
than inventing a series.

### F2 — Player contribution was an order of magnitude too small

Closing the tier-3 → tier-4 gap as originally specified needed **5,040 extra units per cycle**. Twenty
players at the assumed rate supply 1,800. The design needed **56 players** to move a town one tier.

Falls out once F1 is fixed: the real gap is 582/cycle, or about 29 units from each of 20 players.

### F3 — Gating a tier on a *flow* creates a permanent stall

This was the worst one, and the least obvious.

Tier-up required `sustained supply ≥ cost(next tier)`, where sustained = production + purchases. But
purchases are driven by **stock deficit**, so once stock equilibrates the town stops buying. The system
throttles its own inflow.

Result: `sustained` converged to **1,489** against a threshold of **1,608** and sat there **forever**.
Players supply, the town absorbs it, the number never moves. Maximum frustration, no feedback, no
diagnosis available to the player.

> **You cannot gate progress on a flow that the system itself limits.**

**Fix: gate on stock coverage.** A town may rise when it *holds* enough to feed the town it wants to
become, for N cycles. Stock accumulates when supply exceeds consumption, so the gate is reachable — and
it is legible in fiction: the granaries are full.

### F4 — "Only buy what you are short of" blocks growth entirely

A sensible-sounding rule that prevents infinite sinks, and it quietly makes players irrelevant: if NPC
production meets current demand, the town is never short, so it never buys from players, so players can
never contribute anything.

**Fix: the town buys toward the town it intends to become**, not toward what it eats today. This also
turns out to be a good mechanic — whether the town buys for subsistence or for growth is exactly the
kind of decision a ruler should be making.

### F5 — Chicken-and-egg on newly unlocked needs

A tier-1 town does not trade clothing. So it never stocks clothing. So it can never satisfy the
tier-2 requirement. **Every town was frozen at tier 1.**

**Fix: towns stock next-tier goods in anticipation.** Also better fiction — you can watch a town
preparing to grow as unfamiliar goods start arriving in its warehouses before the shops open.

### F6 — Activity scaling was applied to consumption but not production

Consumption scaled with player activity; production did not. So a quiet server over-produced, and
**an empty server grew faster than a busy one**, reaching tier 5 with nobody playing — the exact
inverse of the intent.

**Fix: sites idle too.** Both sides scale with activity, so the *balance* holds while the *rate* slows.
A quiet world sits still rather than booming.

---

## Still open

### F7 — Towns over-extend, then stall under-fed and bankrupt

With the gates fixed, towns rise on accumulated stock to a tier they cannot **sustain**, then sit at
fulfilment 0.59–0.67 with an empty treasury. They never fall back, because the demotion threshold
(0.50) sits *below* where an under-supplied town naturally settles.

| Scenario | Final tier | Fulfilment | Treasury |
|---|---|---|---|
| Empty server | 4 | 0.59 | **bankrupt** |
| 5 players | 4 | 0.59 | **bankrupt** |
| 20 players | 4 | 0.67 | at the reserve floor |

A second attractor, mirroring F3 — stuck just above the threshold that would rescue it.

**Candidate fixes:** raise the demotion threshold above the natural under-supply equilibrium; require
sustained fulfilment *after* promotion or the tier reverts; or make promotion cost the stock it
consumed, so a town cannot rise on a buffer it then eats.

### F8 — Vice masks starvation

Satisfaction adds the luxury bonus to fulfilment, so **flooding a starving town with wine raises its
satisfaction — and therefore its output**. In scenario E the town held satisfaction at 90–100 while
fulfilment sat at 0.65.

The intended penalty only fires above twice the optimum, and consumption clears the excess before it
bites. So "excess vice is counterproductive" never actually triggers, and the mechanic inverts: vice
becomes a straightforward buff.

**Candidate fix:** make luxury *multiply* fulfilment rather than add to it, so it can amplify a
well-fed town but cannot rescue a starving one. Bread first, then circuses.

---

## What worked

**The raid scenario is exactly the drama the concept promises.** Cutting the roads for 60 cycles drove
tier 5 → 4, halved the population from 514 to 290, spiked the treasury (nothing left to buy), and was
followed by a genuine, visible recovery back to tier 5. That is a story a player would tell.

**Treasury binding works** once income and outflow are the same order of magnitude — it reached the
reserve floor and stayed there, which is the graceful degradation the design depends on.

**Tier movement is consequential.** Population, satisfaction and stock all visibly follow it.

---

## Corrected calibration

| Constant | Design | Tested | Why |
|---|---|---|---|
| Tier cost | `1000 × 2.8^t` | `popCap(t) × Σ rates` | F1 — must be in consumption units |
| Tier gate | sustained *flow* | stock **coverage**, 4 cycles | F3 — flow self-throttles |
| Buy target | current deficit | next tier's needs | F4 — otherwise players are irrelevant |
| Goods traded | unlocked needs | unlocked **+ next tier** | F5 — otherwise no town ever grows |
| Activity scaling | consumption only | consumption **and** production | F6 — otherwise empty servers boom |
| NPC capacity | tier-3 consumption | tier-3 consumption **÷ 1.25** | the satisfaction bonus is a multiplier on top |
| Demotion threshold | 0.50 | **unresolved** — must sit above the under-supply equilibrium | F7 |
| Luxury term | additive | **unresolved** — probably multiplicative | F8 |

---

## What this cost, and what it saved

Six iterations of a 200-line script. Every one of these flaws would otherwise have surfaced only after
the settlement model, the tick manager, the schema and the merchant hooks were all written — and F3 in
particular would have presented in-game as "the economy just doesn't do anything", which is close to
undebuggable from the inside.

**Recommendation: keep the simulation as a permanent fixture.** Every tuning change should be run
through it before it reaches the server, and the sim's constants and the server's `economy.properties`
should be the same file.
