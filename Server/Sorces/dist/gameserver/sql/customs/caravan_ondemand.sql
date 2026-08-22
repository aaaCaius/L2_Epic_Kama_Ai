-- ---------------------------------------------------------------------------
-- Caravans are dispatched on demand, not spawned by the world
-- ---------------------------------------------------------------------------
-- The Caravan Manager (npc 100205) spawns convoys through CaravanSpawnManager:
-- one Caravan Leader plus its Soldier escort, placed at the first waypoint of
-- the route, walking it once and despawning together at the end.
--
-- These static spawn rows are therefore removed. Leaving them would put
-- permanent caravans and stranded guards in the world alongside the
-- dispatched ones, and the guards would never find a caravan to escort.
--
-- Run by hand - installer_db.bat only iterates install/, never customs/:
--   mysql -h 127.0.0.1 -u root -D frozen < caravan_ondemand.sql

DELETE FROM `custom_spawnlist` WHERE `npc_templateid` IN (100203, 100204);
