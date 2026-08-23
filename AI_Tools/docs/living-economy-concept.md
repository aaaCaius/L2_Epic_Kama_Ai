# L2Epic — Aden as a living, buildable, losable world

> Status: **concept.** No implementation planned yet. This is about what a player feels.

---

## The vision, in one line

> **Aden is a world of towns and territories that clans raise, rule, defend — or raze.**

Players are not customers of the world. They are its **stewards**. A region prospers because someone
looked after it, and it falls because someone else arrived with an army, or because nobody bothered.

That single idea unifies the three things an economy can be for:

- **Development** is the market — securing sites, moving goods, growing production
- **Defence** is the PvP — everything you build is something someone else can take or break
- **Growth** is the immersion — a town visibly thickening from outpost to metropolis is the reward

They are not three features. They are one loop seen from three angles.

---

## The world

Four kinds of thing on the map. The relationships between them are the game.

**Towns** — the hubs. They consume, craft and retail. Each has a population, a treasury, a stockpile
and a **tier**. What a town can sell you is a direct readout of how well it is being looked after.

**Sites** — the territory, and the half that makes this a *world* rather than a shop simulator. Farms,
mines, quarries, lumber camps, fisheries, herb groves, vineyards, ruins, workshops. Each produces raw
materials, each can be developed, degraded, contested or lost — and **each is owned by someone who owes
the region tax**, whether that owner is an NPC or a player.

**Roads and sea lanes** — the arteries. Goods are **physical**. Nothing appears on a shelf that did not
travel there on a wagon that could have been stopped, or a ship that could have been blockaded. Caravans
already exist and already work; harbours carry the bulk trade and the link to the wider world.

**Threats** — the pressure. Bandit camps preying on roads. Monster nests choking a mine. Rival clans.
A region left alone does not stay still; it decays.

The chain a player can see end to end:

```
secure a mine ─► ore ─► caravan hauls it ─► smith crafts ─► shop stocks better gear
      ▲                                                                  │
      │                                                                  ▼
 more capacity ◄──── town tier rises ◄──── needs met ◄──── population grows
      │                                                                  ▲
      └──────────── threats erode every link ───────────────────────────┘
```

Every link is a place a player can act, and a place an enemy can strike.

---

## The core loop

1. **Clear** a threat — a bandit camp on a road, a nest smothering a farm
2. **Secure and develop** the site it was strangling
3. **Move** what it produces, by caravan, along a road that can be raided
4. **Feed** the town: needs met, population grows, tier rises
5. **Unlock** better shops, services and stock — for everyone in that town
6. **Defend** it, because it is now worth taking

Loss runs the same loop backwards, and that symmetry is the point.

---

## What the world is made of

The material base, in three bands. The bands matter because they behave completely differently as
cargo, and that difference is where a lot of the gameplay lives.

**Bulk — heavy, cheap, constant.** Wood, stone, reeds, clay, grain, ore, hide. These feed food,
construction and tools. High volume, low value per wagon: dull to steal, but a town starves without
them. Losing a grain shipment is not a robbery, it is a famine.

**Refined — what workshops make.** Planks, cut stone, flour and bread, cloth, leather, ingots,
weapons, armour, potions, scrolls, books. The value players add.

**Specials — light, precious, and trouble.** Grapes and berries into **wine and cordials**. Tobacco into
**cigars**. Poppy and certain weeds into **narcotics**. Rare roots into **alchemical reagents** for the
best potions. Gems into jewellery.

Specials are the interesting ones: small, valuable, and easy to carry. They are what makes a caravan
worth ambushing rather than merely worth stopping, and they are what a city needs to reach tier 4 and 5
at all.

### Contraband

Because specials are compact and valuable, a ruler can **tax or ban** them — and that immediately
creates the other half of the economy.

- Prohibit narcotics and the trade does not stop; it goes underground and pays better
- Tax wine heavily and smugglers undercut your own merchants

Vice is lucrative, and taxing it funds the garrison — but tolerating it invites exactly the people who
are useful to your enemies. That is a real dilemma with no clean answer, which is what makes it worth
putting in front of a player.

---

## Defining the resources

The catalogue should be **data, not code** — editable without a rebuild, because this is where most of
the iteration will happen.

### Start from items that already exist

Interlude already ships almost everything needed. Reusing them costs nothing; inventing them costs a
client patch for every player.

| Band | Already in the game |
|---|---|
| Bulk | Crops and grain (manor), Iron Ore, Coal, Charcoal, Stem, Thread, Cord, Animal Bone, Animal Skin, Suede, Varnish |
| Refined | Every craftable — 870 recipes already define raw → finished |
| Specials | Gems, dyes, enchant scrolls, high-grade reagents |

Genuinely missing: **wine, tobacco, narcotics.** Those are the only ones worth minting new items for, and
they should wait until the systems around them are proven.

### The shape of a resource definition

```
resource:
  id            wheat
  name          Wheat
  band          bulk | refined | special
  items         [ item ids that count as this resource ]
  serves        food | water | clothing | medicine | weapons | luxury | entertainment
  weight        how much wagon space a unit takes
  base_value    the anchor price before scarcity moves it
  sources       [ site types that produce it ]
```

Two mappings do the real work:

**Need → accepted items.** "Food" is satisfied by bread, fish, crops, rations — several items, one need.
This is what lets a town's demand be flexible and lets players choose how to meet it.

**Site type → resources produced.** A farm yields grain and reeds, a mine yields ore and stone, a
harbour yields fish and imports. This is what puts the economy on the map.

### Classification you already have for free

Three axes exist in the item data today with no new tables at all:

- **`item_type`** — 654 items are already typed `material`, plus seeds, potions, scrolls, herbs. (Worth
  knowing: the loader currently throws the `material` type away; restoring it is a one-line fix that
  hands you a raw-material predicate over 654 items.)
- **`crystal_type`** — a clean six-level grade axis (none, D, C, B, A, S) spanning every item table. The
  natural key for tiering a production chain against settlement tier.
- **`price`** — the reference price, already the economic backbone the manor system uses.

`item_type` × `crystal_type` × `price` gives a complete three-axis classification before writing a
single new data file.

---

## Outlawry: standing, crime and consequence

**Standing is held with an authority, not with the world.** You are an outlaw *of Talking Island*, not
an outlaw everywhere. That one decision makes the whole system rich: you can be hunted in one region and
welcome in the next, and a rival town has an obvious motive to shelter its enemy's enemies.

### Four roads to outlawry

| Route | What it looks like |
|---|---|
| **War** | Your faction or clan is at war with theirs — the simplest and most honest case |
| **Defiance** | You hold a farm, mine or port in their region and refuse to pay its taxes |
| **Violence** | You raided their caravans, or killed their citizens or their people |
| **Contraband** | You were trafficking banned goods **and you were caught** |

That last word carries weight. **Crime has to be detected.** Goods can be inspected, deeds can be
witnessed, and a smuggler who is never caught is simply a merchant with better margins. Detection risk
is what makes the criminal path a *skill* rather than a toggle — and it means guards patrolling a road
are doing something real.

### What it costs you

- **The garrison attacks on sight.** This is what the town's soldiers are *for*, and it makes the
  ruler's spending on troops visibly meaningful.
- **Services close.** No shops, no trainers, no enchanters. The reward ladder you helped build becomes
  unavailable to you — the sharpest possible punishment, because it is the thing everyone wants.
- **A bounty appears on the work board**, funded from the treasury. Other players are now paid to hunt
  you, and the amount says how badly they want you.
- **Only the black market will deal with you** — which is precisely why smugglers and outlaws need each
  other.

### Defiance is a legitimate way to play

Holding a site without paying tax makes you a **rebel** rather than a criminal, and that should be a
supported path, not a mistake. A clan can seize a mine and hold it against the region — accepting that
the garrison is coming, that bounties will be posted, and that they must supply themselves through the
black market.

That is exactly the "kill the rebel leader" bounty from the other side, and it means **players can do
everything the NPC bandits do.** The bandit camp and the rebel-held mine are the same object to the
system. Every tool built for one works for the other, which is a large saving and a much livelier world.

### Getting back in

Permanent outlawry on a 20-player server would just isolate people, so there must be ways home: pay the
fine, make restitution, work off the debt through contracts, or be granted **amnesty by the ruler**.

That last one is a genuinely interesting political instrument. A ruler can pardon a useful enemy, buy
off a rebel, or refuse — and be judged for it.

## The three goals

**The objective of the whole design is a town at maximum level — or as close as you can hold it.** That
is the north star, and everything else is a means to it.

Getting there is always the same three priorities, in this order:

### Goal 1 — Survive the immediate threat

Bandits on the road, a nest in the fields, an army at the gate. Nothing else matters while something is
actively taking what you have. This is the first thing a new player learns, and the first thing a region
in trouble falls back to.

### Goal 2 — Meet the basic needs

Food, water, then clothing and medicine. Keep people fed and the town holds together. This is
maintenance — unglamorous, constant, and the floor everything else stands on.

### Goal 3 — Grow, deliberately

Only once threats are handled and needs are met can you actually **plan**: which sites to develop, which
resources to chase, what to stockpile, how to keep people satisfied. This is where rulership becomes
interesting rather than reactive.

