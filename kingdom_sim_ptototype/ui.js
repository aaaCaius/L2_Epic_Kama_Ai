/* =============================================================================
   UI for the kingdom prototype.

   Navigation IS the hierarchy: realm -> castle -> advisor, realm -> town ->
   mayor/shop, realm -> enterprise. Every entity uses the same panel shape -
   subject, what it is doing, why, its resources, and how to intervene - because
   if one panel works everywhere then the harness architecture is probably right.
   ============================================================================= */

const S = window.Sim;
let W = S.makeWorld(7);
let view = { kind: 'realm', id: null };
let timer = null;
let speed = 0;

const $ = (id) => document.getElementById(id);
const num = (n) => Math.round(n).toLocaleString();
const pct = (n) => Math.round(n * 100) + '%';
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/* ---------- decision lookup ---------------------------------------------- */
// The most recent thing this agent decided, and everything it weighed.
function lastDecision(agentName) {
  return W.decisions.find((d) => d.agent === agentName) || null;
}

function whyHtml(d) {
  if (!d) return '<div class="hint">Nothing decided yet.</div>';
  let h = '<div class="why">';
  d.why.forEach((o, i) => {
    h += '<div class="whyrow' + (i === 0 ? ' win' : '') + '">' +
      '<div class="sc">' + Math.round(o.score) + '</div>' +
      '<div><div class="lbl">' + esc(o.label) + '</div>' +
      '<div class="note">' + esc(o.note || '') + '</div></div></div>';
  });
  return h + '</div>';
}

function assignmentHtml(agentName, context) {
  const d = lastDecision(agentName);
  return '<div class="card"><h3>What it is doing</h3>' +
    (d ? '<div style="font-size:16px;margin-bottom:2px">' + esc(d.chose) + '</div>' +
         '<div class="subtitle" style="margin:0">' + esc(d.note || context || '') + ' &middot; week ' + d.tick + '</div>'
       : '<div class="hint">Idle.</div>') +
    '</div>' +
    '<div class="card"><h3>Why &mdash; everything it weighed</h3>' + whyHtml(d) + '</div>';
}

/* ---------- small pieces -------------------------------------------------- */
function bar(v, warnAt, badAt) {
  const cls = v < (badAt || 0.5) ? ' bad' : v < (warnAt || 0.8) ? ' warn' : '';
  return '<div class="bar' + cls + '"><i style="width:' + Math.max(0, Math.min(100, v * 100)) + '%"></i></div>';
}
function kv(k, v) { return '<div class="kv"><span class="k">' + k + '</span><span class="v">' + v + '</span></div>'; }
function pill(text, cls) { return '<span class="pill ' + (cls || '') + '">' + esc(text) + '</span>'; }

function stockCard(t) {
  let h = '<div class="card"><h3>Stores and fulfilment</h3>';
  for (const k of S.unlockedFor(t.tier)) {
    const need = t.pop * S.RESOURCES[k].rate;
    const f = t.fulfil[k] === undefined ? 1 : t.fulfil[k];
    h += '<div style="margin-bottom:9px"><div class="kv" style="border:none;padding:0">' +
      '<span class="k">' + S.RESOURCES[k].label + '</span>' +
      '<span class="v">' + num(t.stock[k]) + ' held &middot; ' + num(need) + '/wk &middot; ' + pct(f) + '</span></div>' +
      bar(f) + '</div>';
  }
  return h + '</div>';
}

/* ---------- views --------------------------------------------------------- */

