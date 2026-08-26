/* =============================================================================
   Kingdom simulation core - the whole of Aden.

   Nine regions, each a working kingdom with its own Count, advisors, towns,
   independent enterprises and wild sites. No DOM, no rendering: this file is
   the part that eventually becomes Java.

   Running every region rather than stubbing the neighbours is what makes
   diplomacy real. Each realm has genuine surpluses and genuine gaps, so a
   treaty is worth having and an embargo actually hurts.

   Every decision records the alternatives it weighed. That is not
   instrumentation bolted on afterwards - an AI whose reasoning cannot be read
   cannot be tuned, and the economy model test already proved that reading a
   design is not enough to know it works.
   ============================================================================= */

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
const WORLD = window.AdenWorld;

const DEFAULT_CFG = {
  popPerTier:      120,
  popGrowth:       0.03,
  coverCycles:     3,
  tierUpTicks:     26,      // growth is deliberately slow - a tier is hard-won
  tierDownTicks:   6,
  tierUpFulfil:    0.90,
  tierDownFulfil:  0.70,
  postureTicks:    4,       // weeks a posture must be warranted before it shifts
  basePrice:       20,
  scarcityK:       3.0,
  priceCap:        2.0,
  treasuryReserve: 20000,
  wagePerHead:     4,
  townIncomePerHead: 40,
  garrisonCost:    800,
  threatGrowth:    0.9,
  raidThreshold:   55,
  wildYield:       90,
  standingDrift:   0.4,
  requisitionHit:  16,
  refusalHit:      14,
  complyGain:      6,
  tradeMarkup:     1.35,
  embargoMarkup:   4.0,
  allyMarkup:      1.10,
};

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const sum = (a) => a.reduce((x, y) => x + y, 0);
const unlockedFor = (tier) => RES_KEYS.filter((k) => RESOURCES[k].unlock <= tier);

/* ---------- world construction ------------------------------------------- */

function makeRegion(w, def) {
  const R = {
    id: def.id, name: def.name, castleId: def.castleId,
    wx: def.wx, wy: def.wy, blurb: def.blurb, roads: def.roads.slice(),
    contracts: [],
  };

  R.realm = {
    name: def.name,
    treasury: 240000,
    objective: 'SUSTAIN',
    pendingObjective: null, pendingTicks: 0,
    taxRate: 0.15,
    lawPolicy: 'tax',
    army: { troops: 120, equipment: 60 },
    budgets: { marshal: 0, justiciar: 0, envoy: 0, towns: 0 },
    spent: { marshal: 0, justiciar: 0, envoy: 0, towns: 0 },
  };

  R.towns = def.towns.map((m) => ({
    id: m.id, name: m.loc, loc: m.loc, x: m.x, y: m.y, region: def.id,
    pop: m.pop, tier: m.tier,
    satisfaction: 60,
    stock: Object.fromEntries(RES_KEYS.map((k) => [k, Math.round(m.pop * 2)])),
    treasury: 30000,
    fulfil: Object.fromEntries(RES_KEYS.map((k) => [k, 1])),
    overall: 1,
    upTicks: 0, downTicks: 0,
    localTax: 0.10,
    rebel: false, defectedTo: null,
    mayor: { name: m.mayor, standing: 60, mood: 'content', order: null, lastRefusal: null },
    shops: [],
  }));

  const shopFor = { food: 'Grocer', cloth: 'Weaver', materials: 'Mason',
                    arms: 'Armoury', medicine: 'Apothecary', luxury: 'Vintner' };
  for (const t of R.towns) {
    t.shops = unlockedFor(t.tier).map((k) => ({
      id: t.id + '-' + k, name: shopFor[k], sells: k, stock: 30, sold: 0,
    }));
  }

  R.enterprises = def.enterprises.map((m) => ({
    id: m.id, name: m.loc, loc: m.loc, x: m.x, y: m.y, region: def.id,
    type: m.type, makes: m.makes, capacity: m.rate,
    owner: 'npc', stock: m.makes ? 20 : 0, treasury: 12000,
    standing: 60, taxOwed: 0, mood: 'content',
    contract: null, lastSoldTo: null, requisitions: 0, hiding: false,
  }));

  R.wildSites = def.wildSites.map((m) => ({
    id: m.id, name: m.loc, loc: m.loc, x: m.x, y: m.y, region: def.id,
    threat: m.threat, yields: m.yields, garrisoned: false,
  }));

  R.fortress = {
    id: def.fortress.id, name: def.fortress.loc, loc: def.fortress.loc,
    x: def.fortress.x, y: def.fortress.y, region: def.id, owner: 'npc',
    commander: { name: 'Commander of ' + def.fortress.loc, standing: 65 },
    garrison: def.fortress.garrison, equipment: 30, readiness: 0.6,
  };

  R.castle = { id: def.castle.id, name: def.castle.loc, loc: def.castle.loc,
               x: def.castle.x, y: def.castle.y, region: def.id };
  R.landmarks = def.landmarks.map((m) => Object.assign({ region: def.id }, m));

  // What the region can make for itself, derived from its enterprises rather
  // than declared. This is the fact everything else turns on.
  R.endowment = [];
  for (const e of R.enterprises) if (e.makes && R.endowment.indexOf(e.makes) < 0) R.endowment.push(e.makes);
  R.mustSource = RES_KEYS.filter((k) => R.endowment.indexOf(k) < 0);

  return R;
}