Why this ladder matters as design: it gives every player, at every scale, an unambiguous answer to
"what should I be doing right now?" — and it means a region under pressure naturally *collapses back
down* the ladder. Losing tier 4 is not an arbitrary penalty; it is what happens when growth stops
because you are busy eating.

---

## Needs and tiers

A town's population needs **food, water, clothing, medicine, weapons, luxury, entertainment**, and
needs unlock progressively with tier. A tier-1 outpost does not care about entertainment, because
nobody has eaten yet.

| Tier | Must sustain | What it gives players |
|---|---|---|
| 1 Outpost | food, water | bare essentials, a token watch |
| 2 Village | + clothing, tools | basic craftsmen, D-grade, **pet shop** |
| 3 Town | + medicine, weapons | real smiths, C-grade, a trained garrison, **mount shop** |
| 4 City | + luxury — jewellery, wine, buff scrolls, fine food | B/A-grade stock, specialist services |
| 5 Metropolis | + entertainment, all sustained | top-tier crafting, unique services, best prices, elite garrison |

**Tier is the scoreboard; shop stock is the prize.** Higher tiers add needs faster than capacity, so
the top is hard to hold. Equilibrium should sit around **tier 3** — comfortable, with 4 and 5 as things
a committed clan sustains and can visibly lose.

### Satisfaction — and the trouble with vice

Meeting needs keeps a town alive. **Satisfaction** is what makes it productive.

Luxury and entertainment — wine, tobacco, fine food, jewellery, the special buffs — raise satisfaction,
and a satisfied population works harder, produces more and grows faster. This is why a tier-4 city needs
vineyards as much as it needs grain.

**But excess is counterproductive.** Oversupply the vices and output falls: sickness, absence, a town
that drinks its own profits. There is an optimum, and it is not "as much as possible."

That single curve does a great deal of work:

- It makes **prohibition and tariffs a productivity decision**, not a moral one. A ruler taxes narcotics
  because the fields need working, not because it is wicked.
- It gives **smuggling a real economic effect** — flooding a rival's city with cheap vice is a genuine
  attack on their output, and a subtle one that takes a while to notice.
- It makes satisfaction a **dial to manage** rather than a bar to fill, which is far more interesting to
  play.

Goal 3 is exactly this: growth planned around resources *and* satisfaction, neither alone.

Interlude's materials already map onto this: Stem/Thread/Cord to cloth, Iron Ore and Coal to metal,
Animal Bone and Skin to leather, Varnish and Suede to finishing. The 870 crafting recipes already in
the data *are* the production chains. We are not inventing them — we are making them physical.

---

## The reward ladder: what a town unlocks

This is **why a player cares.** Tier is not civic pride, it is access — to the specialists every
Interlude player already wants.

| Tier | Specialists that open |
|---|---|
| **1 Outpost** | grocer, basic smith. Survival only |
| **2 Village** | **pet shop** (wolves), low-level skill trainers, D-grade stock |
| **3 Town** | **skill masters** — training past the basic tiers · **scholars** selling spellbooks · **stable** selling striders · **pack-animal trader** · **crop and food traders**, restaurants · C-grade · trained garrison |
| **4 City** | **enchanters** selling enchant scrolls · **priests and prophets** selling buffs · **scholar-masters** with the rare books (Book of Giants and kin) · **crystal trader** · **mount rentals** · B/A-grade · mount shop |
| **5 Metropolis** | **Master of Air** — wyverns, and **wyvern rentals** · top-tier crafting · unique services · the best prices anywhere · elite garrison |

Rentals deserve a note of their own: they let a tier-5 city sell *access* to prestige rather than the
prestige itself, which spreads the benefit of a great city to visitors and gives players a reason to
travel to someone else's town. A city everyone visits is a city with a thriving market — and a very
tempting target.

Read that ladder as a player and the motivation writes itself. *We need Giran at tier 4 before the next
siege, because that is where the enchant scrolls are.* That is a clan goal, a war aim, and a reason to
guard a road, all in one sentence.

**Enchant scrolls at tier 4 is the sharpest lever in the design.** This is a +16 server — scrolls are
the most wanted commodity in the game. Making them a finite good that a town must produce or import,
sold only where prosperity has been earned and defended, turns the enchant economy into the single
biggest reason to care about who holds what.

### One thing this must never do: block progression

Skill training and spellbooks are how characters *become playable*. If no town is tier 3, nobody can
learn skills, and the server dies.

So the protected starter town always offers **baseline** training and books, regardless of anything
happening in the world. The ladder gates **quality, breadth, convenience and prestige** — better
selection, better prices, higher tiers of training, the rare books, the mounts, the scrolls — never the
ability to level up at all.

### Feasibility: this is mostly re-gating what already exists

The specialists are already in the world — **183 trainers, 94 village masters**, warehouse keepers,
teleporters and pet managers are spawned right now, and the mods engine already runs a scheme buffer.
The work is deciding *which are active at which tier*, not building a wyvern shop from scratch.

That makes the reward ladder unusually cheap for how much motivation it generates — probably the best
effort-to-impact ratio anywhere in this design.

---

## The garrison: wealth becomes defence

A town defends itself with what it can **afford** and what it **has in stock**.

- Its **treasury** pays the wages — more money, more guards, and better ones
- Its **stockpile** arms them — the guards carry weapons and armour the town actually owns

A rich, well-supplied town fields a real garrison. A poor or starving one fields a handful of badly
equipped watchmen, and everyone can see it.

**This is what makes "weapons" a real need rather than a number on a ledger.** The swords a player's
workshop forged, hauled in by caravan, end up in the hands of the guards standing at the gate. Player
industry becomes visible military power — and an attacker who wants a town has a reason to strangle its
supply *first*, because a besieged town's garrison degrades as its stores run dry.

It also gives the ruling clan a genuine strategic dial: spend the treasury on development and grow
faster, or spend it on troops and be harder to take. That is a real decision with real regret attached.

The garrison stands against everything the world throws at the town — bandit raids, monster incursions
and player invasions alike — so investing in it is never wasted, even in a quiet week.

Feasibility note: guard NPCs, siege guards and a mercenary-hire NPC all already exist, and the item data
already contains **516 items typed `castle_guard`** — equipment for exactly this purpose.

---

## Sites, ownership and tax

**Everything outside the walls is owned by someone, and every owner owes the region tax.**

Talking Island already has the buildings for this — three houses that read naturally as farms — and the
harbours are sites in their own right. They are not scenery; they are property.

| Owner | How it works |
|---|---|
| **NPC-owned** | The default. The world produces and pays tax whether or not any player is involved |
| **Player or clan owned** | Bought, rented or seized. You take the profits and you owe the tax |

That NPC default matters more than it sounds: **the economy runs at full strength with nobody logged
in.** Players are not the engine, they are the improvement — which is exactly right for a server with
twenty characters.

### Tax on sites is a major income source

This is the piece the earlier draft missed. A region's treasury is fed by:

- Tax on player purchases in town
- **Tax from every owned site** — farms, mines, quarries, workshops, the harbour itself
- Citizens spending their wages
- Exports

Site tax is the steadiest of the four, because it does not depend on anybody shopping. A region with
many productive holdings is wealthy by structure, not by luck.

### Three ways to get one

| Route | What it costs you |
|---|---|
| **Buy it** from its NPC owner | Money, and nothing else. The clean path |
| **Granted by the ruler** | Loyalty. Patronage is how a ruler rewards useful clans — and a lever they can withdraw |
| **Take it by force** | The site, and your standing — **if anyone finds out** |

The ruler's power to grant land is worth dwelling on: it turns rulership into genuine feudalism. A
ruling clan can settle allies on productive ground, build a network of tax-paying vassals, and revoke it
all from anyone who turns on them.

### Force, witnesses, and getting away with it

Seizing a site by force marks you an **outlaw** — but only if it is known. Guards who survive, workers
who escape, travellers who saw you: **witnesses are what convict you.**

That turns a raid into a decision with texture. Hit fast and leave nobody to tell, and you may hold the
ground as a legitimate owner. Do it sloppily and the garrison knows your name by evening. It also gives
defenders something concrete to protect beyond the buildings — the people who can testify.

It is a dark mechanic, and deliberately so: this is a world where clans take things.

### Refusing tax is the other road

Own a farm or a berth and refuse the tax, and you are **defying the region** — outlawry with something
concrete behind it. The garrison has an address to visit, and a rebel holding is a real place on the map
that can be taken back.

---

## Harbours, ships and exports

**The harbour is where new money enters the region**, and it should be a place, not an abstraction.

Sea trade runs alongside the roads and behaves differently:

| | Caravans | Trading ships |
|---|---|---|
| Carries | moderate loads | **bulk volume** |
| Route | roads, many points to strike | fixed harbour-to-harbour |
| Vulnerable | anywhere along the way | at the **chokepoint** — the port itself |

Talking Island Harbour and Gludin Harbour connect the region to the rest of Aden. Surplus goes out,
foreign coin and imported goods come back.

Three things make this the right money source rather than an arbitrary faucet:

**Money grows with productivity.** A well-run region gets richer because it *produces* more, not because
a timer ticked — rewarding exactly the behaviour the design wants.

