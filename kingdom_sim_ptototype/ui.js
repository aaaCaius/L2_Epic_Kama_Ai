/* =============================================================================
   UI for the kingdom prototype.

   Navigation IS the hierarchy, and it now starts a level higher:

     Aden  ->  region  ->  castle  ->  advisor
                       ->  town    ->  mayor / shop
                       ->  enterprise
                       ->  wild site

   Every entity uses the same panel shape - subject, what it is doing, why, its
   resources, how to intervene - because if one panel works everywhere then the
   harness architecture is probably right.
   ============================================================================= */

const S = window.Sim;
let W = S.makeWorld(7);
let view = { kind: 'world', region: W.player, id: null };
let timer = null;

const $ = (id) => document.getElementById(id);
const num = (n) => Math.round(n).toLocaleString();
const pct = (n) => Math.round(n * 100) + '%';
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const reg = () => W.regions[view.region] || W.regions[W.player];

function avgFulfil(R) {
  const live = R.towns.filter((t) => !t.defectedTo);
  return live.length ? live.reduce((a, t) => a + t.overall, 0) / live.length : 0;
}

/* ---------- decision lookup ---------------------------------------------- */
function lastDecision(agent) { return W.decisions.find((d) => d.agent === agent) || null; }

function whyHtml(d) {
  if (!d) return '<div class="hint">Nothing decided yet.</div>';
  return '<div class="why">' + d.why.map((o, i) =>
    '<div class="whyrow' + (i === 0 ? ' win' : '') + '">' +
    '<div class="sc">' + Math.round(o.score) + '</div>' +
    '<div><div class="lbl">' + esc(o.label) + '</div>' +
    '<div class="note">' + esc(o.note || '') + '</div></div></div>').join('') + '</div>';
}

function assignmentHtml(agent, context) {
  const d = lastDecision(agent);
  return '<div class="card"><h3>What it is doing</h3>' +
    (d ? '<div style="font-size:16px;margin-bottom:2px">' + esc(d.chose) + '</div>' +
         '<div class="subtitle" style="margin:0">' + esc(d.note || context || '') + ' &middot; week ' + d.tick + '</div>'
       : '<div class="hint">Idle.</div>') + '</div>' +
    '<div class="card"><h3>Why &mdash; everything it weighed</h3>' + whyHtml(d) + '</div>';
}

/* ---------- small pieces -------------------------------------------------- */
function bar(v, warnAt, badAt) {
  const cls = v < (badAt || 0.5) ? ' bad' : v < (warnAt || 0.8) ? ' warn' : '';
  return '<div class="bar' + cls + '"><i style="width:' + Math.max(0, Math.min(100, v * 100)) + '%"></i></div>';
}
const kv = (k, v) => '<div class="kv"><span class="k">' + k + '</span><span class="v">' + v + '</span></div>';
const pill = (t, c) => '<span class="pill ' + (c || '') + '">' + esc(t) + '</span>';
const resList = (arr) => arr.length ? arr.map((k) => S.RESOURCES[k].label).join(', ') : 'nothing';

function tile(kind, id, title, desc, meta, region) {
  const r = region ? "'" + region + "'" : 'null';
  return '<div class="tile" onclick="go(\'' + kind + '\',' + (id ? "'" + id + "'" : 'null') + ',' + r + ')">' +
    '<div class="t">' + title + '</div><div class="d">' + desc + '</div><div class="m">' + meta + '</div></div>';
}

function crumb(parts) {
  return '<div class="crumb">' + parts.map((p) =>
    '<a onclick="go(\'' + p[0] + '\',' + (p[1] ? "'" + p[1] + "'" : 'null') + ',' + (p[3] ? "'" + p[3] + "'" : 'null') + ')">' +
    p[2] + '</a>').join(' &rsaquo; ') + ' &rsaquo;</div>';
}
const base = () => [['world', null, 'Aden', null], ['region', null, reg().name, view.region]];

/* ---------- the world map ------------------------------------------------- */