function makeWorld(seed, cfgOverrides) {
  const w = {
    tick: 0,
    seed: seed || 7,
    rng: makeRng(seed || 7),
    cfg: Object.assign({}, DEFAULT_CFG, cfgOverrides || {}),
    chronicle: [],
    decisions: [],
    lessons: {},
    context: {},
    nextId: 1,
    player: window.AdenWorld_playerRegion || 'gludio',
  };

  w.regions = {};
  w.order = Object.keys(WORLD);
  for (const id of w.order) w.regions[id] = makeRegion(w, WORLD[id]);

  w.relations = {};
  for (const a of w.order) {
    w.relations[a] = {};
    for (const b of w.order) if (a !== b) w.relations[a][b] = { state: 'neutral', opinion: 50 };
  }

  say(w, 'world', 'The chronicles of Aden begin. Nine realms, and not one of them self-sufficient.');
  return w;
}

/* ---------- chronicle and decisions --------------------------------------- */

function say(w, scope, text, severity) {
  w.chronicle.unshift({ tick: w.tick, scope: scope, text: text, severity: severity || 'info' });
  if (w.chronicle.length > 400) w.chronicle.pop();
}

function decide(w, agent, options, note) {
  const scored = options.slice().sort((a, b) => b.score - a.score);
  const chosen = scored[0];
  w.decisions.unshift({
    tick: w.tick, agent: agent, chose: chosen ? chosen.label : 'nothing',
    why: scored.slice(0, 4), note: note || '',
  });
  if (w.decisions.length > 400) w.decisions.pop();
  return chosen;
}

const uid = (w, p) => p + '-' + (w.nextId++);

/* ---------- economy -------------------------------------------------------- */

function townNeeds(w, t) {
  const out = {};
  for (const k of unlockedFor(t.tier)) out[k] = t.pop * RESOURCES[k].rate;
  return out;
}

function growthTarget(w, t, res) {
  const next = Math.min(5, t.tier + 1);
  return w.cfg.popPerTier * next * RESOURCES[res].rate * w.cfg.coverCycles;
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
  // Bread before circuses - finding F8 of the economy model test.
  const lux = need.luxury === undefined ? null : t.fulfil.luxury;
  t.satisfaction = clamp(100 * t.overall * (lux === null ? 1 : 1 + 0.25 * lux * (2 - lux)), 0, 100);
  return t.overall;
}

function evaluateTier(w, t) {
  const c = w.cfg;
  const next = Math.min(5, t.tier + 1);
  let covered = true;
  for (const k of unlockedFor(next)) {
    if (t.stock[k] < c.popPerTier * next * RESOURCES[k].rate * c.coverCycles * 0.9) { covered = false; break; }
  }
  t.upTicks = (t.overall >= c.tierUpFulfil && covered) ? t.upTicks + 1 : 0;
  t.downTicks = (t.overall < c.tierDownFulfil) ? t.downTicks + 1 : 0;

  if (t.upTicks >= c.tierUpTicks && t.tier < 5) {
    t.tier++; t.upTicks = 0;
    say(w, t.region, t.name + ' rises to ' + TIER_NAMES[t.tier] + '.', 'good');
  } else if (t.downTicks >= c.tierDownTicks && t.tier > 1) {
    t.tier--; t.downTicks = 0;
    say(w, t.region, t.name + ' falls to ' + TIER_NAMES[t.tier] + '.', 'bad');
  }
}

function scarcity(t, res) {
  const f = t.fulfil[res];
  return f === undefined ? 0.5 : clamp(1 - f, 0, 1);
}

function priceFor(w, t, res) {
  return Math.round(w.cfg.basePrice * Math.min(w.cfg.priceCap, 1 + w.cfg.scarcityK * scarcity(t, res)));
}

/* ---------- enterprises ---------------------------------------------------- */

function enterpriseProduce(w, e) {
  if (!e.makes) return;
  const mood = e.mood === 'resentful' ? 0.7 : e.mood === 'wary' ? 0.85 : 1.0;
  e.stock += Math.round(e.capacity * mood * (e.hiding ? 0.6 : 1));
}

