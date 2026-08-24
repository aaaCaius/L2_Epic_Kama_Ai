/* =============================================================================
   Kingdom simulation core.

   No DOM, no rendering. This file is the part that eventually becomes Java, so
   it stays readable and free of anything browser-shaped.

   Every decision an agent makes is recorded together with the alternatives it
   weighed. That is not instrumentation bolted on afterwards - it is the point.
   An AI whose reasoning cannot be read cannot be tuned, and the economy model
   test already proved that reading a design is not enough to know it works.
   ============================================================================= */

/* ---------- deterministic randomness ------------------------------------- */
// Seeded, so a bad outcome can be replayed and diagnosed rather than argued about.
function makeRng(seed) {
  let s = seed >>> 0;
  return function () {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/* ---------- what the world is made of ------------------------------------ */

const RESOURCES = {
  food:      { label: 'Food',      unlock: 1, weight: 3, rate: 1.00 },
  cloth:     { label: 'Cloth',     unlock: 2, weight: 2, rate: 0.35 },
  materials: { label: 'Materials', unlock: 2, weight: 1, rate: 0.30 },
  medicine:  { label: 'Medicine',  unlock: 3, weight: 2, rate: 0.25 },
  arms:      { label: 'Arms',      unlock: 3, weight: 2, rate: 0.20 },
  luxury:    { label: 'Luxury',    unlock: 4, weight: 1, rate: 0.40 },
};

const RES_KEYS = Object.keys(RESOURCES);
const TIER_NAMES = ['-', 'Outpost', 'Village', 'Town', 'City', 'Metropolis'];

// Every tunable number. None are buried anywhere else.
const DEFAULT_CFG = {
  popPerTier:      120,
  popGrowth:       0.03,
  coverCycles:     3,
  tierUpTicks:     26,   // growth is deliberately slow - a tier is hard-won
  tierDownTicks:   6,
  tierUpFulfil:    0.90,
  tierDownFulfil:  0.70,
  basePrice:       20,
  scarcityK:       3.0,
  priceCap:        2.0,
  treasuryReserve: 20000,
  wagePerHead:     4,
  townIncomePerHead: 40,   // what a citizen's trade yields the town each week
  garrisonCost:    2500,
  threatGrowth:    0.9,
  raidThreshold:   55,
  wildYield:       120,
  standingDrift:   0.4,
  requisitionHit:  16,
  refusalHit:      14,
  complyGain:      6,
  importMarkup:    1.6,
  embargoMarkup:   4.0,
};

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const sum = (a) => a.reduce((x, y) => x + y, 0);
const unlockedFor = (tier) => RES_KEYS.filter((k) => RESOURCES[k].unlock <= tier);

/* ---------- world construction ------------------------------------------- */

function makeWorld(seed, cfgOverrides) {
  const w = {
    tick: 0,
    seed: seed || 7,
    rng: makeRng(seed || 7),
    cfg: Object.assign({}, DEFAULT_CFG, cfgOverrides || {}),
    chronicle: [],
    decisions: [],
    contracts: [],
    lessons: {},
    context: {},
    nextId: 1,
  };

  w.realm = {
    name: 'Gludio',
    treasury: 240000,
    objective: 'SUSTAIN',
    taxRate: 0.15,
    lawPolicy: 'tax',
    army: { troops: 120, equipment: 60 },
    budgets: { marshal: 0, justiciar: 0, envoy: 0, towns: 0 },
    spent: { marshal: 0, justiciar: 0, envoy: 0, towns: 0 },
    // Deliberately incomplete: Gludio can make no medicine and no luxury.
    // That gap is what gives the Envoy a job and makes an embargo lethal.
    endowment: ['food', 'cloth', 'arms', 'materials'],
  };

  function makeTown(id, name, pop, tier, mayorName) {
    return {
      id: id, name: name, pop: pop, tier: tier,
      satisfaction: 60,
      stock: Object.fromEntries(RES_KEYS.map((k) => [k, Math.round(pop * 2)])),
      treasury: 30000,
      fulfil: Object.fromEntries(RES_KEYS.map((k) => [k, 1])),
      overall: 1,
      upTicks: 0, downTicks: 0,
      localTax: 0.10,
      rebel: false, defectedTo: null,
      mayor: { name: mayorName, standing: 60, mood: 'content', order: null, lastRefusal: null },
      shops: [],
    };
  }

  w.towns = [
    makeTown('talking', 'Talking Island Village', 120, 1, 'Reeve Almond'),
    makeTown('gludin', 'Gludin Village', 240, 2, 'Mayor Corran'),
    makeTown('gludio', 'Town of Gludio', 300, 2, 'Mayor Vasek'),
  ];

  const shop = (id, name, sells) => ({ id: id, name: name, sells: sells, stock: 30, sold: 0 });
  w.towns[0].shops = [shop('ti-gro', 'Grocer', 'food'), shop('ti-smi', 'Smithy', 'arms')];
  w.towns[1].shops = [shop('gd-gro', 'Grocer', 'food'), shop('gd-wea', 'Weaver', 'cloth'), shop('gd-smi', 'Smithy', 'arms')];
  w.towns[2].shops = [shop('gl-gro', 'Grocer', 'food'), shop('gl-apo', 'Apothecary', 'medicine'), shop('gl-arm', 'Armoury', 'arms')];

  function makeEnt(id, name, type, makes, rate) {
    return {
      id: id, name: name, type: type, makes: makes, capacity: rate,
      owner: 'npc', stock: 20, treasury: 12000,
      standing: 60, taxOwed: 0, mood: 'content',
      contract: null, lastSoldTo: null, requisitions: 0, hiding: false,
    };
  }

  w.enterprises = [
    makeEnt('farm-n', 'Northfield Farm', 'farm', 'food', 520),
    makeEnt('farm-s', 'Southmarsh Farm', 'farm', 'food', 400),
    makeEnt('past', 'Highmoor Pasture', 'pasture', 'cloth', 330),
    makeEnt('mine', 'Ironhold Mine', 'mine', 'arms', 160),
    makeEnt('quar', 'Grey Quarry', 'quarry', 'materials', 230),
  ];

  w.fortress = {
    id: 'fort', name: 'Border Keep', owner: 'npc',
    commander: { name: 'Captain Ruedi', standing: 65 },
    garrison: 60, equipment: 30, readiness: 0.6,
  };

  w.wildSites = [
    { id: 'cave', name: 'Windy Caves', threat: 20, yields: 'materials', garrisoned: false },
    { id: 'ruin', name: 'Elven Ruins', threat: 14, yields: 'medicine', garrisoned: false },
    { id: 'camp', name: 'Abandoned Camp', threat: 34, yields: 'materials', garrisoned: false },
  ];

  // Neighbours hold what Gludio cannot make. That is the entire point.
  w.neighbours = [
    { id: 'oren', name: 'Oren', endowment: ['medicine', 'luxury'], relation: 'neutral', opinion: 50 },
    { id: 'dion', name: 'Dion', endowment: ['food', 'luxury'], relation: 'neutral', opinion: 55 },
    { id: 'clan', name: 'Clan Ravenhold', endowment: ['materials'], relation: 'neutral', opinion: 45, isClan: true },
  ];

  say(w, 'realm', 'The realm of Gludio begins its records.');
  return w;
}

/* ---------- chronicle and decision recording ----------------------------- */

function say(w, scope, text, severity) {
  w.chronicle.unshift({ tick: w.tick, scope: scope, text: text, severity: severity || 'info' });
  if (w.chronicle.length > 300) w.chronicle.pop();
}

// A decision keeps everything the agent weighed, not only the winner.
function decide(w, agent, options, note) {
  const scored = options.slice().sort((a, b) => b.score - a.score);
  const chosen = scored[0];
  w.decisions.unshift({
    tick: w.tick, agent: agent,
    chose: chosen ? chosen.label : 'nothing',
    why: scored.slice(0, 4),
    note: note || '',
  });
  if (w.decisions.length > 250) w.decisions.pop();
  return chosen;
}

const uid = (w, p) => p + '-' + (w.nextId++);

/* ---------- economy: the corrected model from ecosim.py ------------------- */

function townNeeds(w, t) {
  const out = {};
  for (const k of unlockedFor(t.tier)) out[k] = t.pop * RESOURCES[k].rate;
  return out;
}

function consume(w, t) {
  const need = townNeeds(w, t);
  let weighted = 0, weightSum = 0;
  for (const k of Object.keys(need)) {
    const required = need[k];
    const took = Math.min(required, t.stock[k]);
    t.stock[k] -= took;
    const met = required > 0 ? took / required : 1;
    t.fulfil[k] = met;
    weighted += RESOURCES[k].weight * met;
    weightSum += RESOURCES[k].weight;
  }
  t.overall = weightSum ? weighted / weightSum : 1;

  // Luxury multiplies rather than adds, so wine cannot rescue a starving town.
  // Bread before circuses - finding F8 from the economy model test.
  const lux = need.luxury === undefined ? null : t.fulfil.luxury;
  const luxFactor = lux === null ? 1 : 1 + 0.25 * lux * (2 - lux);
  t.satisfaction = clamp(100 * t.overall * luxFactor, 0, 100);
  return t.overall;
}

function evaluateTier(w, t) {
  const c = w.cfg;
  const next = Math.min(5, t.tier + 1);
  const growPop = c.popPerTier * next;
  let covered = true;
  for (const k of unlockedFor(next)) {
    if (t.stock[k] < growPop * RESOURCES[k].rate * c.coverCycles * 0.9) { covered = false; break; }
  }
  t.upTicks = (t.overall >= c.tierUpFulfil && covered) ? t.upTicks + 1 : 0;
  t.downTicks = (t.overall < c.tierDownFulfil) ? t.downTicks + 1 : 0;

  if (t.upTicks >= c.tierUpTicks && t.tier < 5) {
    t.tier++; t.upTicks = 0;
    say(w, t.id, t.name + ' rises to ' + TIER_NAMES[t.tier] + '.', 'good');
  } else if (t.downTicks >= c.tierDownTicks && t.tier > 1) {
    t.tier--; t.downTicks = 0;
    say(w, t.id, t.name + ' falls to ' + TIER_NAMES[t.tier] + '.', 'bad');
  }
}

function scarcity(t, res) {
  const f = t.fulfil[res];
  return f === undefined ? 0.5 : clamp(1 - f, 0, 1);
}

function priceFor(w, t, res) {
  return Math.round(w.cfg.basePrice * Math.min(w.cfg.priceCap, 1 + w.cfg.scarcityK * scarcity(t, res)));
}

/* ---------- enterprises --------------------------------------------------- */

// What a town wants to hold: enough to feed the town it is trying to become.
function growthTarget(w, t, res) {
  const next = Math.min(5, t.tier + 1);
  const growPop = w.cfg.popPerTier * next;
  return growPop * RESOURCES[res].rate * w.cfg.coverCycles;
}

function enterpriseProduce(w, e) {
  if (!e.makes) return;
  const moodMult = e.mood === 'resentful' ? 0.7 : e.mood === 'wary' ? 0.85 : 1.0;
  const hide = e.hiding ? 0.6 : 1;
  e.stock += Math.round(e.capacity * moodMult * hide);
}

// An enterprise sells to whoever offers best. It is nobody's subordinate.
function enterpriseTrade(w, e) {
  if (!e.makes || e.stock <= 0) return;

  const opts = [];
  for (const t of w.towns) {
    if (t.defectedTo) continue;
    const price = priceFor(w, t, e.makes);
    const target = growthTarget(w, t, e.makes);
    const gap = clamp((target - t.stock[e.makes]) / Math.max(1, target), 0, 1);
    opts.push({
      label: 'sell to ' + t.name + ' at ' + price,
      score: price * (0.35 + 1.6 * gap) + (e.contract && e.contract.townId === t.id ? price * 0.5 : 0),
      note: gap > 0.6 ? 'they are badly short' : gap > 0.15 ? 'they are short of it' : 'they are well stocked',
      townId: t.id, price: price, gap: gap,
    });
  }
  opts.push({ label: 'hold stock', score: w.cfg.basePrice * 0.8, note: 'wait for a better price' });

  const pick = decide(w, e.name, opts, 'choosing a buyer');
  if (!pick || !pick.townId) return;

  // Serve buyers in order of need until the stock runs out. A single sale per
  // week meant one pasture could supply only one of three towns, and cloth
  // never accumulated anywhere.
  const ranked = opts.filter((o) => o.townId).sort((a, b) => b.score - a.score);
  for (const o of ranked) {
    if (e.stock <= 0) break;
    if (o.score < w.cfg.basePrice * 0.8) break;   // worse than simply holding
    const t = w.towns.find((x) => x.id === o.townId);
    const shortfall = Math.max(0, Math.round(growthTarget(w, t, e.makes) - t.stock[e.makes]));
    if (shortfall <= 0) continue;
    const afford = Math.min(e.stock, shortfall, Math.floor(Math.max(0, t.treasury - 2000) / o.price));
    if (afford <= 0) continue;

    e.stock -= afford;
    t.stock[e.makes] += afford;
    t.treasury -= afford * o.price;
    e.treasury += afford * o.price;
    e.lastSoldTo = t.name;
    e.taxOwed += Math.round(afford * o.price * w.realm.taxRate);
  }
}

function enterprisePayTax(w, e) {
  if (e.taxOwed <= 0) return;
  const willing = e.standing > 25 && !e.hiding;
  if (willing && e.treasury >= e.taxOwed) {
    e.treasury -= e.taxOwed;
    w.realm.treasury += e.taxOwed;
    e.taxOwed = 0;
  } else if (!willing && w.tick % 8 === 0) {
    // Refusing tax is the concrete road to outlawry.
    say(w, e.id, e.name + ' withholds its taxes.', 'bad');
  }
}

/* ---------- contracts: a contract is a quest ------------------------------ */

function townPostContracts(w, t) {
  for (const k of unlockedFor(t.tier)) {
    const need = t.pop * RESOURCES[k].rate;
    if (t.stock[k] > need * 2.5) continue;
    if (w.contracts.some((c) => c.townId === t.id && c.res === k && (c.status === 'open' || c.status === 'taken'))) continue;
    const qty = Math.ceil(need * 3);
    const price = priceFor(w, t, k);
    if (t.treasury < qty * price * 0.4) continue;
    w.contracts.push({
      id: uid(w, 'c'), townId: t.id, res: k, qty: qty, delivered: 0,
      price: price, deadline: w.tick + 10, status: 'open', taker: null,
    });
    say(w, t.id, t.name + ' posts a contract: ' + qty + ' ' + RESOURCES[k].label + ' by week ' + (w.tick + 10) + '.');
  }
}

function enterpriseConsiderContracts(w, e) {
  if (!e.makes || e.contract) return;
  const open = w.contracts.filter((c) => c.status === 'open' && c.res === e.makes);
  if (!open.length) return;

  const opts = open.map((c) => {
    const t = w.towns.find((x) => x.id === c.townId);
    const feasible = e.capacity * 8 >= c.qty;
    return {
      label: 'take ' + t.name + "'s contract (" + c.qty + ' at ' + c.price + ')',
      score: c.price * (feasible ? 1.35 : 0.3) + (e.standing > 50 ? 5 : -5),
      note: feasible ? 'within our capacity' : 'beyond our capacity',
      cid: c.id,
    };
  });
  opts.push({ label: 'stay on the open market', score: w.cfg.basePrice * 1.1, note: 'no obligation' });

  const pick = decide(w, e.name, opts, 'weighing contracts');
  if (!pick || !pick.cid) return;
  const c = w.contracts.find((x) => x.id === pick.cid);
  c.status = 'taken'; c.taker = e.id;
  e.contract = { id: c.id, townId: c.townId };
  say(w, e.id, e.name + ' accepts a contract from ' + w.towns.find((t) => t.id === c.townId).name + '.');
}

function resolveContracts(w) {
  for (const c of w.contracts) {
    if (c.status !== 'taken') continue;
    const e = w.enterprises.find((x) => x.id === c.taker);
    const t = w.towns.find((x) => x.id === c.townId);
    if (!e || !t) { c.status = 'void'; continue; }

    const give = Math.min(e.stock, c.qty - c.delivered);
    if (give > 0) { e.stock -= give; t.stock[c.res] += give; c.delivered += give; }

    if (c.delivered >= c.qty) {
      c.status = 'done';
      const pay = c.qty * c.price;
      t.treasury -= pay; e.treasury += pay;
      e.standing = clamp(e.standing + 5, 0, 100);
      e.contract = null;
      say(w, e.id, e.name + ' completes its contract with ' + t.name + '.', 'good');
    } else if (w.tick > c.deadline) {
      // Failure costs everyone. A contract nobody suffers for breaking is
      // only a suggestion.
      c.status = 'failed';
      e.standing = clamp(e.standing - 12, 0, 100);
      e.mood = 'resentful';
      e.contract = null;
      t.mayor.standing = clamp(t.mayor.standing - 4, 0, 100);
      say(w, e.id, e.name + ' FAILS its contract with ' + t.name + '. Both suffer for it.', 'bad');
    }
  }
  w.contracts = w.contracts.filter((c) => c.status === 'open' || c.status === 'taken' || (w.tick - c.deadline) < 6);
}

/* ---------- foreign trade -------------------------------------------------- */

function importsAvailable(w, res) {
  const out = [];
  for (const n of w.neighbours) {
    if (!n.endowment.includes(res)) continue;
    if (n.relation === 'hostile') continue;
    const mult = n.relation === 'embargo' ? w.cfg.embargoMarkup
      : n.relation === 'alliance' ? 1.15
      : n.relation === 'trade' ? 1.30
      : w.cfg.importMarkup;
    out.push({ n: n, mult: mult });
  }
  return out;
}

function realmImports(w) {
  const budget = w.realm.budgets.envoy;
  let spent = 0;
  for (const t of w.towns) {
    if (t.defectedTo) continue;
    for (const k of unlockedFor(t.tier)) {
      if (w.realm.endowment.includes(k)) continue;
      const need = t.pop * RESOURCES[k].rate;
      if (t.stock[k] > need * 2) continue;
      const sources = importsAvailable(w, k);
      if (!sources.length) {
        if (w.tick % 10 === 0) say(w, t.id, t.name + ' can find no source of ' + RESOURCES[k].label + '.', 'bad');
        continue;
      }
      sources.sort((a, b) => a.mult - b.mult);
      const src = sources[0];
      const unit = Math.round(w.cfg.basePrice * src.mult);
      const can = Math.floor((budget - spent) / unit);
      const qty = Math.max(0, Math.min(Math.ceil(need * 2), can));
      if (qty <= 0) continue;
      t.stock[k] += qty;
      spent += qty * unit;
      if (src.n.relation === 'embargo' && w.tick % 6 === 0) {
        say(w, 'envoy', RESOURCES[k].label + ' bought from ' + src.n.name + ' at embargo prices.', 'warn');
      }
    }
  }
  w.realm.spent.envoy = spent;
  w.realm.treasury -= spent;
}

/* ---------- the Count ------------------------------------------------------ */

function countGovern(w) {
  const r = w.realm;
  const live = w.towns.filter((t) => !t.defectedTo);
  const worst = live.length ? Math.min.apply(null, live.map((t) => t.overall)) : 1;
  const spendable = r.treasury - w.cfg.treasuryReserve;
  const threat = sum(w.wildSites.filter((s) => !s.garrisoned).map((s) => s.threat));

  const opts = [
    { label: 'SURVIVE', score: (worst < 0.65 ? 200 : 0) + (spendable < 0 ? 120 : 0), note: 'worst fulfilment ' + worst.toFixed(2) },
    { label: 'SUSTAIN', score: 100 + (worst >= 0.65 && worst < 0.92 ? 60 : 0), note: 'hold the line' },
    { label: 'GROW', score: (worst > 0.92 ? 150 : 0) + (spendable > 120000 ? 60 : 0), note: 'spendable ' + Math.round(spendable) },
  ];
  const pick = decide(w, 'The Count', opts, 'setting the realm objective');
  if (pick && pick.label !== r.objective) {
    say(w, 'realm', 'The Count sets the realm to ' + pick.label + '.', pick.label === 'SURVIVE' ? 'warn' : 'info');
    r.objective = pick.label;
  }

  const pot = Math.max(0, spendable);
  const share = { SURVIVE: [0.15, 0.05, 0.50, 0.30], SUSTAIN: [0.25, 0.10, 0.35, 0.30], GROW: [0.30, 0.10, 0.25, 0.35] }[r.objective];
  r.budgets.marshal = Math.round(pot * share[0]);
  r.budgets.justiciar = Math.round(pot * share[1]);
  r.budgets.envoy = Math.round(pot * share[2]);
  r.budgets.towns = Math.round(pot * share[3]);

  // Disburse to the towns that need it most. The allocation was previously
  // computed and then never sent - the crown hoarded while its towns starved.
  const needy = live.filter((t) => t.overall < 0.95).sort((a, b) => a.overall - b.overall);
  const relief = r.objective === 'SURVIVE'
    ? Math.round(Math.max(0, r.treasury * 0.35))   // famine relief overrides the reserve
    : r.budgets.towns;
  if (needy.length && relief > 0) {
    let left = relief;
    for (const t of needy) {
      const grant = Math.round(left / needy.length);
      if (grant <= 0) break;
      t.treasury += grant;
      r.treasury -= grant;
      left -= grant;
    }
    r.spent.towns = relief - left;
  }

  // An order states an objective. The Mayor chooses the means, and may refuse.
  if (threat > 90 && w.tick % 14 === 0) {
    const t = live.slice().sort((a, b) => b.pop - a.pop)[0];
    if (t && !t.mayor.order) {
      t.mayor.order = { kind: 'levy', what: 'arms', qty: Math.round(t.pop * 0.25), by: w.tick + 12 };
      say(w, t.id, 'The Count orders ' + t.name + ' to furnish ' + t.mayor.order.qty + ' arms within 12 weeks.', 'warn');
    }
  }
}

/* ---------- advisors -------------------------------------------------------- */

function marshalAct(w) {
  const r = w.realm;
  let spent = 0;

  for (const s of w.wildSites) {
    const cost = w.cfg.garrisonCost;
    const needsYield = w.towns.some((t) => !t.defectedTo && t.stock[s.yields] < t.pop * RESOURCES[s.yields].rate * 2);
    const spilling = s.threat > w.cfg.raidThreshold;
    const canPay = cost <= (r.budgets.marshal - spent);

    const opts = [
      {
        label: 'garrison ' + s.name,
        score: (needsYield ? 90 : 20) + (spilling ? 80 : 0) + (r.objective === 'GROW' ? 40 : 0)
          - (r.objective === 'SURVIVE' ? 70 : 0) - (canPay ? 0 : 200),
        note: (needsYield ? 'we need its ' + RESOURCES[s.yields].label.toLowerCase() : 'the yield is not needed')
          + (spilling ? ', and its threat is spilling out' : ''),
        act: 'hold', site: s,
      },
      {
        label: 'leave ' + s.name,
        score: 60 + (r.objective === 'SURVIVE' ? 60 : 0) - (spilling ? 50 : 0),
        note: 'spare the troops and the coin',
        act: 'leave', site: s,
      },
    ];
    const pick = decide(w, 'Marshal', opts, 'wild site: ' + s.name);
    if (!pick) continue;
    if (pick.act === 'hold') {
      if (!s.garrisoned) say(w, 'marshal', 'The Marshal garrisons ' + s.name + '.');
      s.garrisoned = true; spent += cost;
    } else {
      if (s.garrisoned) say(w, 'marshal', 'The Marshal withdraws from ' + s.name + '.', 'warn');
      s.garrisoned = false;
    }
  }
  r.spent.marshal = spent;
  r.treasury -= spent;
}

function justiciarAct(w) {
  const r = w.realm;
  const live = w.towns.filter((t) => !t.defectedTo);
  const unrest = 100 - (sum(live.map((t) => t.satisfaction)) / Math.max(1, live.length));
  const opts = [
    { label: 'suppress vice', score: unrest > 45 ? 90 : 30, note: 'order before revenue' },
    { label: 'tax vice', score: 80 + (r.objective === 'SURVIVE' ? 40 : 0), note: 'revenue with tolerable disorder' },
    { label: 'tolerate vice', score: 40 + (unrest < 20 ? 30 : 0), note: 'leave well alone' },
  ];
  const pick = decide(w, 'Justiciar', opts, 'law and order');
  if (pick) {
    const map = { 'suppress vice': 'suppress', 'tax vice': 'tax', 'tolerate vice': 'tolerate' };
    if (r.lawPolicy !== map[pick.label]) say(w, 'justiciar', 'The Justiciar moves to ' + pick.label + '.');
    r.lawPolicy = map[pick.label];
  }
  if (r.lawPolicy === 'tax') r.treasury += 400;
  if (r.lawPolicy === 'suppress') r.treasury -= 250;
}

function chancellorAct(w) {
  const r = w.realm;
  const strain = w.towns.some((t) => !t.defectedTo && t.satisfaction < 45);
  const opts = [
    { label: 'raise taxes', score: (r.treasury < 60000 ? 110 : 20) - (strain ? 60 : 0), note: 'treasury ' + Math.round(r.treasury) },
    { label: 'hold taxes', score: 90, note: 'steady as she goes' },
    { label: 'lower taxes', score: (strain ? 100 : 20) + (r.treasury > 300000 ? 40 : 0), note: strain ? 'the towns are strained' : 'the coffers are full' },
  ];
  const pick = decide(w, 'Chancellor', opts, 'setting the tax rate');
  if (pick) {
    if (pick.label === 'raise taxes') r.taxRate = clamp(r.taxRate + 0.01, 0.05, 0.40);
    if (pick.label === 'lower taxes') r.taxRate = clamp(r.taxRate - 0.01, 0.05, 0.40);
  }
}

function envoyAct(w) {
  const gaps = RES_KEYS.filter((k) => !w.realm.endowment.includes(k));
  for (const n of w.neighbours) {
    const useful = n.endowment.filter((e) => gaps.includes(e));
    const opts = [
      {
        label: 'seek a treaty with ' + n.name,
        score: (useful.length ? 120 : 30) + (n.opinion - 50),
        note: useful.length ? 'they hold ' + useful.map((u) => RESOURCES[u].label).join(' and ') : 'they hold nothing we lack',
      },
      { label: 'stay neutral with ' + n.name, score: 80, note: 'no commitment either way' },
      {
        label: 'embargo ' + n.name,
        score: (n.opinion < 25 ? 120 : 0) - (useful.length ? 80 : 0),
        note: n.opinion < 25 ? 'they have wronged us' : 'we have no cause',
      },
    ];
    const pick = decide(w, 'Envoy', opts, 'relations with ' + n.name);
    if (!pick) continue;
    if (pick.label.indexOf('seek') === 0 && n.relation === 'neutral' && n.opinion > 55) {
      n.relation = 'trade';
      say(w, 'envoy', 'A trade agreement is signed with ' + n.name + '.', 'good');
    } else if (pick.label.indexOf('embargo') === 0 && n.relation !== 'embargo') {
      n.relation = 'embargo';
      say(w, 'envoy', n.name + ' is placed under embargo.', 'bad');
    }
  }
}

/* ---------- Mayors: discretion, refusal, escalation ------------------------- */

function mayorAct(w, t) {
  const m = t.mayor;
  const need = townNeeds(w, t);
  const worstRes = Object.keys(need).sort((a, b) => t.fulfil[a] - t.fulfil[b])[0];

  if (!m.order) {
    const opts = [
      { label: 'secure ' + RESOURCES[worstRes].label, score: 100 * (1 - t.fulfil[worstRes]), note: 'fulfilment ' + Math.round(t.fulfil[worstRes] * 100) + '%' },
      { label: 'build reserves', score: t.overall > 0.95 ? 80 : 20, note: 'stock against the winter' },
      { label: 'ease local taxes', score: t.satisfaction < 50 ? 90 : 10, note: 'satisfaction ' + Math.round(t.satisfaction) },
    ];
    const pick = decide(w, m.name, opts, 'running ' + t.name);
    if (pick && pick.label === 'ease local taxes') t.localTax = clamp(t.localTax - 0.01, 0.02, 0.25);
    return;
  }

  const o = m.order;
  const canAfford = t.treasury > o.qty * 120;
  const wouldStarve = t.overall < 0.72;
  const opts = [
    { label: 'comply - buy what is needed', score: (canAfford ? 110 : -50) - (wouldStarve ? 60 : 0), note: canAfford ? 'the treasury can bear it' : 'we cannot pay for it', act: 'comply' },
    { label: 'comply - divert from the citizens', score: 70 - (wouldStarve ? 120 : 0) + (m.standing < 40 ? 40 : 0), note: wouldStarve ? 'the people are already hungry' : 'the people can bear it', act: 'divert' },
    { label: 'comply in part, and say so', score: 60 + (wouldStarve ? 40 : 0), note: 'half now, the rest later', act: 'partial' },
    { label: 'refuse the order', score: (wouldStarve ? 130 : 10) + (m.standing > 70 ? 20 : 0), note: wouldStarve ? 'the town must come first' : 'we have no cause to refuse', act: 'refuse' },
  ];
  const pick = decide(w, m.name, opts, "the Count's order: " + o.qty + ' ' + RESOURCES[o.what].label);
  if (!pick) return;

  if (pick.act === 'comply' || pick.act === 'divert') {
    if (pick.act === 'comply') t.treasury -= o.qty * 120;
    else { t.stock[o.what] = Math.max(0, t.stock[o.what] - o.qty); t.satisfaction -= 6; }
    w.realm.army.equipment += o.qty;
    m.standing = clamp(m.standing + w.cfg.complyGain, 0, 100);
    say(w, t.id, t.name + ' furnishes the levy. ' + m.name + ' gains the Count’s favour.', 'good');
    m.order = null;
  } else if (pick.act === 'partial') {
    w.realm.army.equipment += Math.round(o.qty / 2);
    m.standing = clamp(m.standing - 3, 0, 100);
    say(w, t.id, m.name + ' sends half the levy and asks for time.', 'warn');
    m.order = null;
  } else {
    m.standing = clamp(m.standing - w.cfg.refusalHit, 0, 100);
    m.lastRefusal = { tick: w.tick, reason: pick.note };
    say(w, t.id, m.name + ' REFUSES the Count’s order: "' + pick.note + '".', 'bad');
    m.order = null;
    escalate(w, t);
  }
}

// Refusal does not stop at "no". A standoff has to go somewhere.
function escalate(w, t) {
  const m = t.mayor;
  if (m.standing > 30) return;

  const crownStrong = w.realm.treasury > 100000 && w.realm.army.troops > 100;
  const suitor = w.neighbours.find((n) => n.relation === 'trade' || n.relation === 'alliance');

  const opts = [
    { label: 'the Count replaces the Mayor', score: crownStrong ? 120 : 20, note: crownStrong ? 'the crown is strong enough to impose one' : 'the crown looks weak', act: 'depose' },
    { label: 'the town rebels', score: (crownStrong ? 20 : 110) + (t.satisfaction < 40 ? 40 : 0), note: 'the people are behind him', act: 'rebel' },
    { label: 'the town defects to ' + (suitor ? suitor.name : 'a rival'), score: suitor ? 90 + (100 - m.standing) * 0.4 : 0, note: suitor ? 'better terms are on offer' : 'there is nowhere to go', act: 'defect' },
    { label: 'the Count backs down', score: 55 + (t.pop > 250 ? 40 : 0), note: 'the town is too important to lose', act: 'yield' },
  ];
  const pick = decide(w, 'The Count', opts, 'standoff with ' + t.name);
  if (!pick) return;

  if (pick.act === 'depose') {
    m.name = 'Steward ' + ['Halvard', 'Brenn', 'Osric', 'Wynn'][Math.floor(w.rng() * 4)];
    m.standing = 55; m.mood = 'wary';
    say(w, t.id, 'The Count deposes the mayor of ' + t.name + '. ' + m.name + ' is installed.', 'bad');
  } else if (pick.act === 'rebel') {
    t.rebel = true; m.standing = 0;
    say(w, t.id, t.name + ' RISES IN REBELLION. Its taxes stop at the gate.', 'bad');
  } else if (pick.act === 'defect') {
    t.defectedTo = suitor.name;
    say(w, t.id, t.name + ' DEFECTS to ' + suitor.name + '. Territory lost without a siege.', 'bad');
  } else {
    m.standing = clamp(m.standing + 25, 0, 100);
    say(w, t.id, 'The Count relents. ' + t.name + ' keeps its grain, and its mayor.', 'warn');
  }
}

/* ---------- wild sites, raids, upkeep -------------------------------------- */

function wildSitesTick(w) {
  for (const s of w.wildSites) {
    if (s.garrisoned) {
      s.threat = Math.max(0, s.threat - 2);
      const live = w.towns.filter((t) => !t.defectedTo);
      if (live.length) live[Math.floor(w.rng() * live.length)].stock[s.yields] += w.cfg.wildYield;
    } else {
      s.threat += w.cfg.threatGrowth;
      if (s.threat > w.cfg.raidThreshold && w.rng() < 0.18) {
        const live = w.towns.filter((t) => !t.defectedTo);
        if (!live.length) continue;
        const t = live[Math.floor(w.rng() * live.length)];
        const res = RES_KEYS[Math.floor(w.rng() * RES_KEYS.length)];
        const loss = Math.round(t.stock[res] * 0.25);
        if (loss <= 0) { s.threat -= 4; continue; }
        t.stock[res] -= loss;
        t.satisfaction -= 4;
        say(w, t.id, 'Raiders from ' + s.name + ' strike ' + t.name + ', taking ' + loss + ' ' + RESOURCES[res].label + '.', 'bad');
        s.threat -= 10;
      }
    }
  }
}

function upkeep(w) {
  for (const t of w.towns) {
    if (t.defectedTo) continue;
    // A town earns from the trade of its own citizens. Without this it only ever
    // pays out - treasury to zero, nothing bought, fulfilment to nil, permanent
    // SURVIVE. The first thing the prototype caught.
    const trade = t.pop * w.cfg.townIncomePerHead * (0.75 + t.satisfaction / 400);
    t.treasury += Math.round(trade * (1 - t.localTax));
    t.treasury -= Math.round(t.pop * w.cfg.wagePerHead * 0.25);
    if (t.treasury < 0) t.treasury = 0;
    if (!t.rebel) {
      const remit = Math.round(t.pop * 30 * w.realm.taxRate);
      t.treasury -= remit; w.realm.treasury += remit;
    }
    const cap = w.cfg.popPerTier * t.tier;
    t.pop += (cap - t.pop) * w.cfg.popGrowth * (t.satisfaction / 100);
    t.pop = Math.max(20, t.pop);
    t.mayor.standing += (55 - t.mayor.standing) * (w.cfg.standingDrift / 100);
  }
  for (const e of w.enterprises) {
    e.standing += (55 - e.standing) * (w.cfg.standingDrift / 100);
    e.mood = e.standing < 30 ? 'resentful' : e.standing < 50 ? 'wary' : 'content';
    e.hiding = e.standing < 22;
  }
  w.realm.treasury -= w.realm.army.troops * 3;
  if (w.realm.treasury < 0) w.realm.treasury = 0;
}

/* ---------- the tick -------------------------------------------------------- */

function tick(w) {
  w.tick++;

  countGovern(w);
  chancellorAct(w);
  marshalAct(w);
  justiciarAct(w);
  envoyAct(w);

  for (const e of w.enterprises) { enterpriseProduce(w, e); enterpriseConsiderContracts(w, e); }
  for (const t of w.towns) if (!t.defectedTo) townPostContracts(w, t);
  resolveContracts(w);
  for (const e of w.enterprises) { enterpriseTrade(w, e); enterprisePayTax(w, e); }

  realmImports(w);

  for (const t of w.towns) {
    if (t.defectedTo) continue;
    consume(w, t);
    mayorAct(w, t);
    evaluateTier(w, t);
  }

  wildSitesTick(w);
  upkeep(w);
  return w;
}

/* ---------- player intervention: order, context, feedback -------------------- */

function issueOrder(w, townId, what, qty, weeks) {
  const t = w.towns.find((x) => x.id === townId);
  if (!t || t.defectedTo) return;
  t.mayor.order = { kind: 'levy', what: what, qty: qty, by: w.tick + weeks };
  say(w, t.id, 'You order ' + t.name + ' to furnish ' + qty + ' ' + RESOURCES[what].label + ' within ' + weeks + ' weeks.', 'warn');
}

function giveContext(w, key, text) {
  w.context[key] = text;
  say(w, 'realm', 'You inform the court: ' + text + '.');
}

// The player teaches the AI. This writes to the same record the agents build
// from their own outcomes.
function giveFeedback(w, agent, good) {
  const rec = (w.lessons[agent] = w.lessons[agent] || { good: 0, bad: 0 });
  if (good) rec.good++; else rec.bad++;
  say(w, 'realm', 'You ' + (good ? 'commend' : 'rebuke') + ' ' + agent + '.', good ? 'good' : 'warn');
}

function requisition(w, entId) {
  const e = w.enterprises.find((x) => x.id === entId);
  if (!e || !e.makes) return;
  const take = Math.floor(e.stock * 0.6);
  if (take <= 0) { say(w, e.id, e.name + ' has nothing to requisition.', 'warn'); return; }
  e.stock -= take;
  const live = w.towns.filter((t) => !t.defectedTo);
  if (live.length) live[0].stock[e.makes] += take;
  e.standing = clamp(e.standing - w.cfg.requisitionHit, 0, 100);
  e.requisitions++;
  say(w, e.id, 'The crown requisitions ' + take + ' ' + RESOURCES[e.makes].label + ' from ' + e.name + '.', 'warn');
}

/* ---------- exports ---------------------------------------------------------- */

window.Sim = {
  RESOURCES: RESOURCES, RES_KEYS: RES_KEYS, TIER_NAMES: TIER_NAMES, DEFAULT_CFG: DEFAULT_CFG,
  makeWorld: makeWorld, tick: tick, unlockedFor: unlockedFor, priceFor: priceFor, townNeeds: townNeeds,
  issueOrder: issueOrder, giveContext: giveContext, giveFeedback: giveFeedback, requisition: requisition,
};