function viewRealm() {
  const r = W.realm;
  const live = W.towns.filter((t) => !t.defectedTo);
  let h = '<h1>' + esc(r.name) + '</h1><div class="subtitle">A castle, three towns, a keep, five enterprises and three wild places.</div>';

  h += '<div class="card"><h3>The realm</h3><div class="grid">' +
    kv('Objective', pill(r.objective, r.objective === 'SURVIVE' ? 'bad' : r.objective === 'GROW' ? 'good' : '')) +
    kv('Treasury', num(r.treasury)) + kv('Tax rate', pct(r.taxRate)) +
    kv('Troops', num(r.army.troops)) + kv('Equipment', num(r.army.equipment)) +
    kv('Law', r.lawPolicy) + '</div></div>';

  h += '<h2 class="sec" style="margin-left:0">The castle</h2><div class="tiles">' +
    tile('castle', null, 'Castle of ' + esc(r.name), 'The Count and four advisors', 'objective ' + r.objective) +
    '</div>';

  h += '<h2 class="sec" style="margin-left:0">Towns</h2><div class="tiles">';
  for (const t of W.towns) {
    h += t.defectedTo
      ? tile('town', t.id, esc(t.name), 'Defected to ' + esc(t.defectedTo), 'lost')
      : tile('town', t.id, esc(t.name), S.TIER_NAMES[t.tier] + ' &middot; ' + num(t.pop) + ' souls' + (t.rebel ? ' &middot; IN REVOLT' : ''),
             'fulfilment ' + pct(t.overall) + ' &middot; mayor ' + Math.round(t.mayor.standing));
  }
  h += '</div>';

  h += '<h2 class="sec" style="margin-left:0">Enterprises &mdash; independent, not subordinate</h2><div class="tiles">';
  for (const e of W.enterprises) {
    h += tile('ent', e.id, esc(e.name), e.type + (e.makes ? ' &middot; ' + S.RESOURCES[e.makes].label : ''),
      'stock ' + num(e.stock) + ' &middot; standing ' + Math.round(e.standing) + ' &middot; ' + e.mood);
  }
  h += '</div>';

  h += '<h2 class="sec" style="margin-left:0">The keep and the wild places</h2><div class="tiles">' +
    tile('fort', null, esc(W.fortress.name), 'Garrison ' + W.fortress.garrison, W.fortress.commander.name);
  for (const s of W.wildSites) {
    h += tile('wild', s.id, esc(s.name), (s.garrisoned ? 'Garrisoned' : 'Unheld') + ' &middot; yields ' + S.RESOURCES[s.yields].label,
      'threat ' + Math.round(s.threat));
  }
  h += '</div>';

  h += '<h2 class="sec" style="margin-left:0">Abroad</h2><div class="tiles">';
  for (const n of W.neighbours) {
    h += tile('nb', n.id, esc(n.name), (n.isClan ? 'Player clan' : 'Kingdom') + ' &middot; ' + n.relation,
      'holds ' + n.endowment.map((x) => S.RESOURCES[x].label).join(', '));
  }
  return h + '</div>';
}

function tile(kind, id, title, desc, meta) {
  return '<div class="tile" onclick="go(\'' + kind + '\',' + (id ? "'" + id + "'" : 'null') + ')">' +
    '<div class="t">' + title + '</div><div class="d">' + desc + '</div><div class="m">' + meta + '</div></div>';
}

function viewCastle() {
  const r = W.realm;
  let h = crumb([['realm', null, 'Realm']]) + '<h1>Castle of ' + esc(r.name) + '</h1>' +
    '<div class="subtitle">The Count governs. He runs nothing directly.</div>';

  h += assignmentHtml('The Count', 'setting the realm objective');

  h += '<div class="card"><h3>Allocation this week</h3><div class="grid">' +
    kv('Marshal', num(r.budgets.marshal) + ' (spent ' + num(r.spent.marshal) + ')') +
    kv('Justiciar', num(r.budgets.justiciar)) +
    kv('Envoy', num(r.budgets.envoy) + ' (spent ' + num(r.spent.envoy) + ')') +
    kv('Towns', num(r.budgets.towns) + ' (sent ' + num(r.spent.towns || 0) + ')') +
    '</div></div>';

  h += '<h2 class="sec" style="margin-left:0">Advisors</h2><div class="tiles">';
  [['Marshal', 'military, garrisons, campaigns'], ['Justiciar', 'law, outlawry, contraband'],
   ['Chancellor', 'taxes, budgets, the word no'], ['Envoy', 'treaties, tariffs, embargoes']]
    .forEach(([nm, d]) => {
      const dec = lastDecision(nm);
      h += tile('advisor', nm, nm, d, dec ? esc(dec.chose) : 'idle');
    });
  h += '</div>';

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="feedback(\'The Count\',true)">Commend the Count</button>' +
    '<button onclick="feedback(\'The Count\',false)">Rebuke the Count</button>' +
    '<button onclick="ctx(\'war\')">Warn of war</button>' +
    '<button onclick="ctx(\'famine\')">Warn of famine</button>' +
    '</div><div class="hint">Feedback writes to the same record the agent builds from its own outcomes. ' +
    'Context tells it something it cannot know.</div></div>';
  return h;
}