// An enterprise sells to whoever needs it most. It is nobody's subordinate.
function enterpriseTrade(w, R, e) {
  if (!e.makes || e.stock <= 0) return;

  const opts = [];
  for (const t of R.towns) {
    if (t.defectedTo) continue;
    const price = priceFor(w, t, e.makes);
    const target = growthTarget(w, t, e.makes);
    const gap = clamp((target - t.stock[e.makes]) / Math.max(1, target), 0, 1);
    opts.push({
      label: 'sell to ' + t.name + ' at ' + price,
      // Weighted by the buyer's gap to target. Price alone was a useless signal
      // once every town was fed - they all bid the same and supply went to
      // whoever sorted first.
      score: price * (0.35 + 1.6 * gap) + (e.contract && e.contract.townId === t.id ? price * 0.5 : 0),
      note: gap > 0.6 ? 'they are badly short' : gap > 0.15 ? 'they are short of it' : 'they are well stocked',
      townId: t.id, price: price,
    });
  }
  opts.push({ label: 'hold stock', score: w.cfg.basePrice * 0.8, note: 'wait for a better price' });

  const pick = decide(w, e.name, opts, 'choosing a buyer');
  if (!pick || !pick.townId) return;

  // Serve buyers in order of need until the stock runs out. One sale a week
  // meant a single pasture could supply only one town.
  const ranked = opts.filter((o) => o.townId).sort((a, b) => b.score - a.score);
  for (const o of ranked) {
    if (e.stock <= 0) break;
    if (o.score < w.cfg.basePrice * 0.8) break;
    const t = R.towns.find((x) => x.id === o.townId);
    const shortfall = Math.max(0, Math.round(growthTarget(w, t, e.makes) - t.stock[e.makes]));
    if (shortfall <= 0) continue;
    const qty = Math.min(e.stock, shortfall, Math.floor(Math.max(0, t.treasury - 2000) / o.price));
    if (qty <= 0) continue;
    e.stock -= qty;
    t.stock[e.makes] += qty;
    t.treasury -= qty * o.price;
    e.treasury += qty * o.price;
    e.lastSoldTo = t.name;
    e.taxOwed += Math.round(qty * o.price * R.realm.taxRate);
  }
}

function enterprisePayTax(w, R, e) {
  if (e.taxOwed <= 0) return;
  const willing = e.standing > 25 && !e.hiding;
  if (willing && e.treasury >= e.taxOwed) {
    e.treasury -= e.taxOwed;
    R.realm.treasury += e.taxOwed;
    e.taxOwed = 0;
  } else if (!willing && w.tick % 10 === 0) {
    say(w, R.id, e.name + ' withholds its taxes.', 'bad');
  }
}

/* ---------- contracts: a contract is a quest -------------------------------- */

function townPostContracts(w, R, t) {
  for (const k of unlockedFor(t.tier)) {
    const need = t.pop * RESOURCES[k].rate;
    if (t.stock[k] > need * 2.5) continue;
    if (R.contracts.some((c) => c.townId === t.id && c.res === k && (c.status === 'open' || c.status === 'taken'))) continue;
    const qty = Math.ceil(need * 3);
    const price = priceFor(w, t, k);
    if (t.treasury < qty * price * 0.4) continue;
    R.contracts.push({
      id: uid(w, 'c'), townId: t.id, res: k, qty: qty, delivered: 0,
      price: price, deadline: w.tick + 10, status: 'open', taker: null,
    });
  }
}