function viewWorld() {
  let n = '';
  for (const id of W.order) {
    const R = W.regions[id];
    const f = avgFulfil(R);
    const cls = 'region' + (id === W.player ? ' mine' : '') +
      (f < 0.5 ? ' r-bad' : f < 0.85 ? ' r-warn' : ' r-good');
    n += '<div class="node ' + cls + '" style="left:' + R.wx + '%;top:' + R.wy + '%" ' +
      'onclick="go(\'region\',null,\'' + id + '\')">' +
      '<div class="dot"></div><div class="lbl">' + esc(R.name) + '</div>' +
      '<div class="sub2">' + pct(f) + ' &middot; ' + R.towns.length + ' towns</div></div>';
  }
  // Roads, drawn as thin links between region centres.
  let lines = '';
  for (const id of W.order) {
    const A = W.regions[id];
    for (const to of A.roads) {
      if (id > to) continue;
      const B = W.regions[to];
      if (!B) continue;
      const dx = B.wx - A.wx, dy = B.wy - A.wy;
      const len = Math.sqrt(dx * dx + dy * dy);
      const ang = Math.atan2(dy, dx) * 180 / Math.PI;
      const rel = S.relation(W, id, to);
      const cls = rel && rel.state === 'embargo' ? ' road-bad' : rel && rel.state === 'trade' ? ' road-good' : '';
      lines += '<div class="road' + cls + '" style="left:' + A.wx + '%;top:' + A.wy + '%;width:' + len + '%;transform:rotate(' + ang + 'deg)"></div>';
    }
  }

  let treaties = 0, embargoes = 0;
  for (const a of W.order) for (const b of W.order) if (a < b) {
    const st = W.relations[a][b].state;
    if (st === 'trade' || st === 'alliance') treaties++;
    if (st === 'embargo') embargoes++;
  }

  return '<h1>Aden</h1><div class="subtitle">Nine realms, ' +
    W.order.reduce((a, id) => a + W.regions[id].towns.length, 0) + ' towns, and not one of them self-sufficient. ' +
    'You rule <b>' + esc(W.regions[W.player].name) + '</b>.</div>' +
    '<div id="map" class="worldmap"><div class="inner">' + lines + n + '</div></div>' +
    '<div class="legend">' +
    '<span><i style="background:#6fae62"></i>fed</span>' +
    '<span><i style="background:#d99a3a"></i>strained</span>' +
    '<span><i style="background:#c9584f"></i>starving</span>' +
    '<span><i style="background:#c9a227"></i>your realm</span>' +
    '<span>' + treaties + ' treaties &middot; ' + embargoes + ' embargoes</span>' +
    '</div>' +
    '<div class="card" style="margin-top:16px"><h3>Who makes what</h3>' +
    W.order.map((id) => {
      const R = W.regions[id];
      return '<div class="kv"><span class="k">' + esc(R.name) + '</span><span class="v">makes ' +
        resList(R.endowment) + ' &middot; <span style="color:var(--bad)">needs ' + resList(R.mustSource) + '</span></span></div>';
    }).join('') +
    '<div class="hint">No realm makes everything. That is what gives the Envoy a job, and what makes an embargo bite.</div></div>';
}

/* ---------- a region map --------------------------------------------------- */