function viewAdvisor(name) {
  let h = crumb([['realm', null, 'Realm'], ['castle', null, 'Castle']]) + '<h1>' + esc(name) + '</h1>';
  const blurb = {
    Marshal: 'Raises troops, garrisons the wild places, and asks the towns for levies.',
    Justiciar: 'Sets the law: suppress the vice trade, tolerate it, or tax it.',
    Chancellor: 'Holds the purse, and says what cannot be afforded.',
    Envoy: 'Treaties and tariffs, with rival kingdoms and player clans alike.',
  }[name] || '';
  h += '<div class="subtitle">' + blurb + '</div>';
  h += assignmentHtml(name, '');

  if (name === 'Marshal') {
    h += '<div class="card"><h3>Wild places</h3>';
    for (const s of W.wildSites) {
      h += '<div class="kv"><span class="k">' + esc(s.name) + ' ' +
        pill(s.garrisoned ? 'held' : 'unheld', s.garrisoned ? 'good' : (s.threat > W.cfg.raidThreshold ? 'bad' : 'warn')) +
        '</span><span class="v">threat ' + Math.round(s.threat) + ' &middot; yields ' + S.RESOURCES[s.yields].label + '</span></div>';
    }
    h += '</div>';
  }
  if (name === 'Envoy') {
    h += '<div class="card"><h3>Relations</h3>';
    for (const n of W.neighbours) {
      h += '<div class="kv"><span class="k">' + esc(n.name) + ' ' +
        pill(n.relation, n.relation === 'embargo' ? 'bad' : n.relation === 'trade' ? 'good' : '') +
        '</span><span class="v">opinion ' + Math.round(n.opinion) + ' &middot; holds ' +
        n.endowment.map((x) => S.RESOURCES[x].label).join(', ') + '</span></div>';
    }
    h += '<div class="hint">Gludio makes no medicine and no luxury. Those must be bought abroad, which is what gives an embargo its teeth.</div></div>';
  }

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="feedback(\'' + name + '\',true)">Commend</button>' +
    '<button onclick="feedback(\'' + name + '\',false)">Rebuke</button></div>' +
    lessonLine(name) + '</div>';
  return h;
}

function lessonLine(name) {
  const l = W.lessons[name];
  if (!l) return '<div class="hint">No feedback recorded yet.</div>';
  return '<div class="hint">You have commended this agent ' + l.good + ' times and rebuked it ' + l.bad + '.</div>';
}

