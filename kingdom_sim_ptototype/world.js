/* =============================================================================
   The world of Aden - all nine castle regions, from the real Interlude map.

   `wx`/`wy` place a region on the world map; `x`/`y` place a location inside its
   own region map. Both are schematic 0-100 positions read off the game map, not
   game coordinates. `loc` is the real place name, and is the handle that matters
   when this ports to the server - `MapRegionTable.getAreaCastle` already maps
   every town to one of these nine castles.

   The economic geography is the real one, and it is what makes trade necessary:

     Gludio      farms, lake, estate, mill, quarry, arena - NO mine, NO herbs
     Dion        the granary of the south; Floran's fields
     Giran       the trade crossroads - a great harbour and little else
     Oren        the Enchanted Valley and the Ivory Tower: herbs and learning
     Aden        the capital. Rich, hungry, and makes almost nothing
     Innadril    fish, wine and gardens
     Goddard     hot springs and two war camps: medicine and arms
     Rune        swamp reagents and the Primeval wharf - the rare goods
     Schuttgart  the Dwarven mines. Where the iron actually comes from

   No region is self-sufficient. That is the point.
   ============================================================================= */

window.AdenWorld = {

  /* ------------------------------------------------------------------ 1 */
  gludio: {
    id: 'gludio', name: 'Gludio', castleId: 1, wx: 26, wy: 52,
    blurb: 'Farms, a lake, an estate, a mill, a quarry and an arena — and no mine or apothecary anywhere in it.',
    castle: { id: 'c-gludio', loc: 'Gludio Castle', x: 86, y: 11 },
    fortress: { id: 'f-gludio', loc: 'Windmill Outpost', x: 57, y: 38, garrison: 60 },
    towns: [
      { id: 't-gludio',  loc: 'Town of Gludio',         x: 85, y: 18, pop: 300, tier: 2, mayor: 'Mayor Vasek' },
      { id: 't-gludin',  loc: 'Gludin Village',         x: 37, y: 36, pop: 240, tier: 2, mayor: 'Mayor Corran' },
      { id: 't-talking', loc: 'Talking Island Village', x: 40, y: 88, pop: 120, tier: 1, mayor: 'Reeve Almond' },
    ],
    enterprises: [
      { id: 'e-fellmere',  loc: 'Fellmere Harvesting Grounds', x: 47, y: 17, type: 'farm',    makes: 'food',      rate: 520 },
      { id: 'e-fellake',   loc: 'Fellmere Lake',               x: 50, y: 22, type: 'fishery', makes: 'food',      rate: 400 },
      { id: 'e-windawood', loc: 'Windawood Manor',             x: 77, y: 42, type: 'estate',  makes: 'cloth',     rate: 330 },
      { id: 'e-windmill',  loc: 'Windmill Hill',               x: 46, y: 45, type: 'mill',    makes: 'materials', rate: 230 },
      { id: 'e-wasteland', loc: 'The Wasteland',               x: 67, y: 61, type: 'quarry',  makes: 'materials', rate: 180 },
      { id: 'e-arena',     loc: 'Gludin Arena',                x: 32, y: 27, type: 'arena',   makes: 'luxury',    rate: 150 },
      { id: 'e-gludinhbr', loc: 'Gludin Harbour',              x: 23, y: 35, type: 'port',    makes: null,        rate: 0 },
      { id: 'e-talkhbr',   loc: 'Talking Island Harbour',      x: 30, y: 98, type: 'port',    makes: null,        rate: 0 },
    ],
    wildSites: [
      { id: 'w-orcbar',   loc: 'Orc Barracks',              x: 20, y: 12, yields: 'arms',      threat: 38 },
      { id: 'w-maille',   loc: 'Maille Lizardmen Barracks', x: 72, y: 11, yields: 'arms',      threat: 30 },
      { id: 'w-agony',    loc: 'Ruins of Agony',            x: 61, y: 15, yields: 'materials', threat: 26 },
      { id: 'w-bend',     loc: 'The Ruined Bend',           x: 69, y: 23, yields: 'materials', threat: 18 },
      { id: 'w-camp',     loc: 'Abandoned Camp',            x: 53, y: 28, yields: 'arms',      threat: 34 },
      { id: 'w-despair',  loc: 'Ruins of Despair',          x: 84, y: 33, yields: 'medicine',  threat: 30 },
      { id: 'w-antnest',  loc: 'The Ant Nest',              x: 84, y: 49, yields: 'materials', threat: 24 },
      { id: 'w-temple',   loc: 'Forgotten Temple',          x: 57, y: 56, yields: 'luxury',    threat: 22 },
      { id: 'w-olmahum',  loc: 'Ol Mahum Checkpoint',       x: 91, y: 65, yields: 'arms',      threat: 32 },
      { id: 'w-necro',    loc: 'Necropolis of Sacrifice',   x: 71, y: 71, yields: 'medicine',  threat: 28 },
      { id: 'w-langk',    loc: 'Langk Lizardmen Dwellings', x: 55, y: 73, yields: 'materials', threat: 26 },
      { id: 'w-elvenruin',loc: 'Elven Ruins',               x: 10, y: 86, yields: 'medicine',  threat: 16 },
      { id: 'w-evilhunt', loc: 'Evil Hunting Grounds',      x: 95, y: 5,  yields: 'medicine',  threat: 40 },
    ],
    landmarks: [
      { id: 'l-dusk',    loc: 'Oracle of Dusk',                    x: 36, y: 1 },
      { id: 'l-dawn',    loc: 'Oracle of Dawn',                    x: 36, y: 14 },
      { id: 'l-obelisk', loc: 'Obelisk of Victory',                x: 25, y: 84 },
      { id: 'l-einhov',  loc: "Einhovant's School of Magic",       x: 22, y: 93 },
      { id: 'l-cedric',  loc: "Cedric's Training Hall",            x: 50, y: 95 },
      { id: 'l-tieast',  loc: 'Talking Island, Eastern Territory', x: 17, y: 78 },
    ],
    roads: ['dion'],
  },

  /* ------------------------------------------------------------------ 2 */
  dion: {
    id: 'dion', name: 'Dion', castleId: 2, wx: 36, wy: 68,
    blurb: 'The granary of the south. Floran feeds half of Aden, and Cruma Tower keeps everyone honest.',
    castle: { id: 'c-dion', loc: 'Dion Castle', x: 50, y: 30 },
    fortress: { id: 'f-dion', loc: 'Fortress of Resistance', x: 24, y: 22, garrison: 70 },
    towns: [
      { id: 't-dion',   loc: 'Town of Dion',    x: 50, y: 40, pop: 280, tier: 3, mayor: 'Mayor Halbert' },
      { id: 't-floran', loc: 'Floran Village',  x: 40, y: 66, pop: 160, tier: 2, mayor: 'Reeve Idony' },
    ],
    enterprises: [
      { id: 'e-floranfarm', loc: 'Floran Agricultural Area', x: 34, y: 74, type: 'farm',   makes: 'food',      rate: 620 },
      { id: 'e-beehive',    loc: 'Bee Hive',                 x: 60, y: 60, type: 'apiary', makes: 'medicine',  rate: 190 },
      { id: 'e-dionplain',  loc: 'Plains of Dion',           x: 22, y: 44, type: 'pasture',makes: 'cloth',     rate: 300 },
      { id: 'e-crumamine',  loc: 'Cruma Marshlands',         x: 74, y: 50, type: 'quarry', makes: 'materials', rate: 210 },
    ],
    wildSites: [
      { id: 'w-cruma',   loc: 'Cruma Tower',        x: 70, y: 40, yields: 'luxury',    threat: 34 },
      { id: 'w-exec',    loc: 'Execution Grounds',  x: 62, y: 20, yields: 'arms',      threat: 42 },
      { id: 'w-tanor',   loc: 'Tanor Canyon',       x: 78, y: 72, yields: 'materials', threat: 36 },
      { id: 'w-gorgon',  loc: 'Gorgon Flower Garden', x: 84, y: 30, yields: 'medicine', threat: 30 },
    ],
    landmarks: [
      { id: 'l-partisan', loc: "Partisan's Hideout", x: 30, y: 34 },
    ],
    roads: ['gludio', 'giran', 'oren'],
  },

  /* ------------------------------------------------------------------ 3 */
  giran: {
    id: 'giran', name: 'Giran', castleId: 3, wx: 50, wy: 60,
    blurb: 'The crossroads of the world. A great harbour, a dragon on its doorstep, and very little farmland.',
    castle: { id: 'c-giran', loc: 'Giran Castle', x: 52, y: 22 },
    fortress: { id: 'f-giran', loc: "Breka's Stronghold", x: 26, y: 34, garrison: 80 },
    towns: [
      { id: 't-giran',    loc: 'Town of Giran',  x: 48, y: 44, pop: 340, tier: 3, mayor: 'Mayor Trask' },
      { id: 't-giranhbr', loc: 'Giran Harbor',   x: 36, y: 66, pop: 180, tier: 2, mayor: 'Harbourmaster Vell' },
    ],
    enterprises: [
      { id: 'e-giranport', loc: 'Giran Harbour Docks', x: 32, y: 72, type: 'port',   makes: null,        rate: 0 },
      { id: 'e-gorge',     loc: 'Death Pass Quarry',   x: 70, y: 34, type: 'quarry', makes: 'materials', rate: 260 },
      { id: 'e-hardin',    loc: "Hardin's Academy",    x: 22, y: 54, type: 'academy',makes: 'luxury',    rate: 170 },
    ],
    wildSites: [
      { id: 'w-dragon',  loc: 'Dragon Valley',    x: 74, y: 16, yields: 'arms',      threat: 52 },
      { id: 'w-deathp',  loc: 'Death Pass',       x: 66, y: 30, yields: 'arms',      threat: 44 },
      { id: 'w-devil',   loc: "Devil's Isle",     x: 88, y: 62, yields: 'medicine',  threat: 48 },
      { id: 'w-antharas',loc: "Antharas' Lair",   x: 84, y: 10, yields: 'luxury',    threat: 60 },
      { id: 'w-cave',    loc: 'Cave of Trials',   x: 18, y: 74, yields: 'materials', threat: 24 },
    ],
    landmarks: [
      { id: 'l-giranarena', loc: 'Giran Arena', x: 58, y: 52 },
    ],
    roads: ['dion', 'oren', 'innadril'],
  },

  /* ------------------------------------------------------------------ 4 */
  oren: {
    id: 'oren', name: 'Oren', castleId: 4, wx: 52, wy: 42,
    blurb: 'The Enchanted Valley and the Ivory Tower. Herbs, learning, and the elves who keep both.',
    castle: { id: 'c-oren', loc: 'Oren Castle', x: 56, y: 24 },
    fortress: { id: 'f-oren', loc: 'Elven Fortress', x: 24, y: 40, garrison: 65 },
    towns: [
      { id: 't-oren',     loc: 'Town of Oren',         x: 54, y: 38, pop: 300, tier: 3, mayor: 'Mayor Aldric' },
      { id: 't-elven',    loc: 'Elven Village',        x: 22, y: 60, pop: 170, tier: 2, mayor: 'Elder Maeryn' },
      { id: 't-darkelf',  loc: 'Dark Elven Village',   x: 30, y: 78, pop: 170, tier: 2, mayor: 'Matriarch Zesha' },
      { id: 't-ivory',    loc: 'Ivory Tower',          x: 62, y: 46, pop: 140, tier: 3, mayor: 'Archivist Belen' },
    ],
    enterprises: [
      { id: 'e-enchanted', loc: 'Enchanted Valley',   x: 78, y: 20, type: 'grove',  makes: 'medicine',  rate: 420 },
      { id: 'e-spores',    loc: 'Sea of Spores',      x: 70, y: 60, type: 'grove',  makes: 'medicine',  rate: 300 },
      { id: 'e-windyhill', loc: 'Windy Hill',         x: 36, y: 26, type: 'pasture',makes: 'cloth',     rate: 320 },
      { id: 'e-neutral',   loc: 'Neutral Zone Fields',x: 44, y: 68, type: 'farm',   makes: 'food',      rate: 340 },
      { id: 'e-ivorylab',  loc: 'Ivory Tower Laboratory', x: 66, y: 50, type: 'workshop', makes: 'luxury', rate: 200 },
    ],
    wildSites: [
      { id: 'w-timak',    loc: 'Timak Outpost',        x: 84, y: 34, yields: 'arms',      threat: 40 },
      { id: 'w-darkarts', loc: 'School of Dark Arts',  x: 24, y: 86, yields: 'luxury',    threat: 30 },
      { id: 'w-swamp',    loc: 'The Swampland',        x: 58, y: 74, yields: 'materials', threat: 32 },
      { id: 'w-plunder',  loc: 'Plunderous Plains',    x: 88, y: 52, yields: 'materials', threat: 34 },
    ],
    landmarks: [
      { id: 'l-mothertree', loc: 'Mother Tree', x: 16, y: 52 },
    ],
    roads: ['dion', 'giran', 'aden', 'schuttgart'],
  },

  /* ------------------------------------------------------------------ 5 */
  aden: {
    id: 'aden', name: 'Aden', castleId: 5, wx: 62, wy: 30,
    blurb: 'The capital. Rich, crowded, and it makes almost nothing it eats.',
    castle: { id: 'c-aden', loc: 'Aden Castle', x: 52, y: 16 },
    fortress: { id: 'f-aden', loc: 'Devastated Castle', x: 78, y: 26, garrison: 110 },
    towns: [
      { id: 't-aden',    loc: 'Town of Aden',      x: 50, y: 34, pop: 420, tier: 4, mayor: 'Lord Chamberlain Reyne' },
      { id: 't-hunters', loc: 'Hunters Village',   x: 28, y: 52, pop: 200, tier: 3, mayor: 'Mayor Dorne' },
    ],
    enterprises: [
      { id: 'e-adenmarket', loc: 'Aden Grand Market', x: 46, y: 40, type: 'market',  makes: 'luxury',    rate: 340 },
      { id: 'e-forsaken',   loc: 'Forsaken Plains',   x: 74, y: 54, type: 'pasture', makes: 'cloth',     rate: 260 },
      { id: 'e-mirror',     loc: 'Forest of Mirrors', x: 20, y: 70, type: 'grove',   makes: 'medicine',  rate: 200 },
    ],
    wildSites: [
      { id: 'w-massacre', loc: 'Fields of Massacre', x: 64, y: 44, yields: 'arms',      threat: 50 },
      { id: 'w-blazing',  loc: 'Blazing Swamp',      x: 70, y: 68, yields: 'materials', threat: 44 },
      { id: 'w-cemetery', loc: 'The Cemetery',       x: 40, y: 60, yields: 'medicine',  threat: 38 },
      { id: 'w-giants',   loc: "The Giants' Cave",   x: 86, y: 16, yields: 'luxury',    threat: 54 },
      { id: 'w-silent',   loc: 'Silent Valley',      x: 32, y: 22, yields: 'medicine',  threat: 42 },
      { id: 'w-anghel',   loc: 'Anghel Waterfall',   x: 16, y: 36, yields: 'materials', threat: 36 },
    ],
    landmarks: [
      { id: 'l-colosseum', loc: 'Coliseum', x: 60, y: 26 },
    ],
    roads: ['oren', 'goddard', 'rune'],
  },

  /* ------------------------------------------------------------------ 6 */
  innadril: {
    id: 'innadril', name: 'Innadril', castleId: 6, wx: 46, wy: 82,
    blurb: 'Fish, wine and gardens. The pleasantest place in Aden, and the least defended.',
    castle: { id: 'c-inna', loc: 'Innadril Castle', x: 62, y: 26 },
    fortress: { id: 'f-inna', loc: 'Innadril Garrison', x: 40, y: 24, garrison: 45 },
    towns: [
      { id: 't-heine', loc: 'Heine', x: 52, y: 40, pop: 260, tier: 3, mayor: 'Mayor Elsbeth' },
    ],
    enterprises: [
      { id: 'e-innaport', loc: 'Innadril Harbour',   x: 34, y: 56, type: 'port',    makes: null,     rate: 0 },
      { id: 'e-eva',      loc: 'Garden of Eva',      x: 70, y: 56, type: 'garden',  makes: 'luxury', rate: 380 },
      { id: 'e-silence',  loc: 'Field of Silence',   x: 74, y: 24, type: 'farm',    makes: 'food',   rate: 420 },
      { id: 'e-whisper',  loc: 'Field of Whispers',  x: 82, y: 40, type: 'farm',    makes: 'food',   rate: 380 },
      { id: 'e-innalake', loc: 'Innadril Fisheries', x: 30, y: 70, type: 'fishery', makes: 'food',   rate: 340 },
    ],
    wildSites: [
      { id: 'w-alligator', loc: 'Alligator Island', x: 24, y: 84, yields: 'materials', threat: 38 },
    ],
    landmarks: [
      { id: 'l-boat', loc: 'Innadril Pleasure Boat', x: 44, y: 66 },
    ],
    roads: ['giran', 'rune'],
  },

  /* ------------------------------------------------------------------ 7 */
  goddard: {
    id: 'goddard', name: 'Goddard', castleId: 7, wx: 76, wy: 30,
    blurb: 'Hot springs between two war camps. Medicine and arms, and never any peace.',
    castle: { id: 'c-goddard', loc: 'Goddard Castle', x: 50, y: 22 },
    fortress: { id: 'f-goddard', loc: 'Rune Border Post', x: 66, y: 62, garrison: 95 },
    towns: [
      { id: 't-goddard', loc: 'Town of Goddard', x: 48, y: 38, pop: 300, tier: 3, mayor: 'Mayor Kressen' },
    ],
    enterprises: [
      { id: 'e-springs', loc: 'Hot Springs',        x: 66, y: 44, type: 'springs', makes: 'medicine',  rate: 400 },
      { id: 'e-saints',  loc: 'Valley of Saints',   x: 28, y: 58, type: 'grove',   makes: 'medicine',  rate: 260 },
      { id: 'e-godfarm', loc: 'Goddard Terraces',   x: 40, y: 54, type: 'farm',    makes: 'food',      rate: 300 },
      { id: 'e-godquar', loc: 'Imperial Quarry',    x: 60, y: 20, type: 'quarry',  makes: 'materials', rate: 240 },
    ],
    wildSites: [
      { id: 'w-varka', loc: 'Varka Silenos Barracks', x: 22, y: 34, yields: 'arms',     threat: 46 },
      { id: 'w-ketra', loc: 'Ketra Orc Outpost',      x: 78, y: 28, yields: 'arms',     threat: 46 },
      { id: 'w-tomb',  loc: 'Imperial Tomb',          x: 54, y: 68, yields: 'luxury',   threat: 50 },
      { id: 'w-monast',loc: 'Monastery of Silence',   x: 34, y: 76, yields: 'medicine', threat: 40 },
    ],
    landmarks: [],
    roads: ['aden', 'rune'],
  },

  /* ------------------------------------------------------------------ 8 */
  rune: {
    id: 'rune', name: 'Rune', castleId: 8, wx: 82, wy: 55,
    blurb: 'Swamp reagents and the Primeval wharf. Everything rare comes through here, and everything here is dangerous.',
    castle: { id: 'c-rune', loc: 'Rune Castle', x: 44, y: 24 },
    fortress: { id: 'f-rune', loc: 'Wall of Argos', x: 70, y: 40, garrison: 100 },
    towns: [
      { id: 't-rune', loc: 'Rune Township', x: 44, y: 40, pop: 320, tier: 3, mayor: 'Mayor Sedric' },
    ],
    enterprises: [
      { id: 'e-primeval', loc: 'Primeval Isle Wharf', x: 84, y: 66, type: 'port',   makes: null,       rate: 0 },
      { id: 'e-swampreg', loc: 'Swamp of Screams',    x: 62, y: 60, type: 'grove',  makes: 'medicine', rate: 360 },
      { id: 'e-beastf',   loc: 'Beast Farm',          x: 30, y: 60, type: 'farm',   makes: 'food',     rate: 340 },
      { id: 'e-runeloom', loc: 'Rune Weavers',        x: 34, y: 30, type: 'estate', makes: 'cloth',    rate: 280 },
    ],
    wildSites: [
      { id: 'w-dead',   loc: 'Forest of the Dead', x: 24, y: 18, yields: 'medicine',  threat: 48 },
      { id: 'w-argos',  loc: 'Wall of Argos',      x: 72, y: 36, yields: 'arms',      threat: 52 },
      { id: 'w-primev', loc: 'Primeval Isle',      x: 90, y: 74, yields: 'luxury',    threat: 58 },
      { id: 'w-cave2',  loc: 'Cave of Souls',      x: 56, y: 16, yields: 'materials', threat: 40 },
    ],
    landmarks: [],
    roads: ['aden', 'goddard', 'innadril'],
  },

  /* ------------------------------------------------------------------ 9 */
  schuttgart: {
    id: 'schuttgart', name: 'Schuttgart', castleId: 9, wx: 30, wy: 16,
    blurb: 'The Dwarven mines. This is where the iron of Aden actually comes from.',
    castle: { id: 'c-schutt', loc: 'Schuttgart Castle', x: 50, y: 20 },
    fortress: { id: 'f-schutt', loc: 'Frozen Outpost', x: 70, y: 34, garrison: 75 },
    towns: [
      { id: 't-schutt',  loc: 'Town of Schuttgart', x: 48, y: 34, pop: 280, tier: 3, mayor: 'Mayor Brandt' },
      { id: 't-dwarven', loc: 'Dwarven Village',    x: 30, y: 60, pop: 200, tier: 3, mayor: 'Guildmaster Norn' },
      { id: 't-orc',     loc: 'Orc Village',        x: 62, y: 70, pop: 180, tier: 2, mayor: 'Chieftain Ugra' },
    ],
    enterprises: [
      { id: 'e-mithril', loc: 'Mithril Mines',        x: 22, y: 74, type: 'mine',   makes: 'arms',      rate: 480 },
      { id: 'e-coal',    loc: 'Abandoned Coal Mines', x: 36, y: 78, type: 'mine',   makes: 'materials', rate: 420 },
      { id: 'e-pavel',   loc: 'Pavel Ruins Works',    x: 60, y: 46, type: 'workshop',makes: 'arms',     rate: 260 },
      { id: 'e-schfarm', loc: 'Schuttgart Steadings', x: 44, y: 22, type: 'farm',   makes: 'food',      rate: 280 },
    ],
    wildSites: [
      { id: 'w-crypts', loc: 'Crypts of Disgrace',  x: 74, y: 20, yields: 'materials', threat: 40 },
      { id: 'w-denevil',loc: 'Den of Evil',         x: 82, y: 52, yields: 'arms',      threat: 46 },
      { id: 'w-frozen', loc: 'Frozen Labyrinth',    x: 68, y: 12, yields: 'materials', threat: 36 },
      { id: 'w-pavel',  loc: 'Pavel Ruins',         x: 58, y: 52, yields: 'luxury',    threat: 34 },
    ],
    landmarks: [
      { id: 'l-kamael', loc: 'Ivory Tower Crater', x: 20, y: 32 },
    ],
    roads: ['oren'],
  },
};

// Which region the player rules by default.
window.AdenWorld_playerRegion = 'gludio';