function viewRegion() {
  const R = reg();
  let n = '';
  const at = (o, cls, kind, id, label) =>
    '<div class="node ' + cls + '" style="left:' + o.x + '%;top:' + o.y + '%" ' +
    (kind ? 'onclick="go(\'' + kind + '\',' + (id ? "'" + id + "'" : 'null') + ',\'' + R.id + '\')"' : '') +
    '><div class="dot"></div><div class="lbl">' + esc(label) + '</div></div>';

  R.landmarks.forEach((m) => { n += at(m, 'mark', null, null, m.loc); });
  R.enterprises.forEach((e) => { n += at(e, e.type === 'port' ? 'port' : 'ent', 'ent', e.id, e.loc); });
  R.wildSites.forEach((s) => { n += at(s, 'wild' + (s.garrisoned ? ' held' : ''), 'wild', s.id, s.loc); });
  n += at(R.fortress, 'fort', 'fort', null, R.fortress.loc);
  R.towns.forEach((t) => { n += at(t, 'town', 'town', t.id, t.loc); });
  n += at(R.castle, 'castle', 'castle', null, R.castle.loc);

  const r = R.realm;
  return crumb([['world', null, 'Aden', null]]) +
    '<h1>' + esc(R.name) + (R.id === W.player ? ' <span class="pill good">your realm</span>' : '') + '</h1>' +
    '<div class="subtitle">' + esc(R.blurb) + '</div>' +
    '<div id="map"><div class="inner">' + n + '</div></div>' +
    '<div class="legend">' +
    '<span><i style="background:#c9a227"></i>town</span>' +
    '<span><i style="background:#e0c352;border-radius:2px"></i>castle</span>' +
    '<span><i style="background:#6f9ec4;border-radius:2px"></i>keep / garrisoned</span>' +
    '<span><i style="background:#6fae62"></i>enterprise</span>' +
    '<span><i style="background:#58a8b8"></i>harbour</span>' +
    '<span><i style="background:#c9584f"></i>wild &mdash; unheld</span>' +
    '<span><i style="background:#4a4f46"></i>landmark</span></div>' +

    '<div class="card" style="margin-top:16px"><h3>The realm</h3><div class="grid">' +
    kv('Objective', pill(r.objective, r.objective === 'SURVIVE' ? 'bad' : r.objective === 'GROW' ? 'good' : '')) +
    kv('Treasury', num(r.treasury)) + kv('Tax rate', pct(r.taxRate)) +
    kv('Fulfilment', pct(avgFulfil(R))) + kv('Law', r.lawPolicy) +
    kv('Garrisons', R.wildSites.filter((s) => s.garrisoned).length + ' of ' + R.wildSites.length) +
    '</div></div>' +

    '<div class="card"><h3>What it makes, and what it must buy</h3>' +
    kv('Makes', resList(R.endowment)) +
    kv('Must source', '<span style="color:var(--bad)">' + resList(R.mustSource) + '</span>') +
    kv('Roads to', R.roads.map((id) => esc(W.regions[id].name)).join(', ') || 'nowhere') +
    '</div>' +

    '<h2 class="sec" style="margin-left:0">The castle</h2><div class="tiles">' +
    tile('castle', null, esc(R.castle.loc), 'The Count and four advisors', 'objective ' + r.objective, R.id) + '</div>' +

    '<h2 class="sec" style="margin-left:0">Towns</h2><div class="tiles">' +
    R.towns.map((t) => t.defectedTo
      ? tile('town', t.id, esc(t.name), 'Defected to ' + esc(t.defectedTo), 'lost', R.id)
      : tile('town', t.id, esc(t.name), S.TIER_NAMES[t.tier] + ' &middot; ' + num(t.pop) + ' souls' + (t.rebel ? ' &middot; IN REVOLT' : ''),
             'fulfilment ' + pct(t.overall) + ' &middot; mayor ' + Math.round(t.mayor.standing), R.id)).join('') + '</div>' +

    '<h2 class="sec" style="margin-left:0">Enterprises &mdash; independent, not subordinate</h2><div class="tiles">' +
    R.enterprises.map((e) => tile('ent', e.id, esc(e.name), e.type + (e.makes ? ' &middot; ' + S.RESOURCES[e.makes].label : ' &middot; trade'),
      'stock ' + num(e.stock) + ' &middot; ' + e.mood, R.id)).join('') + '</div>' +

    '<h2 class="sec" style="margin-left:0">The keep and the wild places</h2><div class="tiles">' +
    tile('fort', null, esc(R.fortress.loc), 'Garrison ' + R.fortress.garrison, R.fortress.commander.name, R.id) +
    R.wildSites.map((s) => tile('wild', s.id, esc(s.name), (s.garrisoned ? 'Garrisoned' : 'Unheld') + ' &middot; yields ' + S.RESOURCES[s.yields].label,
      'threat ' + Math.round(s.threat), R.id)).join('') + '</div>';
}

