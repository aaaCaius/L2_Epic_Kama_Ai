-- ----------------------------
-- Table structure for character_skills_save
-- ----------------------------
DROP TABLE IF EXISTS `character_skills_save`;
CREATE TABLE `character_skills_save` (
  `char_obj_id` int(11) NOT NULL DEFAULT '0',
  `skill_id` int(11) NOT NULL DEFAULT '0',
  `skill_level` int(11) NOT NULL DEFAULT '0',
  `effect_count` int(11) NOT NULL DEFAULT '0',
  `effect_cur_time` int(11) NOT NULL DEFAULT '0',
  `buff_index` int(2) NOT NULL DEFAULT '0',
  PRIMARY KEY (`char_obj_id`,`skill_id`)
) ;
