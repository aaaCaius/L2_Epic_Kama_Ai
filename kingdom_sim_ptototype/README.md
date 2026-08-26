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

## The whole of Aden, from the real map

**All nine castle regions run as full kingdoms** — 19 towns, 40 enterprises, 45 wild sites and 12
landmarks, **134 real places** in total. Every one exists in the game: Fellmere Harvesting Grounds,
the Mithril Mines, Cruma Tower, the Enchanted Valley, Dragon Valley, the Hot Springs, Primeval Isle.
`world.js` is the whole mapping in one editable file, and every entity panel names the real location it
stands for.

The **world map** shows all nine realms with the roads between them, coloured by how well fed they are
and by whether each road carries a treaty or an embargo. Click a realm to drop into its own regional
map; click a place to see what it is doing and why.

### The real terrain wrote the economy

| Realm | Makes | Must buy |
|---|---|---|
| Gludio | food, cloth, materials, luxury | **arms, medicine** |
| Dion | food, medicine, cloth, materials | arms, luxury |
| Giran | materials, luxury | **food, cloth, medicine, arms** |
| Oren | medicine, cloth, food, luxury | materials, arms |
| Aden | luxury, cloth, medicine | **food, materials, arms** |
| Innadril | luxury, food | cloth, materials, medicine, arms |
| Goddard | medicine, food, materials | cloth, arms, luxury |
| Rune | medicine, food, cloth | materials, arms, luxury |
| Schuttgart | **arms**, materials, food | cloth, medicine, luxury |

Nobody is self-sufficient, and that is not a balance decision — it is what the map says. Gludio has no
mine and no apothecary. Giran is a great harbour with no farmland at all. **Schuttgart's Dwarven mines
are where the iron of Aden actually comes from**, which makes it the realm everyone needs and nobody
can ignore.

**This fixed the biggest open finding.** Diplomacy used to be decoration because the neighbours were
stubs. With nine running realms that genuinely lack things, the Envoys sign **36 treaties on their own**
inside three years — because they have to.

## What is in it

- **Nine kingdoms** — every castle region of Aden, each with its own Count, advisors, towns and enterprises
- **The Count**, and four advisors: Marshal, Justiciar, Chancellor, Envoy
- **Three Mayors** with real discretion — they may comply, divert, send half, or refuse
- **Escalation** — refusal can end in deposition, rebellion, or defection to a rival
- **Independent enterprises** that negotiate, pay tax, take contracts, and resent requisition
- **Contracts as quests** — posted by towns, taken by enterprises, with consequences for failure
- **Real foreign trade** — nine realms, each short of something, trading along the actual road network
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

Thirteen bugs, none of which were visible in the written design. This is the argument for the prototype.

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
9. **A single bad week unwound the entire military posture.** The Count flipped to `SURVIVE` for one
   week, every garrison was abandoned, and the next week they were all retaken. Governments need
   inertia: a posture change now has to be warranted for several weeks running. The same lesson as the
   tier gate — a state that matters should not turn on one sample.
10. **The Marshal walked its list greedily** and always ran out of budget at the same point, so the
   same sites were dropped and retaken forever. It now ranks every site and funds down the list, which
   is both stabler and a more honest model of how a commander actually allocates.
11. **Import-dependent realms had no way to pay for imports.** The crown bought food out of the realm
   treasury and the towns that ate it were never charged — so Giran's treasury drained 211k → 10k while
   its town's grew 40k → 210k, and then it starved with a full purse next door. Towns now pay for what
   they receive; the crown negotiates the trade and takes a tariff.
12. **The Envoy's budget was a flat share**, so a realm buying four of its six goods ran on the same
   import allowance as one buying two. It now scales with how dependent the realm actually is.
13. **The supply target sat below the growth gate** — enterprises sold toward three weeks of *current*
   need while promotion required four weeks of the *next tier's* need. Supply throttled just under the
   threshold and no town could ever rise. This is the F3/F4 shape from the economy test, appearing again
   in a completely different system.

## What is still open

Honest list. These are findings, not oversights:

- **Towns plateau just below the growth gate.** Food and cloth stall a little short of the coverage
  requirement. This is a tuning problem and it is exactly what the Constants drawer is for — try
  `coverCycles`, the enterprise capacities, and `tierUpTicks`.
- **The garrison choice is now driven by affordability rather than judgement.** With thirteen real
  sites and a fixed budget, the Marshal holds as many as it can pay for. That is not yet the
  "is the yield worth the garrison?" decision the design wants — the yield needs to matter more and the
  cost needs to bite harder.
- **Some import-dependent realms still oscillate.** Giran and Schuttgart swing between fed and starving
  across years. The structure is right — they trade for what they lack — but the numbers are not settled.
  This is Constants-drawer territory: try `tradeMarkup`, `townIncomePerHead` and the enterprise rates.
- **A stable realm produces no politics.** Orders only fire when total threat exceeds 90, so a
  well-garrisoned kingdom never generates a standoff. Force one with the **Order a levy** button on a
  struggling town.
- The **Fortress Commander** tier is defined but not yet acting.

---

## Files

| | |
|---|---|
| `world.js` | all nine regions from the real map — every location, and what it is |
| `sim.js` | the simulation core — no DOM, and the part that becomes Java |
| `ui.js` | rendering and interaction |
| `style.css` | presentation |
| `index.html` | the shell |

`sim.js` is deliberately free of anything browser-shaped so it can be read as a specification. Every
decision goes through one `decide()` function that records the options and their scores, which is what
makes the reasoning visible in the UI.