/* ---------- castle and advisors --------------------------------------------- */

function viewCastle() {
  const R = reg(), r = R.realm;
  let h = crumb(base()) + '<h1>' + esc(R.castle.loc) + '</h1>' +
    '<div class="loc">on the map: ' + esc(R.castle.loc) + '</div>' +
    '<div class="subtitle">The Count governs. He runs nothing directly.</div>';

  h += assignmentHtml('Count of ' + R.name, 'setting the realm objective');

  h += '<div class="card"><h3>Allocation this week</h3><div class="grid">' +
    kv('Marshal', num(r.budgets.marshal) + ' (spent ' + num(r.spent.marshal) + ')') +
    kv('Justiciar', num(r.budgets.justiciar)) +
    kv('Envoy', num(r.budgets.envoy) + ' (traded ' + num(r.spent.envoy) + ')') +
    kv('Towns', num(r.budgets.towns) + ' (sent ' + num(r.spent.towns || 0) + ')') +
    '</div><div class="hint">The Envoy\'s share scales with how much the realm must buy. ' +
    'A region that sources four of six goods cannot run on the same budget as one that sources two.</div></div>';

  h += '<h2 class="sec" style="margin-left:0">Advisors</h2><div class="tiles">' +
    [['Marshal', 'military, garrisons, campaigns'], ['Justiciar', 'law, outlawry, contraband'],
     ['Chancellor', 'taxes, budgets, the word no'], ['Envoy', 'treaties, tariffs, embargoes']]
      .map(([nm, d]) => {
        const dec = lastDecision(nm + ' of ' + R.name);
        return tile('advisor', nm, nm, d, dec ? esc(dec.chose) : 'idle', R.id);
      }).join('') + '</div>';

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="feedback(\'Count of ' + esc(R.name) + '\',true)">Commend the Count</button>' +
    '<button onclick="feedback(\'Count of ' + esc(R.name) + '\',false)">Rebuke the Count</button>' +
    '<button onclick="ctx(\'war\')">Warn of war</button>' +
    '<button onclick="ctx(\'famine\')">Warn of famine</button></div>' +
    '<div class="hint">Feedback writes to the same record the agent builds from its own outcomes.</div></div>';
  return h;
}

function viewAdvisor(name) {
  const R = reg(), agent = name + ' of ' + R.name;
  const blurb = {
    Marshal: 'Raises troops and garrisons the wild places. In a region that makes no arms, he is the only source of them.',
    Justiciar: 'Sets the law: suppress the vice trade, tolerate it, or tax it.',
    Chancellor: 'Holds the purse, and says what cannot be afforded.',
    Envoy: 'Treaties and tariffs with the other eight realms.',
  }[name] || '';

  let h = crumb(base().concat([['castle', null, 'Castle', R.id]])) +
    '<h1>' + esc(name) + ' of ' + esc(R.name) + '</h1><div class="subtitle">' + blurb + '</div>' +
    assignmentHtml(agent, '');

  if (name === 'Marshal') {
    h += '<div class="card"><h3>Wild places</h3>' + R.wildSites.map((s) =>
      '<div class="kv"><span class="k">' + esc(s.name) + ' ' +
      pill(s.garrisoned ? 'held' : 'unheld', s.garrisoned ? 'good' : (s.threat > W.cfg.raidThreshold ? 'bad' : 'warn')) +
      '</span><span class="v">threat ' + Math.round(s.threat) + ' &middot; yields ' + S.RESOURCES[s.yields].label +
      (R.endowment.indexOf(s.yields) < 0 ? ' <b style="color:var(--gold)">(no other local source)</b>' : '') +
      '</span></div>').join('') + '</div>';
  }
  if (name === 'Envoy') {
    h += '<div class="card"><h3>Relations with the other realms</h3>' +
      W.order.filter((id) => id !== R.id).map((id) => {
        const o = W.regions[id], rel = S.relation(W, R.id, id);
        const useful = o.endowment.filter((k) => R.mustSource.indexOf(k) >= 0);
        return '<div class="kv"><span class="k">' + esc(o.name) + ' ' +
          pill(rel.state, rel.state === 'embargo' ? 'bad' : rel.state === 'trade' ? 'good' : '') +
          (R.roads.indexOf(id) >= 0 ? ' <span class="pill">road</span>' : '') +
          '</span><span class="v">opinion ' + Math.round(rel.opinion) +
          (useful.length ? ' &middot; <b style="color:var(--gold)">has our ' + resList(useful) + '</b>' : '') +
          '</span></div>';
      }).join('') +
      '<div class="hint">' + esc(R.name) + ' cannot make ' + resList(R.mustSource) +
      '. Anyone who sells us those has leverage over us.</div></div>';
  }

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="feedback(\'' + esc(agent) + '\',true)">Commend</button>' +
    '<button onclick="feedback(\'' + esc(agent) + '\',false)">Rebuke</button></div>' + lessonLine(agent) + '</div>';
  return h;
}