**It can be strangled.** Blockading or raiding a harbour cuts a region's income at the source. That is a
slower, more strategic attack than robbing one wagon, and it gives a besieging force something to do
before the walls. Ports are also where contraband arrives, and where inspections happen.

**It replaces a worse faucet.** Today, vendor sales mint adena from nothing at half reference price with
no cap — permanent inflation on an x10 server. Bounded, production-linked income is healthier than what
exists now, independent of everything else here.

Meanwhile the town only ever pays what it **has**, and only for what it **needs**. A broke town does not
break the game; it stops buying, prices fall, and players turn to its customers instead. The economy
degrades gracefully rather than collapsing.

### Who actually buys from you

Not one generic vendor. **Different buyers for different needs**, each paying according to what they are
short of:

- A **grain factor** buying food for the population
- A **quartermaster** buying weapons and armour for the garrison
- **Workshop suppliers** buying raw materials for manufacturing
- A **harbour merchant** buying exotics and anything destined for export

This makes the economy legible through *people* rather than menus. You learn that the quartermaster is
paying well for blades and you know, without reading anything, that the garrison is being rebuilt.

---

## Dynamic quests: the world asks for help

**This is the keystone.** It turns the simulation from something running quietly in the background into
something that *talks to players* — and it is what keeps everyone relevant in a clan-owned world.

Work is generated from real state, not authored in advance:

| Generated from | The work | Example |
|---|---|---|
| A stockpile deficit | **Supply order** | "Gludio needs 500 Iron Ore — paying 4x standard" |
| An active threat | **Threat contract** | "Bandits on the Dion road have hit 3 caravans. Clear the camp." |
| A scheduled shipment | **Escort contract** | "Convoy leaves for Giran in 10 minutes. Escort pays 50k." |
| A degraded site | **Recovery** | "The Oren mine is at 20% output. Restore it." |
| A starving town | **Relief** | "Giran is out of food. Anything delivered pays double." |
| A rival clan's coin | **Interdiction** | "Someone is paying well to stop Gludio's caravans." |
| A named enemy | **Bounty** | "The bandit chief Kalgar has raided the Dion road four times. Bring proof." |

Bounties deserve special mention: a **named, findable, killable individual** is far more memorable than
a quota of anonymous kills, and it gives a raiding campaign a face. When the world generates a villain
because of what has actually been happening on the roads, players will talk about him.

### Rewards: XP, adena, or goods — and the town must actually have them

Pay is **XP and money**, or specific goods where that suits better — a rare material, a crafted piece, a
scroll.

The hard rule underneath: **a town can only pay what it has.** Reward adena comes out of the treasury;
reward items come out of the stockpile. Nothing is minted from nothing.

This one constraint does an enormous amount of work:

- **No reward inflation.** The economy cannot leak value through its own quest system.
- **Poverty is felt, not just displayed.** A starving town that desperately needs food may be unable to
  pay well for it — which is a genuine crisis, and exactly the moment a wealthy rival could step in.
- **Being a good employer becomes a competitive advantage.** A clan that keeps its treasury healthy has
  the whole server working for it. One that hoards or overspends finds its roads unguarded.
- **It makes the treasury legible.** Players learn a town's finances by looking at what it offers.

### The reward is the readout

The single most important rule here: **payment scales with actual need.** Nobody should have to read a
spreadsheet to understand the world. They see *"Iron Ore pays 4x in Gludio"* and instantly know Gludio's
iron supply is in crisis — and that there is money in fixing it.

This makes the economy self-explaining, self-balancing, and self-advertising all at once. Scarcity stops
being an obstacle and becomes a **signal that points at content**.

### It is also how solo players get in

Clans own the territory. But the board is open to everyone, and the work is funded from the town's and
the clan's treasury. So:

- The clan **owns** the mine; anyone can be **paid** to haul its ore or clear the nest choking it
- Unclanned players become the **labour market** — mercenaries, hauliers, escorts, exterminators
- A rich, well-run region can afford good bounties and attracts help; a poor or badly-led one cannot,
  and visibly struggles

That last line is a genuine strategic layer: **being a good employer is a competitive advantage.** A
clan that pays well gets the whole server working for it. One that hoards finds its roads unguarded.

And because rival clans can fund counter-contracts, the quest board itself becomes a PvP surface — two
clans bidding for the same players' time.

---

## Workshops: player-owned industry inside the town

Extend the clan hall idea into something anyone can hold: **buildings in a town, bought or rented, each
housing a specialised workshop that produces goods semi-automatically.**

A smithy, tannery, weaver, apothecary, brewery, bakery, alchemist. You stock it with raw materials; it
converts them into finished goods over time, whether or not you are logged in.

### Why this is the keystone of the economy

**It is the missing middle.** Today the chain runs raw material → town. Towns do not need iron ore, they
need *weapons*; not stems, but *clothing*. Workshops are the converter — which means **the town's
prosperity literally depends on player-run industry.** That is the deepest possible integration between
players and the world, and it makes the whole thing cohere.

**It rewards you while you sleep.** On a server where a handful of people are online at any hour, a
stake that keeps working overnight is worth enormously more than one that only pays while you grind.

**It gives individuals a foothold in a clan world.** Clans own the territory — but a solo player can own
a shop in a town their clan does not rule. Along with the work board, this is the second bridge that
keeps unclanned players fully in the game.

**It makes town tier personally desirable.** Plots are limited and unlock with tier: an outpost has two,
a metropolis has many, and the best specialisations need a high-tier town. Suddenly "raise Giran to
tier 4" is not civic duty, it is **your** business expanding.

**It creates a defence motive that is personal, not political.** When a town falls, workshops burn. You
do not defend Dion because your clan leader said so; you defend it because your forge is in it.

### The money loop

```
buy or rent a plot ─► rent drains adena (a sink)
      │
      ▼
stock raw materials ─► workshop converts over time ─► finished goods
      │                                                     │
      ▼                                                     ▼
sell to players                              sell to the town, filling its needs
                                                            │
                                                            ▼
                                          paid from the town treasury (a source)
                                                            │
                                                            ▼
                                     needs met ─► tier rises ─► better plots unlock
```

Rent is a sink, town purchases are a source, and the player is the engine in between. On an x10 server
with adena inflation, that loop is worth having for its own sake.

### Three scales of holding

Everyone has a rung, and each rung answers to the one above it:

| Scale | What you hold | Who can |
|---|---|---|
| **Plot** | A rented workshop in town | any player, clanned or not |
| **Site** | A farm, mine, quarry or harbour berth outside the walls | any player or clan |
| **Clan hall** | The clan's own workshop and storefront — crafting and selling artefacts at real scale | any clan |
| **Region** | The castle, and authority over everyone below | the ruling clan |

Every rung except the top **owes tax to the region**, and every rung can be lost by refusing to pay it.

**Clan halls become the clan's industry.** They already exist in every town, already have grades,
upgradeable functions and a rent that must be paid — they simply become the place a clan crafts and
sells. That means no clan is ever locked out of the economy by not holding the castle: you may not rule
the region, but you always have a workshop, a storefront and a stake in the town's prosperity.

### The ruler's leverage — and why that makes it political

The castle owner can declare a clan **outlaw**, and an outlawed clan **loses its hall, its shops and its
outposts** in that region.

That single rule turns the pyramid into politics. Clans operate at the ruler's sufferance. Cross the
ruling clan badly enough — raid their caravans, refuse their taxes, traffic what they have banned — and
you do not merely get flagged for PvP, you get **evicted from the regional economy**. Everything you
built there stops being yours.

It cuts both ways, which is what makes it interesting. A ruler who wields it fairly keeps a prosperous,
loyal region. One who wields it greedily drives clans into rebellion, or into the arms of a neighbouring
castle that will happily host them. **Being a bad landlord has consequences.**

### Specialisations — and why your class finally matters economically

A workshop is not generic. You pick what it makes:

| Workshop | Produces | Natural owner |
|---|---|---|
| **Forge** | armour and weapons | Dwarven crafters |
| **Scriptorium** | buff scrolls | Prophets, Elders, buffers |
| **Apothecary** | cures and healing potions | Priests, Bishops |
| **Library** | skill books | Masters, scholars |
| **Weaver / tannery** | clothing | any |
| **Bakery / brewery / vineyard** | food, wine, luxury | any |
| **Jeweller** | jewellery | any |

Tying the specialist workshops to the classes that thematically own them does something valuable on a
PvP server: **it gives support and crafting classes an economic reason to exist.** A Prophet is not just
a buff bot for someone else's raid — they can be the reason the region has scrolls at all. A Warsmith
arms the garrison. That is real standing in the world, earned outside of combat.

Owning a workshop also means two things at once, and both matter:

1. **Auto-crafting** — it converts materials into goods while you are away
2. **A real storefront** — a full multi-item catalogue, not the flat single-list offline shop this
   server already has

That is the upgrade path over the existing offline-shop mod: **offline shops sell what you have;
workshops make what you sell.**

### Tenure, automation and risk

**Rented, not owned.** A recurring lease; miss the payments and you are evicted. Rent is a permanent
adena sink, and turnover means an inactive holder loses the plot to someone who will use it. Clan halls
already work exactly this way and prove the pattern.