function viewTown(id) {
  const t = W.towns.find((x) => x.id === id);
  if (!t) return viewRealm();
  let h = crumb([['realm', null, 'Realm']]) + '<h1>' + esc(t.name) + '</h1>' +
    '<div class="subtitle">' + S.TIER_NAMES[t.tier] + ' &middot; ' + num(t.pop) + ' souls' +
    (t.rebel ? ' &middot; <b style="color:var(--bad)">in revolt</b>' : '') +
    (t.defectedTo ? ' &middot; <b style="color:var(--bad)">defected to ' + esc(t.defectedTo) + '</b>' : '') + '</div>';

  h += '<div class="card"><h3>The town</h3><div class="grid">' +
    kv('Fulfilment', pct(t.overall)) + kv('Satisfaction', Math.round(t.satisfaction)) +
    kv('Treasury', num(t.treasury)) + kv('Local tax', pct(t.localTax)) +
    kv('Toward next tier', t.upTicks + '/' + W.cfg.tierUpTicks) +
    kv('Mayor standing', Math.round(t.mayor.standing)) + '</div></div>';

  h += assignmentHtml(t.mayor.name, 'running ' + t.name);

  if (t.mayor.order) {
    h += '<div class="card"><h3>Standing order from the Count</h3>' +
      '<div>' + num(t.mayor.order.qty) + ' ' + S.RESOURCES[t.mayor.order.what].label +
      ' by week ' + t.mayor.order.by + '</div></div>';
  }
  if (t.mayor.lastRefusal) {
    h += '<div class="card"><h3>Last refusal</h3><div>Week ' + t.mayor.lastRefusal.tick +
      ' &mdash; &ldquo;' + esc(t.mayor.lastRefusal.reason) + '&rdquo;</div></div>';
  }

  h += stockCard(t);

  h += '<div class="card"><h3>Shops within the walls</h3><div class="tiles">';
  for (const s of t.shops) {
    const f = t.fulfil[s.sells] === undefined ? 1 : t.fulfil[s.sells];
    h += '<div class="tile"><div class="t">' + esc(s.name) + '</div>' +
      '<div class="d">sells ' + S.RESOURCES[s.sells].label + '</div>' +
      '<div class="m">town holds ' + num(t.stock[s.sells]) + ' &middot; ' + pct(f) + ' met</div></div>';
  }
  h += '</div></div>';

  const mine = W.contracts.filter((c) => c.townId === t.id && (c.status === 'open' || c.status === 'taken'));
  h += '<div class="card"><h3>Contracts posted &mdash; a contract is a quest</h3>';
  if (!mine.length) h += '<div class="hint">Nothing outstanding.</div>';
  for (const c of mine) {
    const taker = c.taker ? W.enterprises.find((e) => e.id === c.taker) : null;
    h += '<div class="kv"><span class="k">' + num(c.qty) + ' ' + S.RESOURCES[c.res].label +
      ' by week ' + c.deadline + '</span><span class="v">' + (taker ? esc(taker.name) + ' &middot; ' + num(c.delivered) + '/' + num(c.qty) : 'unclaimed') +
      ' &middot; ' + num(c.price) + '/unit</span></div>';
  }
  h += '</div>';

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<select id="ordRes">' + S.RES_KEYS.map((k) => '<option value="' + k + '">' + S.RESOURCES[k].label + '</option>').join('') + '</select>' +
    '<input id="ordQty" type="number" value="80" style="width:80px">' +
    '<button onclick="order(\'' + t.id + '\')">Order a levy</button>' +
    '<button onclick="feedback(\'' + esc(t.mayor.name) + '\',true)">Commend the Mayor</button>' +
    '<button onclick="feedback(\'' + esc(t.mayor.name) + '\',false)">Rebuke the Mayor</button>' +
    '</div><div class="hint">A levy is an objective, not a command. The Mayor may buy it, divert it from the citizens, send half, or refuse outright.</div></div>';
  return h;
}

function viewEnterprise(id) {
  const e = W.enterprises.find((x) => x.id === id);
  if (!e) return viewRealm();
  let h = crumb([['realm', null, 'Realm']]) + '<h1>' + esc(e.name) + '</h1>' +
    '<div class="subtitle">' + e.type + ' &middot; independent &middot; ' +
    (e.owner === 'npc' ? 'held by its own people' : 'player held') + '</div>';

  h += '<div class="card"><h3>Its books</h3><div class="grid">' +
    kv('Produces', e.makes ? S.RESOURCES[e.makes].label + ' &times;' + e.capacity + '/wk' : 'nothing') +
    kv('In store', num(e.stock)) + kv('Treasury', num(e.treasury)) +
    kv('Tax owed', num(e.taxOwed)) + kv('Standing', Math.round(e.standing)) +
    kv('Mood', pill(e.mood, e.mood === 'resentful' ? 'bad' : e.mood === 'wary' ? 'warn' : 'good')) +
    kv('Last sold to', e.lastSoldTo || '&mdash;') +
    kv('Requisitioned', e.requisitions + ' times') + '</div>' +
    (e.hiding ? '<div class="hint" style="color:var(--bad)">It is hiding output from the crown.</div>' : '') +
    '</div>';

  h += assignmentHtml(e.name, 'choosing a buyer');

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="req(\'' + e.id + '\')">Requisition its stock</button>' +
    '<button onclick="feedback(\'' + esc(e.name) + '\',true)">Commend</button>' +
    '<button onclick="feedback(\'' + esc(e.name) + '\',false)">Rebuke</button></div>' +
    '<div class="hint">Requisition works, and it is sometimes necessary. It also burns standing, and an enterprise pushed far enough hides its output or turns to smuggling.</div></div>';
  return h;
}

function viewFort() {
  const f = W.fortress;
  return crumb([['realm', null, 'Realm']]) + '<h1>' + esc(f.name) + '</h1>' +
    '<div class="subtitle">' + esc(f.commander.name) + ' commands.</div>' +
    '<div class="card"><h3>The keep</h3><div class="grid">' +
    kv('Garrison', num(f.garrison)) + kv('Equipment', num(f.equipment)) +
    kv('Readiness', pct(f.readiness)) + kv('Commander standing', Math.round(f.commander.standing)) +
    '</div><div class="hint">The Commander tier is defined but not yet acting - it is one of the pieces the next round fills in.</div></div>';
}