function lessonLine(agent) {
  const l = W.lessons[agent];
  return l ? '<div class="hint">Commended ' + l.good + ' times, rebuked ' + l.bad + '.</div>'
           : '<div class="hint">No feedback recorded yet.</div>';
}

/* ---------- town, enterprise, fort, wild ------------------------------------ */

function viewTown(id) {
  const R = reg(), t = R.towns.find((x) => x.id === id);
  if (!t) return viewRegion();

  let h = crumb(base()) + '<h1>' + esc(t.name) + '</h1>' +
    '<div class="loc">on the map: ' + esc(t.loc) + ' &middot; ' + esc(R.name) + '</div>' +
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
    h += '<div class="card"><h3>Standing order from the Count</h3><div>' +
      num(t.mayor.order.qty) + ' ' + S.RESOURCES[t.mayor.order.what].label + ' by week ' + t.mayor.order.by + '</div></div>';
  }
  if (t.mayor.lastRefusal) {
    h += '<div class="card"><h3>Last refusal</h3><div>Week ' + t.mayor.lastRefusal.tick +
      ' &mdash; &ldquo;' + esc(t.mayor.lastRefusal.reason) + '&rdquo;</div></div>';
  }

  h += '<div class="card"><h3>Stores and fulfilment</h3>';
  for (const k of S.unlockedFor(t.tier)) {
    const f = t.fulfil[k] === undefined ? 1 : t.fulfil[k];
    const imported = R.endowment.indexOf(k) < 0;
    h += '<div style="margin-bottom:9px"><div class="kv" style="border:none;padding:0">' +
      '<span class="k">' + S.RESOURCES[k].label + (imported ? ' <span class="pill">imported</span>' : '') + '</span>' +
      '<span class="v">' + num(t.stock[k]) + ' held &middot; ' + num(t.pop * S.RESOURCES[k].rate) + '/wk &middot; ' + pct(f) + '</span></div>' +
      bar(f) + '</div>';
  }
  h += '</div>';

  h += '<div class="card"><h3>Shops within the walls</h3><div class="tiles">' +
    t.shops.map((s) => '<div class="tile"><div class="t">' + esc(s.name) + '</div>' +
      '<div class="d">sells ' + S.RESOURCES[s.sells].label + '</div>' +
      '<div class="m">town holds ' + num(t.stock[s.sells]) + ' &middot; ' + pct(t.fulfil[s.sells] || 0) + ' met</div></div>').join('') +
    '</div></div>';

  const mine = R.contracts.filter((c) => c.townId === t.id && (c.status === 'open' || c.status === 'taken'));
  h += '<div class="card"><h3>Contracts posted &mdash; a contract is a quest</h3>' +
    (mine.length ? mine.map((c) => {
      const taker = c.taker ? R.enterprises.find((e) => e.id === c.taker) : null;
      return '<div class="kv"><span class="k">' + num(c.qty) + ' ' + S.RESOURCES[c.res].label + ' by week ' + c.deadline +
        '</span><span class="v">' + (taker ? esc(taker.name) + ' &middot; ' + num(c.delivered) + '/' + num(c.qty) : 'unclaimed') +
        ' &middot; ' + num(c.price) + '/unit</span></div>';
    }).join('') : '<div class="hint">Nothing outstanding.</div>') + '</div>';

  h += '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<select id="ordRes">' + S.RES_KEYS.map((k) => '<option value="' + k + '">' + S.RESOURCES[k].label + '</option>').join('') + '</select>' +
    '<input id="ordQty" type="number" value="80" style="width:80px">' +
    '<button onclick="order(\'' + t.id + '\')">Order a levy</button>' +
    '<button onclick="feedback(\'' + esc(t.mayor.name) + '\',true)">Commend</button>' +
    '<button onclick="feedback(\'' + esc(t.mayor.name) + '\',false)">Rebuke</button></div>' +
    '<div class="hint">A levy is an objective, not a command. The Mayor may buy it, divert it from the citizens, ' +
    'send half, or refuse outright &mdash; and refusal escalates.</div></div>';
  return h;
}

