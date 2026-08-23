package com.l2jfrozen.gameserver.economy;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.l2jfrozen.L2Properties;

/**
 * Every tunable number in the economy engine.<BR>
 * <BR>
 * Deliberately separate from {@link com.l2jfrozen.Config}: these values are expected to change often,
 * and they are reloadable in place with <code>//reload economy</code>. Nothing economic is hardcoded in
 * Java - if a number affects how the economy behaves, it belongs here.<BR>
 * <BR>
 * Several defaults carry the scars of the model test in
 * <code>AI_Tools/docs/economy-model-test.md</code>; those are noted where they matter.
 */
public class EconomyConfig
{
	private static final Logger LOGGER = Logger.getLogger(EconomyConfig.class);

	private static final String FILE = "config/economy/economy.properties";

	/** Master switch. False leaves the server behaving exactly as it did before the engine existed. */
	public static boolean ENABLED;

	public static int CYCLE_MINUTES;
	public static int EXPECTED_PLAYERS;
	public static double MIN_ACTIVITY;

	public static int TIER_COVER_CYCLES;
	public static double TIER_COVER_FRACTION;
	public static double TIER_UP_THRESHOLD;
	public static int TIER_UP_CYCLES;
	public static double TIER_DOWN_THRESHOLD;
	public static int TIER_DOWN_CYCLES;
	public static int POPULATION_PER_TIER;
	public static double POPULATION_GROWTH;

	public static double LUXURY_OPTIMUM;
	public static double LUXURY_AMPLITUDE;
	public static double SICKNESS_FACTOR;

	public static double BUY_SCARCITY_K;
	public static double BUY_CAP;
	public static double SELL_SCARCITY_K;
	public static long TREASURY_RESERVE;
	public static double EXPORT_PRICE_FRACTION;
	public static double EXPORT_BUFFER;

	public static int NPC_SITE_OUTPUT;

	/**
	 * Settlement used when a location maps to a region that has none of its own. While only one
	 * settlement exists, this is what lets an economy NPC work anywhere rather than silently doing
	 * nothing outside the Gludio domain. 0 disables the fallback.
	 */
	public static int DEFAULT_SETTLEMENT;

	/** How many of each good a town tries to keep on its shelves. */
	public static int SHOP_STOCK_TARGET;

	/** Cap on units of any one good the town will manufacture per cycle. */
	public static int SHOP_CRAFT_PER_CYCLE;

	/** Cap on units of any one good the town will buy in from outside per cycle. */
	public static int SHOP_IMPORT_PER_CYCLE;

	public static int WAGE_PER_HEAD;
	public static int GARRISON_COST_PER_TIER;

	public static boolean SAVE_ALL_ACTIONS;
	public static boolean LOG_TRANSACTIONS;
	public static int SNAPSHOT_EVERY_CYCLES;

	public static void load()
	{
		try
		{
			final L2Properties p = new L2Properties();
			final InputStream is = new FileInputStream(new File(FILE));
			p.load(is);
			is.close();

			ENABLED = Boolean.parseBoolean(p.getProperty("EnableEconomy", "false"));

			CYCLE_MINUTES = Integer.parseInt(p.getProperty("CycleMinutes", "60"));
			EXPECTED_PLAYERS = Integer.parseInt(p.getProperty("ExpectedPlayers", "20"));
			MIN_ACTIVITY = Double.parseDouble(p.getProperty("MinActivity", "0.35"));

			TIER_COVER_CYCLES = Integer.parseInt(p.getProperty("TierCoverCycles", "4"));
			TIER_COVER_FRACTION = Double.parseDouble(p.getProperty("TierCoverFraction", "0.9"));
			TIER_UP_THRESHOLD = Double.parseDouble(p.getProperty("TierUpThreshold", "0.90"));
			TIER_UP_CYCLES = Integer.parseInt(p.getProperty("TierUpCycles", "12"));
			TIER_DOWN_THRESHOLD = Double.parseDouble(p.getProperty("TierDownThreshold", "0.70"));
			TIER_DOWN_CYCLES = Integer.parseInt(p.getProperty("TierDownCycles", "3"));
			POPULATION_PER_TIER = Integer.parseInt(p.getProperty("PopulationPerTier", "120"));
			POPULATION_GROWTH = Double.parseDouble(p.getProperty("PopulationGrowth", "0.04"));

			LUXURY_OPTIMUM = Double.parseDouble(p.getProperty("LuxuryOptimum", "1.0"));
			LUXURY_AMPLITUDE = Double.parseDouble(p.getProperty("LuxuryAmplitude", "0.25"));
			SICKNESS_FACTOR = Double.parseDouble(p.getProperty("SicknessFactor", "0.5"));

			BUY_SCARCITY_K = Double.parseDouble(p.getProperty("BuyScarcityK", "3.0"));
			BUY_CAP = Double.parseDouble(p.getProperty("BuyCap", "4.0"));
			SELL_SCARCITY_K = Double.parseDouble(p.getProperty("SellScarcityK", "1.5"));
			TREASURY_RESERVE = Long.parseLong(p.getProperty("TreasuryReserve", "500000"));
			EXPORT_PRICE_FRACTION = Double.parseDouble(p.getProperty("ExportPriceFraction", "0.45"));
			EXPORT_BUFFER = Double.parseDouble(p.getProperty("ExportBuffer", "1.8"));

			NPC_SITE_OUTPUT = Integer.parseInt(p.getProperty("NpcSiteOutput", "820"));
			DEFAULT_SETTLEMENT = Integer.parseInt(p.getProperty("DefaultSettlement", "1"));
			SHOP_STOCK_TARGET = Integer.parseInt(p.getProperty("ShopStockTarget", "40"));
			SHOP_CRAFT_PER_CYCLE = Integer.parseInt(p.getProperty("ShopCraftPerCycle", "20"));
			SHOP_IMPORT_PER_CYCLE = Integer.parseInt(p.getProperty("ShopImportPerCycle", "10"));

			WAGE_PER_HEAD = Integer.parseInt(p.getProperty("WagePerHead", "12"));
			GARRISON_COST_PER_TIER = Integer.parseInt(p.getProperty("GarrisonCostPerTier", "2000"));

			SAVE_ALL_ACTIONS = Boolean.parseBoolean(p.getProperty("SaveAllActions", "true"));
			LOG_TRANSACTIONS = Boolean.parseBoolean(p.getProperty("LogTransactions", "true"));
			SNAPSHOT_EVERY_CYCLES = Integer.parseInt(p.getProperty("SnapshotEveryCycles", "1"));

			LOGGER.info("EconomyConfig: loaded " + FILE + " (enabled=" + ENABLED + ").");
		}
		catch (final Exception e)
		{
			ENABLED = false;
			LOGGER.error("EconomyConfig: failed to load " + FILE + " - economy disabled.", e);
		}
	}
}