function viewWild(id) {
  const s = W.wildSites.find((x) => x.id === id);
  if (!s) return viewRealm();
  return crumb([['realm', null, 'Realm']]) + '<h1>' + esc(s.name) + '</h1>' +
    '<div class="subtitle">Dangerous and profitable at once.</div>' +
    '<div class="card"><h3>The site</h3><div class="grid">' +
    kv('Status', pill(s.garrisoned ? 'garrisoned' : 'unheld', s.garrisoned ? 'good' : 'warn')) +
    kv('Threat', Math.round(s.threat)) + kv('Yields', S.RESOURCES[s.yields].label) +
    kv('Raids at', W.cfg.raidThreshold) + '</div>' +
    bar(1 - Math.min(1, s.threat / 100), 0.5, 0.3) +
    '<div class="hint">Held, it yields ' + W.cfg.wildYield + ' ' + S.RESOURCES[s.yields].label +
    ' a week and costs ' + num(W.cfg.garrisonCost) + '. Neglected, its threat climbs until it spills onto the roads. ' +
    'Whether that trade is worth making should change with the realm\'s circumstances - if it never does, the numbers are wrong.</div></div>' +
    '<div class="card"><h3>The Marshal on this site</h3>' + whyHtml(lastDecision('Marshal')) + '</div>';
}

function viewNeighbour(id) {
  const n = W.neighbours.find((x) => x.id === id);
  if (!n) return viewRealm();
  return crumb([['realm', null, 'Realm']]) + '<h1>' + esc(n.name) + '</h1>' +
    '<div class="subtitle">' + (n.isClan ? 'A player clan' : 'A rival kingdom') + '</div>' +
    '<div class="card"><h3>Relations</h3><div class="grid">' +
    kv('Standing', pill(n.relation, n.relation === 'embargo' ? 'bad' : n.relation === 'trade' ? 'good' : '')) +
    kv('Opinion of us', Math.round(n.opinion)) +
    kv('Holds', n.endowment.map((x) => S.RESOURCES[x].label).join(', ')) + '</div>' +
    '<div class="hint">We cannot make ' + S.RES_KEYS.filter((k) => !W.realm.endowment.includes(k)).map((k) => S.RESOURCES[k].label).join(' or ') +
    ' at all. Anyone who sells us those has leverage over us.</div></div>';
}

function crumb(parts) {
  return '<div class="crumb">' + parts.map((p) =>
    '<a onclick="go(\'' + p[0] + '\',' + (p[1] ? "'" + p[1] + "'" : 'null') + ')">' + p[2] + '</a>').join(' &rsaquo; ') + ' &rsaquo;</div>';
}

/* ---------- render -------------------------------------------------------- */

function renderNav() {
  let h = '<h2 class="sec">Realm</h2>' + navItem('realm', null, W.realm.name, 'week ' + W.tick);
  h += '<h2 class="sec">Castle</h2>' + navItem('castle', null, 'The Castle', W.realm.objective);
  ['Marshal', 'Justiciar', 'Chancellor', 'Envoy'].forEach((a) => { h += navItem('advisor', a, a, ''); });
  h += '<h2 class="sec">Towns</h2>';
  W.towns.forEach((t) => { h += navItem('town', t.id, t.name, t.defectedTo ? 'lost' : 'T' + t.tier + ' ' + pct(t.overall)); });
  h += '<h2 class="sec">Enterprises</h2>';
  W.enterprises.forEach((e) => { h += navItem('ent', e.id, e.name, num(e.stock)); });
  h += '<h2 class="sec">Holdings</h2>' + navItem('fort', null, W.fortress.name, '');
  W.wildSites.forEach((s) => { h += navItem('wild', s.id, s.name, s.garrisoned ? 'held' : 'thr ' + Math.round(s.threat)); });
  h += '<h2 class="sec">Abroad</h2>';
  W.neighbours.forEach((n) => { h += navItem('nb', n.id, n.name, n.relation); });
  $('nav').innerHTML = h;
}

function navItem(kind, id, name, sub) {
  const sel = view.kind === kind && view.id === id ? ' sel' : '';
  return '<div class="navitem' + sel + '" onclick="go(\'' + kind + '\',' + (id ? "'" + id + "'" : 'null') + ')">' +
    '<span class="nm">' + esc(name) + '</span><span class="sub">' + esc(sub) + '</span></div>';
}