function viewEnterprise(id) {
  const R = reg(), e = R.enterprises.find((x) => x.id === id);
  if (!e) return viewRegion();
  return crumb(base()) + '<h1>' + esc(e.name) + '</h1>' +
    '<div class="loc">on the map: ' + esc(e.loc) + ' &middot; ' + esc(R.name) + '</div>' +
    '<div class="subtitle">' + e.type + ' &middot; independent &middot; ' +
    (e.owner === 'npc' ? 'held by its own people' : 'player held') + '</div>' +
    '<div class="card"><h3>Its books</h3><div class="grid">' +
    kv('Produces', e.makes ? S.RESOURCES[e.makes].label + ' &times;' + e.capacity + '/wk' : 'nothing &mdash; it moves trade') +
    kv('In store', num(e.stock)) + kv('Treasury', num(e.treasury)) +
    kv('Tax owed', num(e.taxOwed)) + kv('Standing', Math.round(e.standing)) +
    kv('Mood', pill(e.mood, e.mood === 'resentful' ? 'bad' : e.mood === 'wary' ? 'warn' : 'good')) +
    kv('Last sold to', e.lastSoldTo || '&mdash;') + kv('Requisitioned', e.requisitions + ' times') + '</div>' +
    (e.hiding ? '<div class="hint" style="color:var(--bad)">It is hiding output from the crown.</div>' : '') + '</div>' +
    assignmentHtml(e.name, 'choosing a buyer') +
    '<div class="card"><h3>Intervene</h3><div class="levers">' +
    '<button onclick="req(\'' + e.id + '\')">Requisition its stock</button>' +
    '<button onclick="feedback(\'' + esc(e.name) + '\',true)">Commend</button>' +
    '<button onclick="feedback(\'' + esc(e.name) + '\',false)">Rebuke</button></div>' +
    '<div class="hint">Requisition works, and is sometimes necessary. It also burns standing, and an enterprise ' +
    'pushed far enough hides its output or turns to smuggling.</div></div>';
}

function viewFort() {
  const R = reg(), f = R.fortress;
  return crumb(base()) + '<h1>' + esc(f.name) + '</h1>' +
    '<div class="loc">on the map: ' + esc(f.loc) + ' &middot; ' + esc(R.name) + '</div>' +
    '<div class="subtitle">' + esc(f.commander.name) + ' commands.</div>' +
    '<div class="card"><h3>The keep</h3><div class="grid">' +
    kv('Garrison', num(f.garrison)) + kv('Equipment', num(f.equipment)) +
    kv('Readiness', pct(f.readiness)) + kv('Commander standing', Math.round(f.commander.standing)) + '</div>' +
    '<div class="hint">The Commander tier is defined but not yet acting &mdash; one of the pieces the next round fills in.</div></div>';
}

