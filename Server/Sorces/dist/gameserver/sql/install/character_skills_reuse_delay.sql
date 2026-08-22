-- ----------------------------
-- Table structure for character_skills_reuse_delay
-- ----------------------------
DROP TABLE IF EXISTS `character_skills_reuse_delay`;
CREATE TABLE `character_skills_reuse_delay` (
  `char_obj_id` int(11) NOT NULL,
  `skill_id` int(11) NOT NULL,
  `skill_level` int(11) DEFAULT NULL,
  `reuse_delay` int(11) DEFAULT NULL,
  `systime` bigint(20) DEFAULT NULL,
  `class_index` int(11) NOT NULL,
  PRIMARY KEY (`char_obj_id`,`skill_id`,`class_index`)
) ;