**Runs while stocked.** The workshop produces offline until its raw materials run out, then idles. The
ceiling on your earnings is **your supply chain, not a timer** — which loops workshops straight back
into caravans, the market and the work board. It rewards being away without rewarding being absent.

**Destroyed with the town.** If a town is razed to ruins, the workshops inside go with it.

That last one is the harshest option available, and it works **only because razing is a campaign, not an
event.** A town does not fall in an afternoon: roads are cut, sites are lost, needs go unmet, tiers
collapse one at a time. Workshop owners get long, visible warning, and every stage is a chance to fight
back — or to pull their stock out and cut their losses. The stake is total, but nobody logs in to a
surprise. If razing ever becomes fast, this decision becomes a griefing engine and must be revisited.

### Competition and rivalry

Plots are **finite**, which makes them contested without any combat: who gets the Giran forge is a real
question with a real answer. And because a town's tier sets how many plots exist and which
specialisations are allowed, every workshop holder has a direct financial interest in the town's
prosperity — and in whoever is threatening it.

### It already half-exists

Clan halls give this its entire skeleton: ownership, a recurring lease with eviction on non-payment,
per-facility upgrade levels, a **Grade** field that is already a tier, and a self-rescheduling upkeep
task. The shape is `(building, facility_type, level, lease, interval, expires)` — precisely what a
workshop needs.

More tellingly, there is a `merchant_lease` table and a `LEASE` item location still in the codebase with
all their logic **commented out**. Someone began building leased player shops and stopped. This idea
finishes a thought the codebase already had.

It also complements the offline-shop mod this server already runs: offline shops sell what you *have*;
workshops **make** what you sell.

---

## Scarcity: everything can run out

You chose the hard version, and it is right for this vision — **development only means something if
shortage is real.** A world where shelves always refill is a world where nobody needs a mine.

Three rules keep that harshness as content rather than a wall:

**Shortage is local, never global.** Giran being out of shots is a *reason to go to Dion*, not a reason
to log off. World-wide shortage stops being a logistics problem and becomes a brick wall.

**Shortage is always attributable and solvable.** A player should see *why* the shelves are empty — the
road has been raided four times this week, the mine is overrun — and be able to act. Unexplained
scarcity is bad luck; explained scarcity is a quest that writes itself. The dynamic quest board is
exactly this mechanism.

**One protected market.** The starter town keeps guaranteed basic supply, so new players never walk into
a world-scale crisis they cannot understand or influence.

Beyond that, let it bite. Let a siege genuinely cut a city off.

---

## Threats: balanced by design

Pressure comes from both sides, deliberately kept at similar weight — and **NPC pressure scales up when
player activity is low**, so the world always pushes back at a steady rate.

- **Bandit camps** spawn along trade roads and raid caravans on their own
- **Monster incursions** smother production sites and cut output
- **Decay** — an untended site slides backwards
- **Rival clans** — the sharpest, most personal, most unpredictable threat

The scaling matters more than it looks. With eight people online, a purely player-driven threat model
means nothing threatens anything and stewardship has no stakes. With NPC pressure filling the gap, there
is **always** something worth defending, always a contract on the board, and always a reason the shelves
look the way they do — whether or not anyone hostile logged in today.

---

## What players do

**Steward** — clear threats, develop sites, supply towns. Needs no new habits: players already kill mobs
for Iron Ore, Stem and Coal. Today those vanish into a vendor at half reference price, minting adena
from nothing. Instead the town buys them **with money it actually has**, at a price that rises when
short. Grinding becomes economic input without anyone changing how they play.

**Ruler** — own the castle, rule the region. See below; this one deserves its own description.

**Carrier / Escort** — commission and protect shipments. Physical, risky, the direct PvP hook.

**Mercenary** — work the board for whoever pays best. The entry point for solo and unclanned players,
and a role with genuine leverage during a war.

**Industrialist** — hold a workshop, buy raw materials low, convert, sell finished goods to the town or
to players. Pays while you are offline, and quietly makes you the reason the town has weapons at all.

**Raider / Destroyer** — interdict roads, sabotage sites, starve a rival's town and eventually break it.
A full and legitimate way to play, with the same depth as building.

**Smuggler** — move contraband past the guards, supply the black market, get rich on other people's
prohibitions. A criminal career that is economic rather than merely antisocial.

---

## Rulership plays like an asymmetric RTS

The single most important rule for this layer: **no micromanagement.** A ruler should never be
performing chores. They set direction and the world executes it — semi-automatically, the way a
commander plays, not the way a shopkeeper works.

What a ruler actually does:

| Dial | The decision |
|---|---|
| **Priorities** | Which needs the town chases first, which sites to develop, what to stockpile |
| **Armouries** | Equip the defenders — **NPC garrison and clan members alike** — from the town's own stock |
| **Missions** | Order objectives: take that point, clear that camp, escort that convoy, break that road |
| **Contracts** | Post paid work to the open board, funded from the treasury |
| **Taxes and law** | Rates, tariffs, and what is legal to trade at all |
| **Manor and production** | What the land grows and what the workshops are pointed at |
| **Outlaws** | Suppress the black market, tolerate it, or quietly tax it |
| **Sanction** | Declare a clan outlaw and strip its halls, shops and outposts in your region |

### The interface is the castle, not a new screen

All of it is driven through **dedicated NPCs in the castle**, exactly the way tax rates, the manor and
siege dates are set today — a steward for stores and priorities, a marshal for the garrison and
missions, a magistrate for law, tariffs and outlawry.

That matters for two reasons. It keeps rulership feeling like *holding court* rather than opening a
management panel, and it means no new client work: the chamberlain dialogs this server already runs are
the template.

Two of those are worth dwelling on.

**Equipping players from the town armoury** is the moment the whole economy pays off. The ore a player
mined, hauled by caravan, forged in a player's workshop, stored in the town, is handed to clan members
before a siege. Every stage of the chain is visible in the gear on your allies' backs.

**Missions unify the command layer with the work board.** A ruler's order and a player's contract are
the same object seen from two sides — the ruler issues an objective and funds it; NPC troops carry it
out, and any player can take the same job for pay. That is the asymmetry: **one player is playing an
RTS, and everyone else is the units — willingly, because the pay is good.**

---

## How a player meets the town

Two very different people walk through the gate, and both must find something.

### The adventurer — most players, no economics required

They should never have to *opt in*. The economy meets them where they already are:

1. **They arrive and read the place.** Crowded or thin, guards many or few, shelves full or bare. Ten
   seconds, no menus.
2. **They sell what they killed.** Same habit as always — but now the price reflects real need, and
   Iron Ore fetching four times normal *tells them something*.
3. **They glance at the board.** Work that is relevant right now, paid in XP, adena or goods, priced by
   how badly the town needs it.
4. **They buy, and sometimes cannot.** Out of stock is a nudge toward another town, or toward the
   contract that fixes the cause.
5. **They benefit from the tier without lifting a finger** — better shops because someone else has been
   looking after the place. That free ride is fine. It is what makes them notice tiers at all.

The rule: **an adventurer who ignores the economy entirely still has a completely normal game**, and
still feels the town around them changing.

### The trader — a real playstyle, for the few who want it

Profit comes from exactly three places, and all three should be available:

**Geography — arbitrage.** Buy where it is cheap, sell where it is dear. Requires *several* towns with
genuinely different prices, which is why this playstyle cannot exist on Talking Island alone.

**Transformation — industry.** Buy raw, convert in a workshop, sell finished. The steadiest and least
dramatic income, and the one that keeps paying while offline.

**Risk — smuggling and war.** Move contraband past tariffs, or stock up before a siege and sell into the
shortage. The highest margins, earned by accepting the chance of losing everything.

Practical texture: cargo has **weight**, so capacity is a real constraint and pack animals are a genuine
upgrade rather than a cosmetic. A trader's career progresses through carrying capacity, protection, and
better information.

### Traders need information, and that is a design gap

Arbitrage without price knowledge is guessing. If a player cannot find out that Dion is paying double
for medicine, the entire trading playstyle collapses into wandering hopefully.

So information has to be obtainable — and ideally *earned* rather than given:

- NPC merchants and travellers gossip about other towns, with the freshness of the news varying
- Caravan masters report what they saw on the road
- A merchants' guild sells price reports — recent ones cost more
- Or simply: go and look, and know something your rivals do not

Treating information as a resource turns scouting into a trade skill, and gives the well-connected a
real edge. **This is currently unspecified and needs deciding before traders can function.**

---

## Destruction: ruins, rebuildable

Destruction is a **campaign, not an event**: cut the roads, take or burn the sites, starve the
population, collapse the tier, and finally break the town. Visible progress on both sides, every stage
reversible by defenders who show up.

A razed town drops to **tier 0 — ruins.** Shops gone, services gone, NPCs scattered. It stays on the map
as a wound, and rebuilding costs real effort. That is a genuine catastrophe worth fighting to prevent,
without any single week permanently deleting content on a 20-player server.

The ruins themselves become content: a site to reclaim, and a standing monument to whoever did it.

---

## The crowd: population you can see

**The size of the crowd is the town's prosperity, displayed without a single line of UI.**

Population is not an abstract number — it is **how many people are actually on the street.** A tier-1
outpost has a handful of figures. A tier-5 metropolis is busy. And when a town starves or is put under
siege, the streets visibly thin out. Nobody needs to open a panel to know a town is dying; they can see
it as they ride in.