function viewWild(id) {
  const R = reg(), s = R.wildSites.find((x) => x.id === id);
  if (!s) return viewRegion();
  const only = R.endowment.indexOf(s.yields) < 0;
  return crumb(base()) + '<h1>' + esc(s.name) + '</h1>' +
    '<div class="loc">on the map: ' + esc(s.loc) + ' &middot; ' + esc(R.name) + '</div>' +
    '<div class="subtitle">Dangerous and profitable at once' +
    (only ? ', and <b>' + esc(R.name) + ' has no other local source of ' + S.RESOURCES[s.yields].label + '</b>' : '') + '.</div>' +
    '<div class="card"><h3>The site</h3><div class="grid">' +
    kv('Status', pill(s.garrisoned ? 'garrisoned' : 'unheld', s.garrisoned ? 'good' : 'warn')) +
    kv('Threat', Math.round(s.threat)) + kv('Yields', S.RESOURCES[s.yields].label) +
    kv('Raids at', W.cfg.raidThreshold) + '</div>' + bar(1 - Math.min(1, s.threat / 100), 0.5, 0.3) +
    '<div class="hint">Held, it yields ' + W.cfg.wildYield + ' ' + S.RESOURCES[s.yields].label +
    ' a week and costs ' + num(W.cfg.garrisonCost) + '. Neglected, its threat climbs until it spills onto the roads.</div></div>' +
    '<div class="card"><h3>The Marshal on this region\'s sites</h3>' + whyHtml(lastDecision('Marshal of ' + R.name)) + '</div>';
}

/* ---------- render ---------------------------------------------------------- */

function renderNav() {
  let h = '<h2 class="sec">The world</h2>' +
    navItem('world', null, 'Aden', W.order.length + ' realms', null);
  h += '<h2 class="sec">Realms</h2>';
  for (const id of W.order) {
    const R = W.regions[id];
    h += navItem('region', null, R.name + (id === W.player ? ' ★' : ''), pct(avgFulfil(R)), id);
  }
  const R = reg();
  h += '<h2 class="sec">' + esc(R.name) + '</h2>' +
    navItem('castle', null, 'The Castle', R.realm.objective, R.id);
  ['Marshal', 'Justiciar', 'Chancellor', 'Envoy'].forEach((a) => { h += navItem('advisor', a, a, '', R.id); });
  R.towns.forEach((t) => { h += navItem('town', t.id, t.name, t.defectedTo ? 'lost' : 'T' + t.tier + ' ' + pct(t.overall), R.id); });
  R.enterprises.forEach((e) => { h += navItem('ent', e.id, e.name, num(e.stock), R.id); });
  h += navItem('fort', null, R.fortress.name, '', R.id);
  R.wildSites.forEach((s) => { h += navItem('wild', s.id, s.name, s.garrisoned ? 'held' : 'thr ' + Math.round(s.threat), R.id); });
  $('nav').innerHTML = h;
}

function navItem(kind, id, name, sub, region) {
  const sel = view.kind === kind && view.id === id && (!region || view.region === region) ? ' sel' : '';
  return '<div class="navitem' + sel + '" onclick="go(\'' + kind + '\',' + (id ? "'" + id + "'" : 'null') + ',' +
    (region ? "'" + region + "'" : 'null') + ')">' +
    '<span class="nm">' + esc(name) + '</span><span class="sub">' + esc(sub) + '</span></div>';
}

function renderDetail() {
  const v = {
    world: viewWorld, region: viewRegion, castle: viewCastle,
    advisor: () => viewAdvisor(view.id), town: () => viewTown(view.id),
    ent: () => viewEnterprise(view.id), fort: viewFort, wild: () => viewWild(view.id),
  }[view.kind] || viewWorld;
  $('detail').innerHTML = v();
}

