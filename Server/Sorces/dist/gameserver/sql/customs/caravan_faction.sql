-- ---------------------------------------------------------------------------
-- Caravan: link the trader to its escort, and make it survivable on the road
-- ---------------------------------------------------------------------------
-- npc 100203 "Caravan Leader" (L2NpcCaravan) travels a route from
-- data/csv/walker_routes.csv. npc 100204 "Soldier" (L2EscortGard) guards it.
--
-- The faction link is what makes the escort respond: L2CaravanAI notifies every
-- living same-faction NPC within faction_range with EVT_AGGRESSION when the
-- caravan is attacked, and the stock L2AttackableAI on the guards turns that
-- into an attack. No escort-side code is needed.
--
-- NOTE: sql/installer_db.bat only iterates install/, never customs/. Run this
-- by hand:
--   mysql -h 127.0.0.1 -u root -D frozen < caravan_faction.sql

UPDATE `npc` SET `faction_id` = 'caravan', `faction_range` = 1000
  WHERE `id` IN (100203, 100204);

-- The caravan was level 80 while its escort is level 20, so the guards died
-- instantly and never mattered. Bring the caravan down to match them.
--
-- runspd was 126 - slower than its own escort (140) and slower than most
-- level-20 mobs, while buffed players run 150+. Fleeing would have been purely
-- cosmetic. At 150 it outruns an unbuffed player but a buffed one can still run
-- it down, so killing the escort first stays the sensible tactic.
UPDATE `npc` SET `level` = 20, `runspd` = 150 WHERE `id` = 100203;

-- The Caravan Manager is a town NPC, not a fighter, but was created holding a
-- Bec de Corbin (item 94) - copied from the Soldier escort, which uses the same.
-- Clear both hands so it stands unarmed.
UPDATE `npc` SET `rhand` = 0, `lhand` = 0 WHERE `id` = 100205;