### Citizens live somewhere and go shopping

Citizens emerge from buildings, walk to shops, buy what they need, and go home. Crucially, they buy from
**player workshops and stores as readily as from NPC shops.**

That single detail does something no status board ever could: **the town's consumption stops being a
number ticking down and becomes something you watch happen.** The demand in the ledger is the person
walking through the door.

And it makes player industry feel genuinely alive. Your forge does not "sell to the town" as an abstract
transaction — it has *customers*. They arrive while you are offline, they take goods off your shelf,
they leave adena behind. A busy shop looks busy.

It also makes shortage visceral. When there is no bread, you see people going to the baker and leaving
with nothing.

### How this stays affordable

The honest constraint: roughly 2,000 NPCs can run a decision routine cheaply, but **only ~250 can be
walking at once** — movement is single-threaded and shared with player movement. A crowded world is not
free.

So the crowd is a **live sample, not a full simulation.** The ledger consumes at its own rate; when
players are present, a proportion of that consumption is *performed* by visible shoppers. Empty towns
tick their numbers quietly and spend nothing on animation. Each town gets a crowd budget scaled to its
tier, drawn from a global pool, so a busy capital looks busy without every village paying for it.

The result is that the performance ceiling and the design goal point the same way: **the crowd is
thickest exactly where players are, which is exactly where it matters.**

### A named cast on top

Above the crowd sits a **named cast per town — scaled to its tier**, from a couple of figures in an
outpost to a proper civic roster in a metropolis. These get real individual life: a job, a schedule, a
wallet, and opinions about how things are going. The blacksmith who tells you he is out of iron, and
means it.

Those are the NPCs players learn names for. Growth is felt not only as bigger numbers and better shops,
but as **new people arriving** — a town at tier 4 has faces that a tier-2 village simply did not.

---

## The numbers everyone reads

Traders need information, rulers need instruments, and both wants are answered by the same thing: **a
region publishes its vital statistics.**

| Indicator | What it tells you |
|---|---|
| **Output** — the regional GDP | How much the region produces per cycle. The headline health number |
| **Tier** | What the town has unlocked, and what it must sustain to keep it |
| **Population** | How many mouths — and how many hands |
| **Satisfaction** | Whether people are working well, or drunk, sick and idle |
| **Supply and demand index** | Per good: current price against its baseline. **This is the arbitrage signal** |

The supply/demand index is the one that makes trading a playstyle. *Medicine in Gludin: 240% of
baseline.* That is a sentence a trader can act on, and it is the difference between commerce and
wandering hopefully.

### Information should be earned, not given

Publishing your *own* region's numbers is free — you live there. Knowing **someone else's** should take
effort, and that is where the gameplay is:

- **NPC gossip** — travellers and merchants mention other towns, with news as stale as their journey
- **Caravan and ship masters** report what they saw and what it sold for
- **A merchants' guild** sells price reports, with fresh data costing more than old
- **Go and look yourself** — and know something your rivals do not

Treating information as a resource makes scouting a trade skill and gives the well-connected a genuine
edge. It also gives the harbour another role: ships bring news along with cargo, so a blockaded port is
an *ignorant* port.

For a ruler these same numbers are the instrument panel — the thing that tells them whether the answer
this week is goal 1, goal 2 or goal 3.

---

## Making it readable

A simulation nobody can see does not exist. Four surfaces, in order of value per effort:

1. **Shop shelves** — finite stock and moving prices. The Interlude client already renders this, with no
   client patch.
2. **The quest board** — the world stating its own needs, with pay that encodes urgency.
3. **NPC chatter** tied to the ledger — the cheapest immersion in the design.
4. **Caravans on the road** — supply as something you see, not something you read.

---

## Starting ground: the Gludio domain

The pilot is **the whole Gludio region**, not one village. The core already defines it — three towns
answer to castle 1:

| Town | Role in the region |
|---|---|
| **Talking Island Village** | the protected starter market, and its harbour |
| **Gludin Village** | second market, second harbour |
| **Town of Gludio** | the seat — castle, treasury, clan halls |

*(Confirmed in `MapRegionTable.getAreaCastle` — Talking Island, Gludio and Gludin all map to castle 1.)*

This is a far better pilot than a single village, because **it can exercise essentially the whole
design**:

- **A real castle**, so rulership, treasury, tax, garrison, missions and outlawry all have somewhere to
  live
- **Clan halls in Gludio**, so the clan-workshop tier works
- **Three towns with different needs**, so prices genuinely differ — which is the one thing that makes
  the trader playstyle possible at all
- **Two harbours** as the link to the wider world, and the natural home of imports and exports
- **Roads between them** for caravans, including the route already built and running
- **A siege castle**, so the region is contested by design rather than by invention

And it keeps the newbie protection intact *inside* the region: **Talking Island stays the guaranteed
market** — never dry, never razed — while Gludio and Gludin carry real scarcity and real risk. New
players learn the systems somewhere safe, then walk into the contested part with those skills already
in hand.

The sites are already on the map: forests for wood, fields for grain and reeds, stone, the harbours for
fish and imports, the Elven Ruins for rare materials, and the Abandoned Camp as a ready-made standing
threat.

The only thing this region cannot demonstrate is **inter-regional trade** — routes to Dion and beyond —
which is a natural second step rather than a gap.

---

## Implementation path

The governing rule: **every stage must be worth playing on its own.** If a stage only pays off once a
later one lands, it is the wrong stage. Anything that cannot justify itself alone gets cut or deferred.

### Stage 0 — Foundations and the tuning apparatus *(invisible, keep it small)*

A settlement record — tier, population, treasury, stockpile — and the need↔item mapping.

**Plus the tuning apparatus, which is not optional**: config-driven constants with hot reload,
transaction tagging for telemetry, periodic indicator snapshots, and admin inspect/override commands.
Since every real number will be found by observing live play, this has to exist before there is anything
to observe.

The discipline here is to build only what stages 1 and 2 actually consume, and resist building the whole
schema for systems that may never arrive.

### Stage 1 — The town comes alive *(the "look at that" slice)*

**Finite shop stock driven by the town stockpile, plus the shopping crowd.** Citizens leave buildings,
walk to shops, buy, go home. Shelves visibly drain.

This is the right first stage because it is **immediately visible, needs no new UI, and requires nothing
of the player.** It changes how the town feels on the walk from the gate to the smith. If nothing else
ever shipped, the server would still be better for it.

### Stage 2 — Tier and the reward ladder *(the goal)*

Gate the specialists — trainers, scholars, stables, enchanters, priests — behind town tier. Almost
entirely re-gating NPCs that already exist, so the effort is small and the motivation is enormous.

Deliberately **before** players can do much about it: knowing what tier 4 unlocks is what makes them
want to push for it.

### Stage 3 — Players supply the town *(the means)*

The town buys from players at need-scaled prices out of a real treasury, and the work board posts supply
orders, bounties and escorts. Now the loop closes and player action moves the numbers.

Exports come in here too — surplus leaves the region and foreign coin arrives — because the treasury has
to be able to refill before it is allowed to drain.

### Stage 4 — Sites, harbours and trade routes *(the map)*

Sites produce and pay tax — NPC-owned first, so the map works before anyone claims anything, then
acquirable by players and clans. Caravans haul overland; ships run harbour to harbour. Shortage becomes
geographic across the three towns.

Builds directly on the caravan system that already works, and the harbour turns exports from a number
into a place that can be blockaded.

Trading becomes a real playstyle here, since three towns with different needs finally produce price
differences worth travelling for — which means **the information layer must be decided by this stage.**

### Stage 5 — Workshops *(the middle of the chain)*

Rented plots and clan halls converting raw into finished goods. The value-add layer, and the first thing
that pays players while they are offline.

### Stage 6 — Rulership, garrison, outlawry *(the political half)*

Gludio's castle makes this testable inside the pilot region: the steward, marshal and magistrate NPCs,
the armoury, missions, tax and law, the garrison funded from the treasury, and clan sanction.

It comes last because it is the half most likely to need rebalancing, and because it is only meaningful
once there is a working economy to rule over.

### Stage 7 — Beyond the region

Inter-regional trade to Dion and further, contested sites, and destruction. The first region proves the
systems; the second makes them worth fighting over.

---

## Critical review — where this does not yet hold together

An honest pass over the design as it stands. The spine is sound; these are the parts that are not.

### 1. Scope is the single biggest risk in this document

We have accumulated roughly **fifteen interlocking systems**: resources, needs, tiers, sites, caravans,
workshops, clan halls, garrisons, generated contracts, outlawry, contraband, rulership, crowds, NPC
shopping, and the reward ladder. Each is reasonable. Together they are years of work for a small team,
and **most of them only pay off once the others exist.**

This is how ambitious server overhauls die: half-built, with nothing playable.

The mitigation is non-negotiable — **every stage must be enjoyable on its own, with no dependency on the
next one arriving.** If a stage is only worthwhile once stage 4 lands, it is the wrong stage.

### 2. Scope of the pilot — resolved