function renderDetail() {
  const v = {
    realm: viewRealm, castle: viewCastle, advisor: () => viewAdvisor(view.id),
    town: () => viewTown(view.id), ent: () => viewEnterprise(view.id),
    fort: viewFort, wild: () => viewWild(view.id), nb: () => viewNeighbour(view.id),
  }[view.kind] || viewRealm;
  $('detail').innerHTML = v();
}

function renderChron() {
  $('chron').innerHTML = '<h2 class="sec">Chronicle</h2>' + W.chronicle.slice(0, 90).map((c) =>
    '<div class="ev ' + c.severity + '"><span class="wk">wk ' + c.tick + '</span>' + esc(c.text) + '</div>').join('');
}

function renderTop() {
  const live = W.towns.filter((t) => !t.defectedTo);
  const avg = live.length ? live.reduce((a, t) => a + t.overall, 0) / live.length : 0;
  $('hud').innerHTML =
    '<span class="stat">Week <b>' + W.tick + '</b></span>' +
    '<span class="stat">Year <b>' + (1 + Math.floor(W.tick / 52)) + '</b></span>' +
    '<span class="stat">Objective <b>' + W.realm.objective + '</b></span>' +
    '<span class="stat">Treasury <b>' + num(W.realm.treasury) + '</b></span>' +
    '<span class="stat">Realm fulfilment <b>' + pct(avg) + '</b></span>';
}

function render() { renderTop(); renderNav(); renderDetail(); renderChron(); }

/* ---------- controls ------------------------------------------------------ */

function go(kind, id) { view = { kind: kind, id: id }; render(); }
function step(n) { for (let i = 0; i < (n || 1); i++) S.tick(W); render(); }

function setSpeed(s) {
  speed = s;
  if (timer) { clearInterval(timer); timer = null; }
  document.querySelectorAll('#speeds button').forEach((b) => b.classList.toggle('on', +b.dataset.s === s));
  if (s > 0) timer = setInterval(() => step(1), s === 1 ? 700 : s === 2 ? 220 : 60);
}

function reset() {
  const seed = +($('seed').value || 7);
  W = S.makeWorld(seed, readTuner());
  view = { kind: 'realm', id: null };
  render();
}

function order(townId) {
  S.issueOrder(W, townId, $('ordRes').value, +$('ordQty').value || 50, 12);
  render();
}
function req(id) { S.requisition(W, id); render(); }
function feedback(agent, good) { S.giveFeedback(W, agent, good); render(); }
function ctx(kind) {
  S.giveContext(W, kind, kind === 'war' ? 'war is coming' : 'a famine is feared');
  render();
}

/* ---------- tuning drawer -------------------------------------------------- */

const TUNABLE = ['tierUpTicks', 'tierDownTicks', 'coverCycles', 'tierUpFulfil', 'tierDownFulfil',
  'basePrice', 'scarcityK', 'priceCap', 'townIncomePerHead', 'wagePerHead', 'treasuryReserve',
  'garrisonCost', 'wildYield', 'threatGrowth', 'raidThreshold',
  'refusalHit', 'complyGain', 'requisitionHit', 'importMarkup', 'embargoMarkup', 'popPerTier', 'popGrowth'];

function buildTuner() {
  $('tuner').innerHTML = '<h2 class="sec" style="margin:0 0 8px">Constants &mdash; live</h2>' +
    TUNABLE.map((k) => '<div class="row"><label for="c_' + k + '">' + k + '</label>' +
      '<input id="c_' + k + '" value="' + W.cfg[k] + '" onchange="applyTuner()"></div>').join('') +
    '<div class="hint">Changes apply immediately. Reset re-seeds the world with them.</div>';
}
function readTuner() {
  const o = {};
  TUNABLE.forEach((k) => { const el = $('c_' + k); if (el) o[k] = parseFloat(el.value); });
  return o;
}
function applyTuner() { Object.assign(W.cfg, readTuner()); render(); }

/* ---------- boot ----------------------------------------------------------- */

window.go = go; window.step = step; window.setSpeed = setSpeed; window.reset = reset;
window.order = order; window.req = req; window.feedback = feedback; window.ctx = ctx;
window.applyTuner = applyTuner;

buildTuner();
render();