function renderChron() {
  const scope = view.kind === 'world' ? null : view.region;
  const list = W.chronicle.filter((c) => !scope || c.scope === scope || c.scope === 'world');
  $('chron').innerHTML = '<h2 class="sec">Chronicle' + (scope ? ' &mdash; ' + esc(reg().name) : ' &mdash; all Aden') + '</h2>' +
    list.slice(0, 90).map((c) => '<div class="ev ' + c.severity + '"><span class="wk">wk ' + c.tick + '</span>' + esc(c.text) + '</div>').join('');
}

function renderTop() {
  const mine = W.regions[W.player];
  let fed = 0, total = 0;
  for (const id of W.order) { total++; if (avgFulfil(W.regions[id]) > 0.85) fed++; }
  $('hud').innerHTML =
    '<span class="stat">Week <b>' + W.tick + '</b></span>' +
    '<span class="stat">Year <b>' + (1 + Math.floor(W.tick / 52)) + '</b></span>' +
    '<span class="stat">' + esc(mine.name) + ' <b>' + mine.realm.objective + '</b></span>' +
    '<span class="stat">Treasury <b>' + num(mine.realm.treasury) + '</b></span>' +
    '<span class="stat">Realms fed <b>' + fed + '/' + total + '</b></span>';
}

function render() { renderTop(); renderNav(); renderDetail(); renderChron(); }

/* ---------- controls --------------------------------------------------------- */

function go(kind, id, region) {
  view = { kind: kind, id: id, region: region || view.region };
  render();
}
function step(n) { for (let i = 0; i < (n || 1); i++) S.tick(W); render(); }

function setSpeed(s) {
  if (timer) { clearInterval(timer); timer = null; }
  document.querySelectorAll('#speeds button').forEach((b) => b.classList.toggle('on', +b.dataset.s === s));
  if (s > 0) timer = setInterval(() => step(1), s === 1 ? 700 : s === 2 ? 220 : 60);
}

function reset() {
  W = S.makeWorld(+($('seed').value || 7), readTuner());
  view = { kind: 'world', region: W.player, id: null };
  render();
}

function order(townId) { S.issueOrder(W, townId, $('ordRes').value, +$('ordQty').value || 50, 12); render(); }
function req(id) { S.requisition(W, id); render(); }
function feedback(agent, good) { S.giveFeedback(W, agent, good); render(); }
function ctx(kind) { S.giveContext(W, kind, kind === 'war' ? 'war is coming' : 'a famine is feared'); render(); }

/* ---------- tuning drawer ----------------------------------------------------- */

const TUNABLE = ['tierUpTicks', 'tierDownTicks', 'coverCycles', 'tierUpFulfil', 'tierDownFulfil',
  'postureTicks', 'basePrice', 'scarcityK', 'priceCap', 'townIncomePerHead', 'wagePerHead',
  'treasuryReserve', 'garrisonCost', 'wildYield', 'threatGrowth', 'raidThreshold',
  'refusalHit', 'complyGain', 'requisitionHit', 'tradeMarkup', 'embargoMarkup', 'allyMarkup',
  'popPerTier', 'popGrowth'];

function buildTuner() {
  $('tuner').innerHTML = '<h2 class="sec" style="margin:0 0 8px">Constants &mdash; live</h2>' +
    TUNABLE.map((k) => '<div class="row"><label for="c_' + k + '">' + k + '</label>' +
      '<input id="c_' + k + '" value="' + W.cfg[k] + '" onchange="applyTuner()"></div>').join('') +
    '<div class="hint">Changes apply immediately. Reset re-seeds the world with them.</div>';
}
function readTuner() {
  const o = {};
  TUNABLE.forEach((k) => { const el = $('c_' + k); if (el && el.value !== '') o[k] = parseFloat(el.value); });
  return o;
}
function applyTuner() { Object.assign(W.cfg, readTuner()); render(); }

/* ---------- boot -------------------------------------------------------------- */

window.go = go; window.step = step; window.setSpeed = setSpeed; window.reset = reset;
window.order = order; window.req = req; window.feedback = feedback; window.ctx = ctx;
window.applyTuner = applyTuner;

buildTuner();
render();