An earlier draft claimed the pilot could not test the political half. That was wrong: the region runs on
**Gludio's castle**, which brings rulership, treasury, garrison, clan halls and outlawry into scope, and
three towns give the price differences that traders need.

What remains genuinely out of reach in the first region is only **inter-regional trade** — routes to
Dion and beyond — and **full destruction**, since Talking Island must stay protected. Gludio and Gludin
can carry real scarcity and real risk, so even that is only partly deferred.

### 3. The money supply — closed, by exports

An earlier draft called this an open hole. On reflection it is not, for two reasons:

**Town shops sell as well as buy.** Players purchasing gear, consumables and services return adena to
the treasury, alongside the tax. Money moves both ways, not one.

**The town is bounded, so it degrades instead of breaking.** A town only pays what it *has* and only for
what it *needs* — prices fall as the stockpile fills and the treasury empties. A broke town does not
crash the economy; it simply stops buying, which is a legitimate and playable state that pushes players
toward selling to its customers instead.

**Exports are the designed injection.** A region that produces more than it consumes sells the surplus
abroad, and that is genuinely new money entering. This is the right shape for three reasons:

- The money supply **grows with productivity** — exactly the behaviour the design wants to reward
- It gives caravans a second purpose: **export convoys leaving the region**, which are just as raidable
  as inbound ones
- It replaces the current uncapped faucet, where vendor sales mint adena from nothing at half reference
  price with no limit at all. On an x10 server, that is an improvement in its own right.

What still needs setting is the **export rate** — the exchange between surplus and foreign coin. Too
generous and it is a farm; too mean and prosperity has no reward. That is a tuning number, not a
structural hole.

### 4. Everything expensive competes for the same budget

The crowd, caravans, patrolling garrisons and the players themselves all draw on the **same ~250
concurrent movers**, because movement is single-threaded. The crowd is the most player-visible idea in
this document and also the most expensive. It needs a hard, enforced budget from day one, not a limit
discovered later under load.

### 5. New items mean client patches

Wine, tobacco, narcotics and reagents **do not exist in Interlude.** Every new item requires patching
`Client/system` and redistributing it to every player. That is possible here — this server already
hand-patches client files — but it is a real release cost and a real barrier to iteration.

**Strong recommendation: build the first version entirely from items that already exist.** Interlude
already has crops, Iron Ore, Coal, Stem, Thread, food, potions and scrolls. Prove the economy with
those, and only mint new items once the systems have earned it.

### 6. The authoring burden exceeds the coding burden

Needs mappings, resource catalogues, site definitions, NPC routines, contract templates, tier tables,
recipes per workshop. **Most of this work is content, not code**, and content is the part that quietly
takes three times as long as expected. Design the data formats to be editable without a rebuild.

---

## Risks worth watching

| Risk | Why it matters |
|---|---|
| **Chore creep** | Addressed by design: stewardship is semi-automatic and command-level, never micromanagement. If a ruler is ever clicking through inventories item by item, the design has failed. |
| **Clan lock-out** | Addressed: every clan holds a clan hall as its workshop and storefront, and individuals can rent a plot or work the board. Not holding the castle costs you authority, never access. |
| **Invisible simulation** | The likeliest failure — see below. |
| **Quest spam** | **Accepted.** Generated work may sometimes feel repetitive; we take that trade for a world that always has something to ask for. |
| **Scope** | This touches shops, crafting, NPCs, spawns, sieges and persistence at once. It must ship as a thin slice that is fun alone. |

---

### The invisible-simulation trap, explained

This is the failure worth guarding against hardest, because it is silent.

You build the whole thing. Stock drains, prices move, populations shift, sites degrade — every number
correct. Then a player logs in, plays for three hours, and **notices nothing.** The changes were too
slow, too small, or displayed somewhere they never looked. The result is real server load producing zero
player experience. The tell is players asking *"is the economy thing live yet?"* a month after it
shipped, or knowing it exists but being unable to name one thing it changed for them.

It happens because simulations get built for the designer looking at a database, not for the player
looking at a screen.

**The test to apply to every single feature here:**

> If this number changed by 50%, would a player notice within one session, without opening a menu?

If the answer is no, it needs a readout — or it should not be built.

This design already answers the test well, and deliberately:

| The number | How a player perceives it |
|---|---|
| Population and prosperity | **The crowd** — how many people are on the street |
| Stockpile levels | Shelves that are visibly empty, and shoppers leaving with nothing |
| Shortage severity | **Pay on the work board**, and what each buyer NPC is offering |
| Treasury health | What the town can afford to offer as rewards |
| Trade volume | Caravans on the road and **ships at the quay** |
| Site productivity | Fields worked or fallow, mines busy or abandoned, who owns them |
| Regional income | How busy the **harbour** is — a blockaded port is visibly idle |
| Tier | Which specialists have opened, and how many |
| Garrison funding | How many guards are standing there, and what they are carrying |

Every one of those is legible from horseback — and note how many are **outside the walls.** The farm,
the mine and the quay say as much about a region's health as the marketplace does, and a player riding
in has usually passed all three before reaching the gate.

---

## Feasibility, briefly

Enough exists that this is assembly more than invention:

- **Finite, restocking, per-settlement stock with treasury-gated reordering** — the castle Manor system
  is exactly this, working today
- **Client-visible limited stock** — the buy window already sends real counts and hides sold-out lines
- **Production chains** — 870 recipes plus a 256-seed chain whose outputs are already recipe inputs
- **Settlement money, tax and clan ownership** — castle treasury, tax and clan-holds-castle already work
- **Quest delivery** — the quest engine and community board already exist; no client work needed
- **Workshops** — clan halls already provide ownership, recurring lease with eviction, per-facility
  upgrade levels, a Grade/tier field and a self-rescheduling upkeep task. `merchant_lease` is a
  half-built, commented-out attempt at exactly this
- **Player shops** — the offline-shop mod already establishes player-run commerce on this server
- **Caravans** — built, dispatchable, lootable, already on the road

Genuinely new: **settlement tier and population**, **developable sites**, **the generated-work board**,
**NPC wallets and routines**, **a sell-side price model**, and **per-NPC persistence**.

---

## Settled so far

| Question | Decision |
|---|---|
| Purpose | All three — development, defence, growth — unified by stewardship |
| Scarcity | Everything can run out; local not global, always attributable, starter town protected |
| NPC depth | Town ledger + a named cast sized to the town's tier |
| Population | Visible headcount — the crowd on the street *is* the prosperity readout |
| Citizen behaviour | Leave buildings, shop at NPC **and player** stores, go home. Consumption performed, not just counted |
| Workshop types | Forge, scriptorium, apothecary, library, weaver, bakery/brewery, jeweller — specialist ones tied to the fitting class |
| Destruction | Ruins, rebuildable — tier 0, not deletion |
| Stewardship | Clan-owned territory, with an open paid-work board as the bridge for everyone else |
| Threats | Balanced; NPC pressure scales up when player activity is low |
| Garrison | Town treasury pays guards; town stockpile arms them. Wealth and supply become defence |
| Quest rewards | XP, adena or goods — drawn from the town's real treasury and stock, never minted |
| Luxury goods | Jewellery, wine, buff scrolls, complex food |
| Tier services | Specialists unlock by tier: pet shop, skill masters, scholars, stable/striders, enchanters, priests, scholar-masters, Master of Air/wyverns. No repairs |
| Progression floor | Baseline skill training and spellbooks always available in the protected starter town — the ladder gates quality and prestige, never the ability to level |
| Resources | Three bands — bulk (wood, stone, reeds, grain, ore, hide), refined, and specials (wine, tobacco, narcotics, reagents, gems) |
| Contraband | Rulers may tax or ban specials; smuggling and black markets are the consequence |
| Outlawry | Standing held **per authority**, not globally — outlaw here, welcome there |
| — causes | War · holding a site and refusing tax · raiding and killing · being **caught** smuggling |
| — costs | Garrison hostile on sight · services and the reward ladder closed · bounty posted · black market only |
| — symmetry | Player rebels and NPC bandits are the same object to the system |
| — redemption | Fines, restitution, contract work, or amnesty granted by the ruler |
| Rulership | Asymmetric RTS — priorities, armouries, missions, contracts, tax and law. No micromanagement |
| — interface | Dedicated castle NPCs (steward, marshal, magistrate), like tax and manor today. No new screens |
| — sanction | A ruler may outlaw a clan and strip its halls, shops and outposts in the region |
| Scales of holding | Plot (any player) · clan hall as clan workshop (any clan) · castle (the ruler) |
| Armoury | Rulers equip NPC garrison **and** clan members from the town's own stock |
| Missions | A ruler's order and a player's paid contract are the same object seen from both sides |
| First region | **The Gludio domain** — Talking Island Village, Gludin Village and Town of Gludio, all under castle 1 |
| — protection | Talking Island stays the guaranteed starter market; Gludio and Gludin carry real scarcity |
| Money in | Player purchases and tax · **tax from every owned site** · citizen wages returning · **exports through the harbour** |
| Money out | Buying from players, contract rewards, wages, garrison pay — all bounded by treasury and need |
| Sites | Farms, mines, quarries, harbour berths — **NPC-owned by default**, acquirable by players and clans |
| — obligation | Every owner owes regional tax; refusing it is the concrete road to outlawry |
| — resilience | NPC ownership means the economy runs at full strength with nobody logged in |
| Sea trade | Trading ships harbour-to-harbour: bulk volume, fixed routes, vulnerable at the port chokepoint |
| Buyers | Distinct buyer NPCs — grain factor, quartermaster, workshop suppliers, harbour merchant |
| **The objective** | A town at maximum tier, or as near as you can hold it |
| Priority ladder | Goal 1 survive threats · Goal 2 meet basic needs · Goal 3 grow deliberately |
| Satisfaction | A core stat beside population and tier. Luxury and vice raise it; **excess lowers output** — sickness, idleness |
| Vice policy | Prohibition and tariffs become productivity decisions, not moral ones; flooding a rival with cheap vice is a real attack |
| Acquiring a site | Buy from its NPC owner · granted by the ruler as patronage · **taken by force**, outlawing you only if witnessed |
| Statistics | Regions publish output/GDP, tier, population, satisfaction and a **supply-demand index** — the arbitrage signal |
| Information | Own region free; others earned through gossip, caravan and ship masters, guild reports, or travelling |
| New items | Wine, tobacco and narcotics get **real items — raw and finished** — in one batched client patch |
| — ids | Custom range from **10000** (highest in use is 9208); `custom_etcitem` etc. exist, are loaded, and are empty |
| — client cost | Only `ItemName-e.dat` + `etcitemgrp.dat`; **reuse existing icons** and no new textures are needed |
| Growth curve | Exponential cost per tier, so equilibrium settles at 3 with no artificial cap |
| — player margin | Region self-sustains at tier 2–3 on **NPC production alone**; players push it to 4–5. Works at any online population |
| — decay | Tier must be *sustained* — needs unmet for 3 cycles costs a tier. Starter market floors at 2 |
| — tuning | All curve parameters in config files, never constants. Instrument first, tune from observed play |
| Tuning apparatus | Built in **stage 0**: hot-reloadable config, transaction telemetry via the existing `process` tag, periodic indicator snapshots, admin inspect/override |
| Export pricing | **Exports are the worst price available** — a floor against collapse, never an income strategy |
| Satisfaction model | Town-level consumption rate, diminishing returns then a penalty threshold; the ruler sets a policy, not a micromanaged dial |
| Closest prior art | **Mount & Blade: Bannerlord** — villages, workshops, prosperity, caravans, fiefs. Shipped and playable |
| Workshops | Player/clan-held plots in town, converting raw materials into finished goods |
| — tenure | Rented, evicted on non-payment; plot count and specialisations gated by town tier |
| — automation | Produces offline while stocked, then idles — the limit is your supply chain |
| — risk | Lost if the town is razed; acceptable only while razing stays a slow, visible campaign |

