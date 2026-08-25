/* =============================================================================
   The Gludio region, taken from the real Interlude world map.

   Every entry here is a place that actually exists in the game. `x` and `y` are
   schematic positions read off the map (0-100, origin top-left) so the prototype
   can draw the region roughly where it really sits; they are not game
   coordinates. When this ports to the server, `loc` is the handle that matters -
   the real place each abstraction stands for.

   One thing fell out of using the real map rather than invented sites, and it is
   the most interesting change in the whole prototype: **the Gludio region has no
   mine and no apothecary.** Food, cloth, stone and entertainment all have honest
   local sources. Arms and medicine do not. They can only come from garrisoning
   dangerous places - the barracks, the ruins, the checkpoints - or from buying
   them abroad.

   So the Marshal is no longer an optional expense. No garrisons, no weapons.
   ============================================================================= */

window.GludioMap = {
  name: 'Gludio',

  // ---- settlements -------------------------------------------------------
  towns: [
    { id: 'gludio',  loc: 'Town of Gludio',        x: 85, y: 18, pop: 300, tier: 2, mayor: 'Mayor Vasek' },
    { id: 'gludin',  loc: 'Gludin Village',        x: 37, y: 36, pop: 240, tier: 2, mayor: 'Mayor Corran' },
    { id: 'talking', loc: 'Talking Island Village', x: 40, y: 88, pop: 120, tier: 1, mayor: 'Reeve Almond' },
  ],

  castle: { id: 'castle', loc: 'Gludio Castle', x: 86, y: 11 },

  // The two banner markers on the map, read as border posts.
  fortress: { id: 'fort', loc: 'Windmill Outpost', x: 57, y: 38, garrison: 60 },

  // ---- enterprises: the honest producers ---------------------------------
  // Independent businesses. Note what is NOT here: no mine, no apothecary.
  enterprises: [
    { id: 'fellmere',  loc: 'Fellmere Harvesting Grounds', x: 47, y: 17, type: 'farm',    makes: 'food',      rate: 520 },
    { id: 'fellake',   loc: 'Fellmere Lake',               x: 50, y: 22, type: 'fishery', makes: 'food',      rate: 400 },
    { id: 'windawood', loc: 'Windawood Manor',             x: 77, y: 42, type: 'estate',  makes: 'cloth',     rate: 330 },
    { id: 'windmill',  loc: 'Windmill Hill',               x: 46, y: 45, type: 'mill',    makes: 'materials', rate: 230 },
    { id: 'wasteland', loc: 'The Wasteland',               x: 67, y: 61, type: 'quarry',  makes: 'materials', rate: 180 },
    { id: 'arena',     loc: 'Gludin Arena',                x: 32, y: 27, type: 'arena',   makes: 'luxury',    rate: 150 },
    { id: 'gludinhbr', loc: 'Gludin Harbour',              x: 23, y: 35, type: 'port',    makes: null,        rate: 0 },
    { id: 'talkhbr',   loc: 'Talking Island Harbour',      x: 30, y: 98, type: 'port',    makes: null,        rate: 0 },
  ],

  // ---- wild sites: dangerous, and the ONLY local source of arms and medicine
  wildSites: [
    { id: 'orcbar',   loc: 'Orc Barracks',              x: 20, y: 12, yields: 'arms',      threat: 38 },
    { id: 'maille',   loc: 'Maille Lizardmen Barracks', x: 72, y: 11, yields: 'arms',      threat: 30 },
    { id: 'agony',    loc: 'Ruins of Agony',            x: 61, y: 15, yields: 'materials', threat: 26 },
    { id: 'bend',     loc: 'The Ruined Bend',           x: 69, y: 23, yields: 'materials', threat: 18 },
    { id: 'camp',     loc: 'Abandoned Camp',            x: 53, y: 28, yields: 'arms',      threat: 34 },
    { id: 'despair',  loc: 'Ruins of Despair',          x: 84, y: 33, yields: 'medicine',  threat: 30 },
    { id: 'antnest',  loc: 'The Ant Nest',              x: 84, y: 49, yields: 'materials', threat: 24 },
    { id: 'temple',   loc: 'Forgotten Temple',          x: 57, y: 56, yields: 'luxury',    threat: 22 },
    { id: 'olmahum',  loc: 'Ol Mahum Checkpoint',       x: 91, y: 65, yields: 'arms',      threat: 32 },
    { id: 'necro',    loc: 'Necropolis of Sacrifice',   x: 71, y: 71, yields: 'medicine',  threat: 28 },
    { id: 'langk',    loc: 'Langk Lizardmen Dwellings', x: 55, y: 73, yields: 'materials', threat: 26 },
    { id: 'elvenruin',loc: 'Elven Ruins',               x: 10, y: 86, yields: 'medicine',  threat: 16 },
    { id: 'evilhunt', loc: 'Evil Hunting Grounds',      x: 95, y: 5,  yields: 'medicine',  threat: 40 },
  ],

  // ---- landmarks: no economy, but they are on the map and they place you ---
  landmarks: [
    { id: 'dusk',    loc: 'Oracle of Dusk',                x: 36, y: 1 },
    { id: 'dawn',    loc: 'Oracle of Dawn',                x: 36, y: 14 },
    { id: 'obelisk', loc: 'Obelisk of Victory',            x: 25, y: 84 },
    { id: 'einhov',  loc: "Einhovant's School of Magic",   x: 22, y: 93 },
    { id: 'cedric',  loc: "Cedric's Training Hall",        x: 50, y: 95 },
    { id: 'tieast',  loc: 'Talking Island, Eastern Territory', x: 17, y: 78 },
    { id: 'dionrd',  loc: 'Plains of Dion (to Dion)',      x: 97, y: 51 },
  ],

  // ---- what the region can and cannot make --------------------------------
  // Derived from the enterprises above, and stated explicitly because it is the
  // single most consequential fact about this region.
  endowment: ['food', 'cloth', 'materials', 'luxury'],
  mustSource: ['arms', 'medicine'],

  // Roads out of the region. Both harbours and the Dion road.
  neighbours: [
    { id: 'dion',  name: 'Dion',            endowment: ['food', 'luxury'],       relation: 'neutral', opinion: 55, via: 'Plains of Dion' },
    { id: 'oren',  name: 'Oren',            endowment: ['medicine', 'luxury'],   relation: 'neutral', opinion: 50, via: 'Gludin Harbour' },
    { id: 'clan',  name: 'Clan Ravenhold',  endowment: ['arms', 'materials'],    relation: 'neutral', opinion: 45, via: 'Talking Island Harbour', isClan: true },
  ],
};
