-- Living Aden economy engine - schema.
-- Idempotent: safe to re-run. Never DROPs. See AI_Tools/docs/database-rework.md.

CREATE TABLE IF NOT EXISTS `eco_settlement` (
  `settlement_id` INT          NOT NULL,
  `name`          VARCHAR(40)  NOT NULL DEFAULT '',
  `tier`          TINYINT      NOT NULL DEFAULT 1,
  `population`    INT          NOT NULL DEFAULT 120,
  `treasury`      BIGINT       NOT NULL DEFAULT 1000000,
  `satisfaction`  SMALLINT     NOT NULL DEFAULT 50,
  `tier_floor`    TINYINT      NOT NULL DEFAULT 1,
  `up_streak`     INT          NOT NULL DEFAULT 0,
  `down_streak`   INT          NOT NULL DEFAULT 0,
  `last_cycle`    BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`settlement_id`)
);

CREATE TABLE IF NOT EXISTS `eco_stock` (
  `settlement_id` INT NOT NULL,
  `item_id`       INT NOT NULL,
  `qty`           INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`settlement_id`,`item_id`)
);

CREATE TABLE IF NOT EXISTS `eco_indicator` (
  `ts`            BIGINT   NOT NULL,
  `settlement_id` INT      NOT NULL,
  `tier`          TINYINT  NOT NULL,
  `population`    INT      NOT NULL,
  `treasury`      BIGINT   NOT NULL,
  `satisfaction`  SMALLINT NOT NULL,
  `fulfilment`    SMALLINT NOT NULL,
  KEY `k_ts` (`ts`),
  KEY `k_set` (`settlement_id`,`ts`)
);

CREATE TABLE IF NOT EXISTS `eco_txn` (
  `ts`            BIGINT      NOT NULL,
  `kind`          VARCHAR(16) NOT NULL,
  `settlement_id` INT         NOT NULL DEFAULT 0,
  `char_id`       INT         NOT NULL DEFAULT 0,
  `item_id`       INT         NOT NULL DEFAULT 0,
  `qty`           INT         NOT NULL DEFAULT 0,
  `adena`         BIGINT      NOT NULL DEFAULT 0,
  KEY `k_ts` (`ts`),
  KEY `k_kind` (`kind`,`ts`)
);

-- The Gludio domain: Talking Island Village, Gludin Village, Town of Gludio all
-- answer to castle 1 (MapRegionTable.getAreaCastle). One settlement for now.
INSERT INTO `eco_settlement` (`settlement_id`,`name`,`tier`,`population`,`treasury`,`tier_floor`)
VALUES (1,'Gludio Domain',1,120,1000000,1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
