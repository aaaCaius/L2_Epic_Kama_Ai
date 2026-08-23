-- The town factor: an economy-aware merchant whose shelves are the town's stockpile.
-- Cloned from Lector (30001), a plain L2Merchant, so every stat is known-good.
-- Unarmed: rhand and lhand cleared, since a shopkeeper should not be holding a weapon.
-- Idempotent.

INSERT INTO `custom_npc`
  (`id`,`idTemplate`,`name`,`serverSideName`,`title`,`serverSideTitle`,`class`,
   `collision_radius`,`collision_height`,`level`,`sex`,`type`,`attackrange`,`hp`,`mp`,`hpreg`,`mpreg`,
   `str`,`con`,`dex`,`int`,`wit`,`men`,`exp`,`sp`,`patk`,`pdef`,`matk`,`mdef`,`atkspd`,`aggro`,`matkspd`,
   `rhand`,`lhand`,`armor`,`walkspd`,`runspd`,`faction_id`,`faction_range`,`isUndead`,`absorb_level`,`absorb_type`)
SELECT 100206, idTemplate, 'Town Factor', serverSideName, 'Economy', 1, class,
   collision_radius, collision_height, level, sex, 'L2EcoMerchant', attackrange, hp, mp, hpreg, mpreg,
   str, con, dex, `int`, wit, men, exp, sp, patk, pdef, matk, mdef, atkspd, aggro, matkspd,
   0, 0, armor, walkspd, runspd, faction_id, faction_range, isUndead, absorb_level, absorb_type
FROM `npc` WHERE id = 30001
ON DUPLICATE KEY UPDATE `type`='L2EcoMerchant', `name`='Town Factor', `title`='Economy', `rhand`=0, `lhand`=0;