---

## Prior art: how other games solved this

### The closest thing that already exists — Mount & Blade: Bannerlord

Worth studying before anything else, because it has **shipped almost exactly this design**:

| Bannerlord | Our equivalent |
|---|---|
| Villages bound to a town, producing specific goods | Sites bound to a region |
| **Player-owned workshops in towns** that convert local raw materials automatically and pay daily | Workshops |
| Prosperity per settlement, driving shop stock, militia and garrison size | Tier, population, garrison |
| Prices set per settlement by local supply and demand | The supply–demand index |
| Player-funded caravans that trade autonomously and can be attacked | Caravans |
| Raiding a village drops its prosperity and hurts its owner | Raiding sites and roads |
| Fiefs **granted by your ruler**, giving tax income and owing service | Patronage and site tax |

That is a working, shipped proof that this whole shape is playable. Steal the structure.

**But note what Bannerlord gets wrong, because we can avoid it:**

- **The economy is invisible to most players.** They fight for twenty hours and never notice prices
  exist. Our crowd, shelves and buyer NPCs are the direct answer.
- **Workshops are set-and-forget.** You choose a type once and then it is a passive trickle with no
  further decisions. Ours must keep asking something of the owner — what to make, what to stock, what to
  do when the local price of tannin doubles.
- **Caravan attacks feel random, not strategic.** Nobody is *targeting* you. In our design raiding is
  aimed at a rival's tier and income, which makes it personal and legible.

### Others worth borrowing from

| Game | The lesson |
|---|---|
| **EVE Online** | Regional markets with real price gaps, full player manufacturing, dangerous hauling. Also: publish the economic data and **tune the faucets continuously**. And accept that most players never touch industry — that is fine |
| **Albion Online** | Territorial full-loot economy where transport risk *is* the balancing mechanism |
| **Foxhole** | Logistics genuinely works as gameplay — but only when hauling is short and social, never a lonely chore |
| **Anno / Victoria** | Tiered population needs, with each tier costing exponentially more. Victoria's "standard of living" is our satisfaction stat |
| **RimWorld** | The vice model: diminishing returns, then a threshold, then real harm — plus a **policy** you set rather than micromanaging |
| **Tropico / Frostpunk** | Happiness as a dial with genuine policy trade-offs, not a bar to fill |
| **Wurm / Life is Feudal** | Upkeep and decay are what stop infinite accumulation and make *holding* something meaningful |

---

## How I would settle the open questions

**The satisfaction curve.** Take Victoria's structure and RimWorld's shape. Needs split into
life (food, water), everyday (clothing, medicine) and luxury (wine, entertainment). Satisfaction gain
per unit of luxury **falls as consumption per head rises**, and past a threshold an output penalty kicks
in — sickness and idleness. Model it **at town level, never per NPC**: a consumption rate against
population, not addiction tracked on individuals. And expose it as RimWorld does — a **policy the ruler
sets** (permitted, taxed, banned), not a thing they micromanage. Find the peak by playtesting; theory
will not find it.

**The growth curve and the export rate.** Make each tier cost **exponentially** more than the last, as
Anno does. Equilibrium then settles at tier 3 on its own, with no artificial cap, and tier 5 is a real
achievement rather than an inevitability.

For exports, one structural principle settles most of the tuning:

> **Exports should be the *worst* price available, not the best.**

They are a floor that stops a region collapsing, never an income strategy. Selling to another player, or
to a town that actually needs the goods, must always beat dumping surplus at the quay. Set that way, the
export rate stops being a delicate balance and becomes a safety net — and player-to-player trade stays
the most profitable thing in the game, which is where we want the activity.

**The player in the loop — the part that makes tuning hard.** Players do not only *supply* the town;
they also *drain* it, buying gear, consumables and services. So the equilibrium has three moving parts:
NPC consumption, player consumption, and player supply. Tune for five players and fifty players trivially
max every town; tune for fifty and five players can never move anything.

The answer is to make the world stand on its own and let players be the margin:

> **The region self-sustains at tier 2–3 on NPC production alone. Players are what push it to 4 and 5.**

That works at any population. With nobody online the region idles at tier 3 — alive, functioning,
unremarkable. Player effort is visibly the difference between *a town* and *a city*, and player
consumption is a real drag that has to be outpaced. Nothing needs rescaling when the server is quiet or
busy, and no one ever logs in to a dead world.

**New items.** Verified against the data: the highest item id in use is **9208**, and
`custom_etcitem` / `custom_armor` / `custom_weapon` exist, are already loaded by the core, and are
**completely empty**. So:

- Start the economy range at **10000**, well clear of retail content, in the custom tables
- Reserve blocks up front — e.g. 10000–10099 raw vice materials, 10100–10199 finished goods,
  10200+ for later economy items
- **Batch them into one client patch**, not a drip

The client cost is smaller than expected. New items need only **`ItemName-e.dat`** (the display name) and
**`etcitemgrp.dat`** (the icon reference), both of which this repo already tracks and hand-patches. Point
the new items at **icons that already ship** and no new texture packages are required at all — the patch
is two small data files, which is about as painless as client distribution gets.

**How much statistics ship early.** EVE's lesson is that aggregate figures are for enthusiasts, but the
**per-good price signal is what everyone acts on**. So ship the supply–demand signal early — it doubles
as the shortage readout and costs almost nothing — and leave GDP, output and population aggregates for
later, as a ruler's instrument panel.

---

## Proposed answers to the last three

### 1. The satisfaction curve

Use a single-peaked curve with two parameters, so it is tunable by anyone without reading code:

```
bonus(L) = A · (L / Lopt) · (2 − L / Lopt)
```

where `L` is luxury consumed per citizen per cycle and `Lopt` is the optimum. It rises with diminishing
returns to `+A` at the optimum, returns to zero at **twice** the optimum, and goes negative beyond.

Suggested starting values — define the luxury unit so that the optimum is exactly 1, which makes every
later conversation legible:

| Consumption | Bonus | Reads as |
|---|---|---|
| 0 | 0 | fed, joyless, no bonus |
| 1 × optimum | **+25** | contented and productive |
| 2 × optimum | 0 | drinking the profits |
| 3 × optimum | −25 | sick, idle, output falling |

Output then scales as `multiplier = 0.75 + satisfaction/100`, clamped to `[0.5, 1.25]` — so satisfaction
swings productivity by roughly a third either way. Enough to matter, not enough to spiral.

**Add one feedback loop:** excess vice **raises medicine demand**. Overindulgence does not merely reduce
output, it creates a new shortage — which shows up on the work board as an unexplained run on medicine,
and lets an observant player diagnose a town that is drinking itself sick.