function enterpriseConsiderContracts(w, R, e) {
  if (!e.makes || e.contract) return;
  const open = R.contracts.filter((c) => c.status === 'open' && c.res === e.makes);
  if (!open.length) return;

  const opts = open.map((c) => {
    const t = R.towns.find((x) => x.id === c.townId);
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
  const c = R.contracts.find((x) => x.id === pick.cid);
  c.status = 'taken'; c.taker = e.id;
  e.contract = { id: c.id, townId: c.townId };
}

function resolveContracts(w, R) {
  for (const c of R.contracts) {
    if (c.status !== 'taken') continue;
    const e = R.enterprises.find((x) => x.id === c.taker);
    const t = R.towns.find((x) => x.id === c.townId);
    if (!e || !t) { c.status = 'void'; continue; }

    const give = Math.min(e.stock, c.qty - c.delivered);
    if (give > 0) { e.stock -= give; t.stock[c.res] += give; c.delivered += give; }

    if (c.delivered >= c.qty) {
      c.status = 'done';
      const pay = c.qty * c.price;
      t.treasury -= pay; e.treasury += pay;
      e.standing = clamp(e.standing + 5, 0, 100);
      e.contract = null;
    } else if (w.tick > c.deadline) {
      // Failure costs everyone. A contract nobody suffers for breaking is only
      // a suggestion.
      c.status = 'failed';
      e.standing = clamp(e.standing - 12, 0, 100);
      e.mood = 'resentful';
      e.contract = null;
      t.mayor.standing = clamp(t.mayor.standing - 4, 0, 100);
      say(w, R.id, e.name + ' FAILS its contract with ' + t.name + '.', 'bad');
    }
  }
  R.contracts = R.contracts.filter((c) => c.status === 'open' || c.status === 'taken' || (w.tick - c.deadline) < 6);
}

/* ---------- trade between realms -------------------------------------------- */

function relation(w, a, b) { return w.relations[a] && w.relations[a][b]; }

function markup(w, state) {
  return state === 'embargo' ? w.cfg.embargoMarkup
    : state === 'alliance' ? w.cfg.allyMarkup
    : state === 'trade' ? w.cfg.tradeMarkup * 0.85
    : w.cfg.tradeMarkup;
}

// Who can sell this region a given good, and at what multiplier. Reachability
// is the road network, so geography constrains trade as much as politics does.
function sourcesFor(w, R, res) {
  const out = [];
  for (const id of w.order) {
    if (id === R.id) continue;
    const other = w.regions[id];
    if (other.endowment.indexOf(res) < 0) continue;
    const rel = relation(w, R.id, id);
    if (!rel || rel.state === 'hostile') continue;
    const direct = R.roads.indexOf(id) >= 0;
    out.push({ region: other, mult: markup(w, rel.state) * (direct ? 1 : 1.25), state: rel.state, direct: direct });
  }
  return out;
}

function realmImports(w, R) {
  const budget = R.realm.budgets.envoy;
  let spent = 0;
  for (const t of R.towns) {
    if (t.defectedTo) continue;
    for (const k of unlockedFor(t.tier)) {
      if (R.endowment.indexOf(k) >= 0) continue;
      const need = t.pop * RESOURCES[k].rate;
      if (t.stock[k] > need * 2) continue;
      const src = sourcesFor(w, R, k).sort((a, b) => a.mult - b.mult)[0];
      if (!src) {
        if (w.tick % 12 === 0) say(w, R.id, t.name + ' can find no source of ' + RESOURCES[k].label + ' anywhere.', 'bad');
        continue;
      }
      const unit = Math.round(w.cfg.basePrice * src.mult);
      // The TOWN pays for what it receives. The crown negotiates the trade and
      // caps its volume; it does not buy the food as a gift. Previously the
      // realm treasury drained to nothing paying for imports while the towns
      // that ate them banked their own income untouched.
      const canPay = Math.floor(Math.max(0, t.treasury - 2000) / unit);
      const qty = Math.max(0, Math.min(Math.ceil(need * 2), Math.floor((budget - spent) / unit), canPay));
      if (qty <= 0) continue;
      t.stock[k] += qty;
      t.treasury -= qty * unit;
      spent += qty * unit;
      R.realm.treasury += Math.round(qty * unit * 0.10);           // the crown's tariff
      src.region.realm.treasury += Math.round(qty * unit * 0.5);   // the seller profits
      if (src.state === 'embargo' && w.tick % 8 === 0) {
        say(w, R.id, RESOURCES[k].label + ' bought from ' + src.region.name + ' at embargo prices.', 'warn');
      }
    }
  }
  R.realm.spent.envoy = spent;   // volume traded, not money the crown spent
}

/* ---------- the Count -------------------------------------------------------- */

function countGovern(w, R) {
  const r = R.realm;
  const live = R.towns.filter((t) => !t.defectedTo);
  const worst = live.length ? Math.min.apply(null, live.map((t) => t.overall)) : 1;
  const spendable = r.treasury - w.cfg.treasuryReserve;
  const threat = sum(R.wildSites.filter((s) => !s.garrisoned).map((s) => s.threat));

  const opts = [
    { label: 'SURVIVE', score: (worst < 0.65 ? 200 : 0) + (spendable < 0 ? 120 : 0), note: 'worst fulfilment ' + worst.toFixed(2) },
    { label: 'SUSTAIN', score: 100 + (worst >= 0.65 && worst < 0.92 ? 60 : 0), note: 'hold the line' },
    { label: 'GROW', score: (worst > 0.92 ? 150 : 0) + (spendable > 120000 ? 60 : 0), note: 'spendable ' + Math.round(spendable) },
  ];
  const pick = decide(w, 'Count of ' + R.name, opts, 'setting the realm objective');

  // A realm does not change its whole posture on one bad week. Without this a
  // single dip flipped the Count to SURVIVE, abandoned every garrison, and put
  // them all back seven days later. Governments have inertia.
  if (pick && pick.label !== r.objective) {
    if (pick.label !== r.pendingObjective) { r.pendingObjective = pick.label; r.pendingTicks = 0; }
    r.pendingTicks++;
    if (r.pendingTicks >= w.cfg.postureTicks) {
      say(w, R.id, 'The Count of ' + R.name + ' sets the realm to ' + pick.label + '.', pick.label === 'SURVIVE' ? 'warn' : 'info');
      r.objective = pick.label; r.pendingTicks = 0; r.pendingObjective = null;
    }
  } else { r.pendingTicks = 0; r.pendingObjective = null; }

  const pot = Math.max(0, spendable);
  const share = { SURVIVE: [0.15, 0.05, 0.50, 0.30], SUSTAIN: [0.25, 0.10, 0.35, 0.30], GROW: [0.30, 0.10, 0.25, 0.35] }[r.objective];

  // A region that must buy four of its six goods cannot run on the same import
  // budget as one that buys two. Giran has no farms at all and starved on a flat
  // share. Weight the Envoy by how dependent the realm actually is, and take it
  // from the others.
  const dependence = R.mustSource.length / RES_KEYS.length;      // 0 .. 1
  const envoyShare = clamp(share[2] * (0.6 + 1.6 * dependence), 0.15, 0.72);
  const rest = 1 - envoyShare;
  const restBase = share[0] + share[1] + share[3];
  r.budgets.marshal = Math.round(pot * rest * (share[0] / restBase));
  r.budgets.justiciar = Math.round(pot * rest * (share[1] / restBase));
  r.budgets.envoy = Math.round(pot * envoyShare);
  r.budgets.towns = Math.round(pot * rest * (share[3] / restBase));

  // Relief to the towns that need it. Allocating a budget and never sending it
  // was the first bug this prototype found.
  const needy = live.filter((t) => t.overall < 0.95).sort((a, b) => a.overall - b.overall);
  const relief = r.objective === 'SURVIVE' ? Math.round(Math.max(0, r.treasury * 0.35)) : r.budgets.towns;
  if (needy.length && relief > 0) {
    let left = relief;
    for (const t of needy) {
      const grant = Math.round(left / needy.length);
      if (grant <= 0) break;
      t.treasury += grant; r.treasury -= grant; left -= grant;
    }
    r.spent.towns = relief - left;
  }

  if (threat > 120 && w.tick % 16 === 0) {
    const t = live.slice().sort((a, b) => b.pop - a.pop)[0];
    if (t && !t.mayor.order) {
      t.mayor.order = { kind: 'levy', what: 'arms', qty: Math.round(t.pop * 0.25), by: w.tick + 12 };
      say(w, R.id, 'The Count orders ' + t.name + ' to furnish ' + t.mayor.order.qty + ' arms within 12 weeks.', 'warn');
    }
  }
}

/* ---------- advisors ---------------------------------------------------------- */

function marshalAct(w, R) {
  const r = R.realm;

  // Rank every site, then fund down the list. Walking it greedily meant the same
  // sites always ran dry at the same point and were dropped and retaken forever.
  const ranked = R.wildSites.map((s) => {
    const needsYield = R.towns.some((t) => !t.defectedTo && t.stock[s.yields] < t.pop * RESOURCES[s.yields].rate * 2);
    const spilling = s.threat > w.cfg.raidThreshold;
    const onlySource = R.endowment.indexOf(s.yields) < 0;
    return {
      site: s,
      label: 'hold ' + s.name,
      score: (needsYield ? 90 : 20) + (spilling ? 80 : 0) + (onlySource ? 45 : 0)
        + (r.objective === 'GROW' ? 40 : 0) - (r.objective === 'SURVIVE' ? 70 : 0)
        + (s.garrisoned ? 35 : 0),
      note: (needsYield ? 'we need its ' + RESOURCES[s.yields].label.toLowerCase() : 'the yield is not needed')
        + (onlySource ? ', and the region makes none of its own' : '')
        + (spilling ? ', and its threat is spilling out' : ''),
    };
  }).sort((a, b) => b.score - a.score);

  let spent = 0, held = 0;
  for (const cand of ranked) {
    const affordable = spent + w.cfg.garrisonCost <= r.budgets.marshal;
    const take = affordable && cand.score > 55;
    if (take) { spent += w.cfg.garrisonCost; held++; }
    if (take && !cand.site.garrisoned) say(w, R.id, 'The Marshal garrisons ' + cand.site.name + '.');
    if (!take && cand.site.garrisoned) say(w, R.id, 'The Marshal withdraws from ' + cand.site.name + '.', 'warn');
    cand.site.garrisoned = take;
  }

  decide(w, 'Marshal of ' + R.name, ranked.slice(0, 4).map((c) => ({ label: c.label, score: c.score, note: c.note })),
    'holding ' + held + ' of ' + R.wildSites.length + ' sites for ' + Math.round(spent));

  r.spent.marshal = spent;
  r.treasury -= spent;
}

function justiciarAct(w, R) {
  const r = R.realm;
  const live = R.towns.filter((t) => !t.defectedTo);
  const unrest = 100 - (sum(live.map((t) => t.satisfaction)) / Math.max(1, live.length));
  const opts = [
    { label: 'suppress vice', score: unrest > 45 ? 90 : 30, note: 'order before revenue' },
    { label: 'tax vice', score: 80 + (r.objective === 'SURVIVE' ? 40 : 0), note: 'revenue with tolerable disorder' },
    { label: 'tolerate vice', score: 40 + (unrest < 20 ? 30 : 0), note: 'leave well alone' },
  ];
  const pick = decide(w, 'Justiciar of ' + R.name, opts, 'law and order');
  if (pick) r.lawPolicy = { 'suppress vice': 'suppress', 'tax vice': 'tax', 'tolerate vice': 'tolerate' }[pick.label];
  if (r.lawPolicy === 'tax') r.treasury += 400;
  if (r.lawPolicy === 'suppress') r.treasury -= 250;
}

function chancellorAct(w, R) {
  const r = R.realm;
  const strain = R.towns.some((t) => !t.defectedTo && t.satisfaction < 45);
  const opts = [
    { label: 'raise taxes', score: (r.treasury < 60000 ? 110 : 20) - (strain ? 60 : 0), note: 'treasury ' + Math.round(r.treasury) },
    { label: 'hold taxes', score: 90, note: 'steady as she goes' },
    { label: 'lower taxes', score: (strain ? 100 : 20) + (r.treasury > 300000 ? 40 : 0), note: strain ? 'the towns are strained' : 'the coffers are full' },
  ];
  const pick = decide(w, 'Chancellor of ' + R.name, opts, 'setting the tax rate');
  if (pick && pick.label === 'raise taxes') r.taxRate = clamp(r.taxRate + 0.01, 0.05, 0.40);
  if (pick && pick.label === 'lower taxes') r.taxRate = clamp(r.taxRate - 0.01, 0.05, 0.40);
}

// Diplomacy is real now: every counterparty is a running realm with its own
// gaps, so a treaty is worth having and an embargo genuinely hurts.
function envoyAct(w, R) {
  for (const id of w.order) {
    if (id === R.id) continue;
    const other = w.regions[id];
    const rel = relation(w, R.id, id);
    const useful = other.endowment.filter((k) => R.mustSource.indexOf(k) >= 0);
    const direct = R.roads.indexOf(id) >= 0;

    rel.opinion = clamp(rel.opinion + (useful.length ? 0.9 : -0.3) + (direct ? 0.5 : 0), 0, 100);

    const opts = [
      { label: 'seek a treaty with ' + other.name,
        score: (useful.length ? 120 : 30) + (rel.opinion - 50) + (direct ? 25 : 0),
        note: useful.length ? 'they hold ' + useful.map((k) => RESOURCES[k].label).join(' and ') : 'they hold nothing we lack' },
      { label: 'stay neutral with ' + other.name, score: 80, note: 'no commitment either way' },
      { label: 'embargo ' + other.name,
        score: (rel.opinion < 25 ? 120 : 0) - (useful.length ? 80 : 0),
        note: rel.opinion < 25 ? 'they have wronged us' : 'we have no cause' },
    ];
    const pick = decide(w, 'Envoy of ' + R.name, opts, 'relations with ' + other.name);
    if (!pick) continue;

    if (pick.label.indexOf('seek') === 0 && rel.state === 'neutral' && rel.opinion > 60) {
      rel.state = 'trade';
      const back = relation(w, id, R.id);
      if (back) { back.state = 'trade'; back.opinion = clamp(back.opinion + 8, 0, 100); }
      say(w, R.id, R.name + ' and ' + other.name + ' sign a trade agreement.', 'good');
    } else if (pick.label.indexOf('embargo') === 0 && rel.state !== 'embargo') {
      rel.state = 'embargo';
      say(w, R.id, R.name + ' places ' + other.name + ' under embargo.', 'bad');
    }
  }
}

/* ---------- Mayors ------------------------------------------------------------ */

function mayorAct(w, R, t) {
  const m = t.mayor;
  const need = townNeeds(w, t);
  const worst = Object.keys(need).sort((a, b) => t.fulfil[a] - t.fulfil[b])[0];

  if (!m.order) {
    const opts = [
      { label: 'secure ' + RESOURCES[worst].label, score: 100 * (1 - t.fulfil[worst]), note: 'fulfilment ' + Math.round(t.fulfil[worst] * 100) + '%' },
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
    R.realm.army.equipment += o.qty;
    m.standing = clamp(m.standing + w.cfg.complyGain, 0, 100);
    say(w, R.id, t.name + ' furnishes the levy.', 'good');
    m.order = null;
  } else if (pick.act === 'partial') {
    R.realm.army.equipment += Math.round(o.qty / 2);
    m.standing = clamp(m.standing - 3, 0, 100);
    say(w, R.id, m.name + ' sends half the levy and asks for time.', 'warn');
    m.order = null;
  } else {
    m.standing = clamp(m.standing - w.cfg.refusalHit, 0, 100);
    m.lastRefusal = { tick: w.tick, reason: pick.note };
    say(w, R.id, m.name + ' REFUSES the Count of ' + R.name + ': "' + pick.note + '".', 'bad');
    m.order = null;
    escalate(w, R, t);
  }
}

// Refusal does not stop at "no". A standoff has to go somewhere - and with nine
// realms running, defection now has somewhere real to go.
function escalate(w, R, t) {
  const m = t.mayor;
  if (m.standing > 30) return;

  const crownStrong = R.realm.treasury > 100000 && R.realm.army.troops > 100;
  const suitor = R.roads.map((id) => w.regions[id])
    .filter((o) => { const rel = relation(w, R.id, o.id); return rel && rel.state !== 'hostile'; })
    .sort((a, b) => b.realm.treasury - a.realm.treasury)[0];

  const opts = [
    { label: 'the Count replaces the Mayor', score: crownStrong ? 120 : 20, note: crownStrong ? 'the crown can impose one' : 'the crown looks weak', act: 'depose' },
    { label: 'the town rebels', score: (crownStrong ? 20 : 110) + (t.satisfaction < 40 ? 40 : 0), note: 'the people are behind him', act: 'rebel' },
    { label: 'the town defects to ' + (suitor ? suitor.name : 'a rival'), score: suitor ? 90 + (100 - m.standing) * 0.4 : 0, note: suitor ? 'better terms are on offer' : 'there is nowhere to go', act: 'defect' },
    { label: 'the Count backs down', score: 55 + (t.pop > 250 ? 40 : 0), note: 'the town is too important to lose', act: 'yield' },
  ];
  const pick = decide(w, 'Count of ' + R.name, opts, 'standoff with ' + t.name);
  if (!pick) return;

  if (pick.act === 'depose') {
    m.name = 'Steward ' + ['Halvard', 'Brenn', 'Osric', 'Wynn'][Math.floor(w.rng() * 4)];
    m.standing = 55; m.mood = 'wary';
    say(w, R.id, 'The Count deposes the mayor of ' + t.name + '. ' + m.name + ' is installed.', 'bad');
  } else if (pick.act === 'rebel') {
    t.rebel = true; m.standing = 0;
    say(w, R.id, t.name + ' RISES IN REBELLION.', 'bad');
  } else if (pick.act === 'defect') {
    t.defectedTo = suitor.name;
    say(w, R.id, t.name + ' DEFECTS to ' + suitor.name + '. Territory lost without a siege.', 'bad');
  } else {
    m.standing = clamp(m.standing + 25, 0, 100);
    say(w, R.id, 'The Count relents. ' + t.name + ' keeps its mayor.', 'warn');
  }
}

/* ---------- wild sites and upkeep --------------------------------------------- */

function wildSitesTick(w, R) {
  for (const s of R.wildSites) {
    const live = R.towns.filter((t) => !t.defectedTo);
    if (s.garrisoned) {
      s.threat = Math.max(0, s.threat - 2);
      if (live.length) live[Math.floor(w.rng() * live.length)].stock[s.yields] += w.cfg.wildYield;
    } else {
      s.threat += w.cfg.threatGrowth;
      if (s.threat > w.cfg.raidThreshold && w.rng() < 0.16 && live.length) {
        const t = live[Math.floor(w.rng() * live.length)];
        const res = RES_KEYS[Math.floor(w.rng() * RES_KEYS.length)];
        const loss = Math.round(t.stock[res] * 0.25);
        s.threat -= 10;
        if (loss <= 0) continue;
        t.stock[res] -= loss;
        t.satisfaction -= 4;
        say(w, R.id, 'Raiders from ' + s.name + ' strike ' + t.name + ', taking ' + loss + ' ' + RESOURCES[res].label + '.', 'bad');
      }
    }
  }
}

function upkeep(w, R) {
  for (const t of R.towns) {
    if (t.defectedTo) continue;
    // A town earns from the trade of its own citizens. Without this it only ever
    // pays out, and starves by construction.
    t.treasury += Math.round(t.pop * w.cfg.townIncomePerHead * (0.75 + t.satisfaction / 400) * (1 - t.localTax));
    t.treasury -= Math.round(t.pop * w.cfg.wagePerHead * 0.25);
    if (t.treasury < 0) t.treasury = 0;
    if (!t.rebel) {
      const remit = Math.round(t.pop * 30 * R.realm.taxRate);
      t.treasury -= remit; R.realm.treasury += remit;
    }
    const cap = w.cfg.popPerTier * t.tier;
    t.pop += (cap - t.pop) * w.cfg.popGrowth * (t.satisfaction / 100);
    t.pop = Math.max(20, t.pop);
    t.mayor.standing += (55 - t.mayor.standing) * (w.cfg.standingDrift / 100);
  }
  for (const e of R.enterprises) {
    e.standing += (55 - e.standing) * (w.cfg.standingDrift / 100);
    e.mood = e.standing < 30 ? 'resentful' : e.standing < 50 ? 'wary' : 'content';
    e.hiding = e.standing < 22;
  }
  R.realm.treasury -= R.realm.army.troops * 3;
  if (R.realm.treasury < 0) R.realm.treasury = 0;
}

/* ---------- the tick ----------------------------------------------------------- */

function tickRegion(w, R) {
  countGovern(w, R);
  chancellorAct(w, R);
  marshalAct(w, R);
  justiciarAct(w, R);

  for (const e of R.enterprises) { enterpriseProduce(w, e); enterpriseConsiderContracts(w, R, e); }
  for (const t of R.towns) if (!t.defectedTo) townPostContracts(w, R, t);
  resolveContracts(w, R);
  for (const e of R.enterprises) { enterpriseTrade(w, R, e); enterprisePayTax(w, R, e); }

  realmImports(w, R);

  for (const t of R.towns) {
    if (t.defectedTo) continue;
    consume(w, t);
    mayorAct(w, R, t);
    evaluateTier(w, t);
  }

  wildSitesTick(w, R);
  upkeep(w, R);
}

function tick(w) {
  w.tick++;
  for (const id of w.order) tickRegion(w, w.regions[id]);
  // Diplomacy after everyone has traded, so opinions reflect the week just past.
  if (w.tick % 4 === 0) for (const id of w.order) envoyAct(w, w.regions[id]);
  return w;
}

/* ---------- player intervention ------------------------------------------------ */

function findTown(w, id) {
  for (const r of w.order) { const t = w.regions[r].towns.find((x) => x.id === id); if (t) return t; }
  return null;
}
function findEnt(w, id) {
  for (const r of w.order) { const e = w.regions[r].enterprises.find((x) => x.id === id); if (e) return e; }
  return null;
}
function findWild(w, id) {
  for (const r of w.order) { const s = w.regions[r].wildSites.find((x) => x.id === id); if (s) return s; }
  return null;
}

function issueOrder(w, townId, what, qty, weeks) {
  const t = findTown(w, townId);
  if (!t || t.defectedTo) return;
  t.mayor.order = { kind: 'levy', what: what, qty: qty, by: w.tick + weeks };
  say(w, t.region, 'You order ' + t.name + ' to furnish ' + qty + ' ' + RESOURCES[what].label + ' within ' + weeks + ' weeks.', 'warn');
}

function giveContext(w, key, text) {
  w.context[key] = text;
  say(w, 'world', 'You inform the court: ' + text + '.');
}

// The player teaches the AI: this writes to the same record the agents build
// from their own outcomes.
function giveFeedback(w, agent, good) {
  const rec = (w.lessons[agent] = w.lessons[agent] || { good: 0, bad: 0 });
  if (good) rec.good++; else rec.bad++;
  say(w, 'world', 'You ' + (good ? 'commend' : 'rebuke') + ' ' + agent + '.', good ? 'good' : 'warn');
}

function requisition(w, entId) {
  const e = findEnt(w, entId);
  if (!e || !e.makes) return;
  const take = Math.floor(e.stock * 0.6);
  if (take <= 0) { say(w, e.region, e.name + ' has nothing to requisition.', 'warn'); return; }
  e.stock -= take;
  const R = w.regions[e.region];
  const live = R.towns.filter((t) => !t.defectedTo);
  if (live.length) live[0].stock[e.makes] += take;
  e.standing = clamp(e.standing - w.cfg.requisitionHit, 0, 100);
  e.requisitions++;
  say(w, e.region, 'The crown requisitions ' + take + ' ' + RESOURCES[e.makes].label + ' from ' + e.name + '.', 'warn');
}

/* ---------- exports ------------------------------------------------------------- */

window.Sim = {
  RESOURCES: RESOURCES, RES_KEYS: RES_KEYS, TIER_NAMES: TIER_NAMES, DEFAULT_CFG: DEFAULT_CFG,
  makeWorld: makeWorld, tick: tick, unlockedFor: unlockedFor, priceFor: priceFor,
  townNeeds: townNeeds, growthTarget: growthTarget, relation: relation, sourcesFor: sourcesFor,
  findTown: findTown, findEnt: findEnt, findWild: findWild,
  issueOrder: issueOrder, giveContext: giveContext, giveFeedback: giveFeedback, requisition: requisition,
};
