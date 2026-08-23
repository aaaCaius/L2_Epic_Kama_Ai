-- Convert chosen stock merchants into economy shops.
-- They keep their own catalogue (shops.xml inherit="true"); only the quantity
-- becomes real. Reversible: set the type back to L2Merchant.
UPDATE `npc` SET `type` = 'L2EcoMerchant' WHERE `id` IN (30001, 30002, 30003);