**How to find the real optimum:** do not guess it. Ship with generous defaults, **log actual consumption
per capita**, and set `Lopt` to the observed median after a few weeks of real play. This is EVE's
approach — instrument, publish, tune continuously — and it beats any amount of spreadsheet work.

**Put both parameters in a config file**, not in code. This codebase already runs on editable property
files; tuning must never require a rebuild.

### 2. Growth constants and the NPC baseline

**Shape:** `cost(tier) = base · k^tier`, with **k ≈ 2.8**. Roughly:

| Tier | Sustained supply per cycle | Who provides it |
|---|---|---|
| 2 | 1,000 | NPC sites alone |
| 3 | 2,800 | **NPC sites alone — the unaided ceiling** |
| 4 | 8,000 | NPC + real player effort |
| 5 | 22,000 | NPC + sustained, organised player effort |

Set NPC production to sustain **exactly tier 3**. That single decision makes the whole thing
population-proof: an empty server idles at 3, and every tier above it is visibly the players' doing.

**Derive the constants empirically, in three steps:**

1. Measure one player's material output in an hour of ordinary grinding, at this server's actual rates
   (x20 items, AutoLoot on — the number will be large).
2. Set tier 4 so it needs roughly *three active players contributing part-time*, and tier 5 roughly
   three times that — an organised clan's full attention.
3. Watch where regions actually settle, and adjust `k` rather than individual tiers.

**Tier must be sustained, not achieved.** Needs unmet for **3 consecutive cycles** costs a tier. Without
decay, one big dump permanently maxes a town and the entire game ends. With it, tier 5 is a standing
commitment — which is exactly what makes losing it dramatic.

**One floor:** the protected starter market never drops below tier 2, whatever happens.

### 3. The client patch

**Ship it with stage 4**, when sites and resources arrive — that is the first point at which vice goods
can actually be produced and moved. Stages 1 to 3 use existing items only, so the patch is not on the
critical path for anything earlier.

**Choosing icons:** read the existing icon list out of `etcitemgrp.dat` and map the new goods onto
**generic** existing art — powders, liquids, plants, raw materials — rather than anything visually
distinctive that players already associate with a specific item. Document the mapping in the datapack so
it survives.

**The rollout is safe to do softly.** An unpatched client receiving a new item shows a blank or garbled
name, but the item still functions — it can be held, traded and sold normally. So the patch can be
announced and distributed without gating logins, and the goods can be introduced quietly while adoption
catches up. Two small `.dat` files, no textures, no downtime.

---

## Built for tuning

Since the real numbers only emerge from live play, **the tuning apparatus is part of stage 0, not a
later addition.** A system that cannot be adjusted while running cannot be tuned live, and the whole
plan above quietly fails.

Four requirements, none expensive:

**Every constant lives in config, and reloads without a restart.** Rates, curves, tier costs, export
prices, decay timings. This codebase already runs on editable property files and already has admin
reload commands — follow that pattern and never hardcode an economic number.

**Instrument every transaction from day one.** This is nearly free: the core already threads a
free-text `process` tag through every item movement ("Buy", "Sell", "Manufacture", "Manor"), and it
lands in the item log. Give the economy its own tags and the transaction history is captured with almost
no new code. Without this, "tune from observed play" has nothing to observe.

**Snapshot the indicators periodically.** Output, population, tier, satisfaction, stock levels and
prices, written to a table on a timer. **History is what makes tuning possible** — a single instant tells
you the state, but only a trend tells you whether last week's change helped.

**Admin commands to inspect and override.** Read a region's full state, force a tier, set a stockpile,
trigger a cycle. Essential for testing scenarios that would otherwise take days of real play to reach —
a starving town, a maxed city, a blockaded port.

The first thing worth measuring, before any balance decision, is **one player's material throughput per
hour** at this server's real rates. Every other number in the design is downstream of it.

---

## Still genuinely unknown

Only the numbers that real play produces:

- Where `Lopt` actually lands once people are consuming
- Whether `k = 2.8` puts tier 4 within reach of this server's real population
- One player's true material throughput per hour at current rates — the input every other number
  depends on, and the first thing worth measuring

---

## Implementing it

### Rule one: a small engine, everything else in data

This codebase has four places code can live, and they differ enormously in what it costs to change
them. That difference should drive the entire architecture.

| Where | Cost of one change | What belongs there |
|---|---|---|
| `head-src/` Java core | Rebuild, copy the jar to **both** servers, restart both | The data model, the tick loop, packet hooks |
| `config/*.properties` | Edit + reload | **Every tuning number, without exception** |
| `data/*.xml`, `*.csv` | Edit, reloadable | Content: resources, needs, tier tables, site definitions |
| `data/html/` | Edit — lazy-cached, no restart at all | All UI |

Every Java change costs a full build-and-restart of a live server. So:

> **Anything that will be tuned or authored must not be Java.**

The Java layer is a small engine that reads data and runs a clock. If a balance change requires a
rebuild, the "tune it live" plan quietly dies.

### Rule two: start by measuring, not building

The very first deliverable should change no gameplay at all: **instrument the economy that already
exists.**

Log what players sell, buy, earn and destroy today, tagged by transaction type. This is nearly free —
the core already threads a `process` string through every item movement — and it answers the question
every balance number depends on: **what does one player actually produce in an hour at x20 rates?**

Doing this first means the design gets built against measured reality rather than guesses, and it is a
real, shippable, zero-risk piece of work.

### Rule three: additive, never invasive

The buy and sell paths are live code that every player touches constantly. A bug there breaks shopping
for everyone, on a running server.

So **do not modify the existing merchant path.** Add new NPC types alongside it. A stock merchant keeps
working exactly as it does today; an economy-aware merchant is a different class on different NPC ids.
Nothing that already works can regress.

And the whole system takes **one kill switch** — `EnableEconomy = False` — that returns the server to
present behaviour. Non-negotiable for a live server.

### The work packages

| | Package | What it is | Needs |
|---|---|---|---|
| **A** | Settlement core | Settlement model, stockpile, treasury, population, tier. SQL + manager, loaded at boot, write-through | — |
| **B** | Resource catalogue | `resources.xml`, `needs.xml`, `tiers.xml` and their loaders. The `material` item-type fix | — |
| **C** | Economy tick | The clock: consumption, tier evaluation, satisfaction. Manor's task pattern | A, B |
| **D** | Tuning apparatus | Config reload, `//eco` admin commands, transaction telemetry, indicator snapshots | A |
| **E** | Finite shop stock | Economy-aware merchant reading stock from the settlement | A |
| **F** | The crowd | Citizen NPCs, bucketed routines, movement budget, shopping | A, C |
| **G** | Buyer NPCs | Grain factor, quartermaster and friends. Need-scaled, treasury-bounded prices | A, B, C |
| **H** | Contract board | Generation from real state, HTML board, rewards drawn from treasury and stock | A, C, G |
| **I** | Sites | Site model, ownership, production, tax | A, B |
| **J** | Caravans and ships | Cargo drawn from and delivered to stockpiles; sea routes | A, I |
| **K** | Workshops | Plots, rent, conversion. Clan hall patterns reused | A, B, I |
| **L** | Garrison | Guards scaled by treasury, equipped from stock | A, C |
| **M** | Rulership | Steward, marshal and magistrate NPCs | most |
| **N** | Outlawry | Standing per settlement, sanction, bounties | A, I |

### Mapping packages to stages

| Stage | Packages | What a player notices |
|---|---|---|
| **0** | D first, then A + B | Nothing — but you now have real numbers |
| **1** | E + F | Shelves drain, streets fill. The town feels alive |
| **2** | tier gates on C | Specialists open and close with the town's fortunes |
| **3** | G + H | Their own effort moves the numbers |
| **4** | I + J | The map matters; goods come from somewhere |
| **5** | K | Their workshop earns while they sleep |
| **6** | L + M + N | Rule, defend, and be defied |

**A and B are independent** and can be built in parallel; D only needs A's model. Everything after that
is a chain, so with one developer it is simply sequential.

### Where the code lives

A self-contained package, so it can be reasoned about separately and disabled wholesale:

```
com.l2jfrozen.gameserver.economy
   model/        Settlement, Stockpile, Need, Site, Workshop, Contract
   managers/     SettlementManager, EconomyTickManager, ContractManager, SiteManager
   datatables/   ResourceTable, NeedTable, TierTable
   instance/     buyer NPCs, board NPCs, citizens
   telemetry/    transaction log, indicator snapshots
```

Config in `config/economy/`, content in `data/economy/`, UI in `data/html/economy/`.

### Build-specific risks

**Settlement identity.** Three overlapping town-to-castle maps already exist and disagree with each
other. Pick one — `MapRegionTable.getAreaCastle` is the most complete — and use it everywhere.

**NPC object ids are not stable across restarts.** Anything persisted per NPC must key off the
`spawnlist` row id, not the runtime object id. Grand boss persistence is the pattern to copy.

**Persistence strategy.** The manor offers both write-through-per-transaction and periodic-save, chosen
by config. Take the same option: write through for money and stock, periodic for the rest.

**The movement budget.** The crowd must have a hard, enforced cap from its first day, not a limit
discovered later under load.
