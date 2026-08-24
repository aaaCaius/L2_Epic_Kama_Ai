# Kingdom of Gludio — prototype

A playable model of the Kingdom AI, built to find out whether the concept works **before** any of it
reaches the Lineage 2 server.

**Open `index.html` in a browser.** No build step, no dependencies, no server.

---

## Why this exists

`ecosim.py` found six structural flaws in the economy mechanics that reading the design had not. The
Kingdom design is far more intricate. So it gets played before it gets ported.

The prototype is the **specification**: the server's job is to reproduce its behaviour, and its tuned
constants become the starting values in `economy.properties`.

---

## What is in it

- **One kingdom** — Gludio: three towns, a keep, five independent enterprises, three wild sites
- **The Count**, and four advisors: Marshal, Justiciar, Chancellor, Envoy
- **Three Mayors** with real discretion — they may comply, divert, send half, or refuse
- **Escalation** — refusal can end in deposition, rebellion, or defection to a rival
- **Independent enterprises** that negotiate, pay tax, take contracts, and resent requisition
- **Contracts as quests** — posted by towns, taken by enterprises, with consequences for failure
- **Foreign trade** — Gludio can make no medicine and no luxury, so it must buy them abroad
- **Wild sites** — dangerous and profitable at once

## How to use it

**Navigation is the hierarchy.** Click into the castle to see the Count and his advisors; into a town
to see its Mayor, shops and contracts; into an enterprise to see its books and its mood. Every panel has
the same shape — *what it is doing, why, its resources, how to intervene* — because if one panel works
everywhere, the harness architecture is probably right.

**Every decision shows its reasoning**: the alternatives, their scores, and the note explaining each.
The winning line is highlighted.

**Three levers**, the three a ruler actually has:

| Lever | What it does |
|---|---|
| **Order** | a levy on a town — an objective, not a command. It may be refused |
| **Context** | tell the court something it cannot know (war, famine) |
| **Feedback** | commend or rebuke an agent, which writes to its lessons |

**Constants** opens a live tuning drawer. Everything applies immediately.

---

## What building it already found

Nine bugs, none of which were visible in the written design. This is the argument for the prototype.

1. **Towns had no income at all.** They paid wages and remitted tax and bought goods, and nothing ever
   paid them. Treasury to zero, nothing bought, fulfilment to zero, permanent `SURVIVE`.
2. **The Count allocated a budget to the towns and never sent it.** The crown hoarded while its towns
   starved.
3. **Production and consumption were never reconciled** — 84 food produced against 660 consumed. The
   same class of error as finding F1 in the economy test: two numbers in different units, never checked
   against each other.
4. **A 40-unit cap per trade** when a town eats 300 a week.
5. **Absolute scores competing against rescaled prices.** When `basePrice` moved from 100 to 20, the
   "hold stock" option kept its hard-coded score of 55 — so every enterprise hoarded forever while towns
   sat on full treasuries and starved. *A score that competes with a price must be in the same units as
   that price.*
6. **An unrecoverable death spiral**: income was multiplied by satisfaction while scarcity multiplied
   prices fourfold. A town that dipped once faced quadrupled prices on halved income and could never
   climb out.
7. **Price is a useless routing signal once everyone is fed.** With all towns at 100% fulfilment every
   town bid the same, so supply went to whichever sorted first — one town hoarded while another starved.
   Supply now follows the gap to target, not the price alone.
8. **One sale per enterprise per week** meant a single pasture could supply only one of three towns.
9. **The supply target sat below the growth gate** — enterprises sold toward three weeks of *current*
   need while promotion required four weeks of the *next tier's* need. Supply throttled just under the
   threshold and no town could ever rise. This is the F3/F4 shape from the economy test, appearing again
   in a completely different system.

## What is still open

Honest list. These are findings, not oversights:

- **Towns plateau just below the growth gate.** Food and cloth stall a little short of the coverage
  requirement. This is a tuning problem and it is exactly what the Constants drawer is for — try
  `coverCycles`, the enterprise capacities, and `tierUpTicks`.
- **The garrison choice is often a near-tie** (60 against 60). That is precisely the failure the design
  predicted: if cost and yield do not move with circumstance, it is a formality rather than a decision.
- **The Envoy never signs anything.** Opinion has to exceed 55 and nothing moves it, so every relation
  stays neutral and diplomacy never gets tested.
- **A stable realm produces no politics.** Orders only fire when total threat exceeds 90, so a
  well-garrisoned kingdom never generates a standoff. Force one with the **Order a levy** button on a
  struggling town.
- The **Fortress Commander** tier is defined but not yet acting.

---

## Files

| | |
|---|---|
| `sim.js` | the simulation core — no DOM, and the part that becomes Java |
| `ui.js` | rendering and interaction |
| `style.css` | presentation |
| `index.html` | the shell |

`sim.js` is deliberately free of anything browser-shaped so it can be read as a specification. Every
decision goes through one `decide()` function that records the options and their scores, which is what
makes the reasoning visible in the UI.
