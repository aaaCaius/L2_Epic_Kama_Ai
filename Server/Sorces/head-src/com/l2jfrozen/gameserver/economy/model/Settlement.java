package com.l2jfrozen.gameserver.economy.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.datatables.EconomyDataTable;
import com.l2jfrozen.gameserver.templates.L2Item;

/**
 * A settlement, and every mechanic that acts on it.<BR>
 * <BR>
 * The numbers here were arrived at by running the model rather than by reasoning about it - see
 * <code>AI_Tools/docs/economy-model-test.md</code>. Six structural flaws in the original specification
 * only appeared once it was simulated, and four of them are corrected in this class. They are marked
 * <b>F3</b> to <b>F8</b> below, because each looks like a mistake until you know what it prevents.
 */
public class Settlement
{
	private final int id;
	private String name;

	private int tier;
	private double population;
	private long treasury;
	private double satisfaction;
	private int tierFloor;

	private int upStreak;
	private int downStreak;
	private long lastCycle;

	/** itemId -> quantity held. */
	private final Map<Integer, Integer> stock = new HashMap<>();

	/** Last cycle's fulfilment per need, kept for pricing and for the admin view. */
	private final Map<String, Double> fulfilment = new HashMap<>();

	private double lastOverall = 1.0;
	private boolean dirty;

	public Settlement(final int id, final String name, final int tier, final int population, final long treasury, final int tierFloor)
	{
		this.id = id;
		this.name = name;
		this.tier = tier;
		this.population = population;
		this.treasury = treasury;
		this.tierFloor = tierFloor;
		satisfaction = 50;
	}

	// ------------------------------------------------------------------ the cycle

	/**
	 * Advance the settlement by one economic cycle.
	 * @param  playersOnline how many players are active in this region
	 * @return               overall fulfilment for the cycle, 0..1
	 */
	public double runCycle(final int playersOnline)
	{
		final EconomyDataTable data = EconomyDataTable.getInstance();

		// F6: activity scales production as well as consumption. Scaling only consumption made a
		// quiet server over-produce, so an empty world grew faster than a busy one.
		final double activity = Math.max(EconomyConfig.MIN_ACTIVITY,
			Math.min(1.0, (double) playersOnline / Math.max(1, EconomyConfig.EXPECTED_PLAYERS)));

		// F5: the town stocks what it is growing INTO, not only what it eats. Without this a
		// tier-1 town never holds clothing, so it can never satisfy the tier-2 requirement.
		final List<NeedDef> consumes = data.getNeedsUpTo(tier);
		final List<NeedDef> trades = data.getNeedsUpTo(Math.min(5, tier + 1));

		if (consumes.isEmpty())
		{
			return 1.0;
		}

		produce(trades, activity);
		final double overall = consume(consumes, activity);
		updateSatisfaction(consumes, overall);
		exportSurplus(trades, activity);
		payUpkeep();
		driftPopulation();
		evaluateTier(overall, activity);

		lastOverall = overall;
		lastCycle = System.currentTimeMillis();
		dirty = true;

		return overall;
	}

	/** NPC-held sites produce, spread across the goods the town trades in proportion to demand. */
	private void produce(final List<NeedDef> trades, final double activity)
	{
		final double output = EconomyConfig.NPC_SITE_OUTPUT * outputMultiplier() * activity;

		double rateSum = 0;

		for (final NeedDef n : trades)
		{
			rateSum += n.getRatePerHead();
		}

		if (rateSum <= 0)
		{
			return;
		}

		for (final NeedDef n : trades)
		{
			final int share = (int) Math.round(output * n.getRatePerHead() / rateSum);

			if (share > 0 && !n.getItemIds().isEmpty())
			{
				addStock(n.getItemIds().get(0).intValue(), share);
			}
		}
	}

	/** Draw down the stockpile, and report how well each need was met. */
	private double consume(final List<NeedDef> consumes, final double activity)
	{
		double weighted = 0;
		double weightSum = 0;

		for (final NeedDef n : consumes)
		{
			final double required = population * n.getRatePerHead() * activity;

			if (required <= 0)
			{
				continue;
			}

			final int took = takeForNeed(n, (int) Math.ceil(required));
			final double met = Math.min(1.0, took / required);

			fulfilment.put(n.getId(), Double.valueOf(met));
			weighted += n.getWeight() * met;
			weightSum += n.getWeight();
		}

		return weightSum > 0 ? weighted / weightSum : 1.0;
	}

	/**
	 * F8: luxury <b>multiplies</b> fulfilment rather than adding to it.<BR>
	 * <BR>
	 * Additively, flooding a starving town with wine raised its satisfaction and therefore its output -
	 * the model held satisfaction near 100 while fulfilment sat at 0.65. Multiplying means luxury can
	 * amplify a well-fed town but cannot rescue a hungry one. Bread before circuses.
	 */
	private void updateSatisfaction(final List<NeedDef> consumes, final double overall)
	{
		double luxFactor = 1.0;

		for (final NeedDef n : consumes)
		{
			if (!"luxury".equals(n.getId()))
			{
				continue;
			}

			final Double met = fulfilment.get(n.getId());
			final double consumedPerHead = met == null ? 0 : met.doubleValue() * n.getRatePerHead();
			final double L = consumedPerHead / Math.max(0.0001, n.getRatePerHead() * EconomyConfig.LUXURY_OPTIMUM);

			// Single-peaked: rises to the optimum, back to neutral at twice it, harmful beyond.
			luxFactor = 1.0 + EconomyConfig.LUXURY_AMPLITUDE * L * (2 - L);

			// Excess vice makes people ill, which shows up as an unexplained run on medicine.
			if (L > 1.0)
			{
				final NeedDef med = findNeed(consumes, "medicine");

				if (med != null)
				{
					takeForNeed(med, (int) Math.round(EconomyConfig.SICKNESS_FACTOR * (L - 1.0) * population));
				}
			}
		}

		satisfaction = Math.max(0, Math.min(100, 100 * overall * luxFactor));
	}

	/** Anything above the growth target leaves through the harbour, at the worst price available. */
	private void exportSurplus(final List<NeedDef> trades, final double activity)
	{
		final double growPop = EconomyConfig.POPULATION_PER_TIER * Math.min(5, tier + 1);

		for (final NeedDef n : trades)
		{
			final double keep = growPop * n.getRatePerHead() * activity
				* EconomyConfig.TIER_COVER_CYCLES * EconomyConfig.EXPORT_BUFFER;

			int held = stockFor(n);

			if (held <= keep)
			{
				continue;
			}

			int excess = (int) (held - keep);

			for (final Integer itemId : n.getItemIds())
			{
				if (excess <= 0)
				{
					break;
				}

				final int have = getStock(itemId.intValue());
				final int sell = Math.min(have, excess);

				if (sell <= 0)
				{
					continue;
				}

				removeStock(itemId.intValue(), sell);
				treasury += (long) (sell * referencePrice(itemId.intValue()) * EconomyConfig.EXPORT_PRICE_FRACTION);
				excess -= sell;
			}
		}
	}

	private void payUpkeep()
	{
		treasury -= (long) (population * EconomyConfig.WAGE_PER_HEAD) + (long) EconomyConfig.GARRISON_COST_PER_TIER * tier;

		if (treasury < 0)
		{
			treasury = 0;
		}
	}

	private void driftPopulation()
	{
		final double cap = EconomyConfig.POPULATION_PER_TIER * tier;
		population += (cap - population) * EconomyConfig.POPULATION_GROWTH * (satisfaction / 100);
		population = Math.max(10, population);
	}

	/**
	 * F3: the tier gate is on <b>stock held</b>, not on supply flowing in.<BR>
	 * <BR>
	 * Gating on flow deadlocked the whole economy. Purchases are driven by stock deficit, so once stock
	 * equilibrates the town stops buying and throttles its own inflow - the measure converged just below
	 * the threshold and stayed there permanently, with players supplying and nothing ever happening.
	 * Coverage of the next tier's needs is reachable, and legible: the granaries are full.
	 */
	private void evaluateTier(final double overall, final double activity)
	{
		final EconomyDataTable data = EconomyDataTable.getInstance();
		final int next = Math.min(5, tier + 1);
		final double growPop = EconomyConfig.POPULATION_PER_TIER * next;

		boolean covered = true;

		for (final NeedDef n : data.getNeedsUpTo(next))
		{
			final double want = growPop * n.getRatePerHead() * activity
				* EconomyConfig.TIER_COVER_CYCLES * EconomyConfig.TIER_COVER_FRACTION;

			if (stockFor(n) < want)
			{
				covered = false;
				break;
			}
		}

		upStreak = overall >= EconomyConfig.TIER_UP_THRESHOLD && covered ? upStreak + 1 : 0;
		downStreak = overall < EconomyConfig.TIER_DOWN_THRESHOLD ? downStreak + 1 : 0;

		if (upStreak >= EconomyConfig.TIER_UP_CYCLES && tier < 5)
		{
			tier++;
			upStreak = 0;
		}
		// F7: the demotion threshold must sit above where an under-supplied town settles, or towns
		// rise to a tier they cannot sustain and stall there permanently, half-fed and bankrupt.
		else if (downStreak >= EconomyConfig.TIER_DOWN_CYCLES && tier > tierFloor)
		{
			tier--;
			downStreak = 0;
		}
	}

	// ------------------------------------------------------------------ stock

	public int getStock(final int itemId)
	{
		final Integer q = stock.get(Integer.valueOf(itemId));
		return q == null ? 0 : q.intValue();
	}

	public void addStock(final int itemId, final int qty)
	{
		if (qty <= 0)
		{
			return;
		}

		stock.put(Integer.valueOf(itemId), Integer.valueOf(getStock(itemId) + qty));
		dirty = true;
	}

	public boolean removeStock(final int itemId, final int qty)
	{
		final int have = getStock(itemId);

		if (qty <= 0 || have < qty)
		{
			return false;
		}

		stock.put(Integer.valueOf(itemId), Integer.valueOf(have - qty));
		dirty = true;
		return true;
	}

	/** @return everything the settlement holds that serves this need */
	public int stockFor(final NeedDef need)
	{
		int total = 0;

		for (final Integer itemId : need.getItemIds())
		{
			total += getStock(itemId.intValue());
		}

		return total;
	}

	/** Drain a need's items, cheapest first, so luxuries are not eaten as staples. */
	private int takeForNeed(final NeedDef need, final int wanted)
	{
		if (wanted <= 0)
		{
			return 0;
		}

		final List<Integer> ids = new ArrayList<>(need.getItemIds());
		ids.sort((a, b) -> Integer.compare(referencePrice(a.intValue()), referencePrice(b.intValue())));

		int taken = 0;

		for (final Integer itemId : ids)
		{
			if (taken >= wanted)
			{
				break;
			}

			final int take = Math.min(getStock(itemId.intValue()), wanted - taken);

			if (take > 0)
			{
				removeStock(itemId.intValue(), take);
				taken += take;
			}
		}

		return taken;
	}

	public Map<Integer, Integer> getAllStock()
	{
		return stock;
	}

	// ------------------------------------------------------------------ prices

	/** How short the settlement is of a need, 0 (full) to 1 (nothing). */
	public double scarcityOf(final NeedDef need)
	{
		final Double met = fulfilment.get(need.getId());
		return met == null ? 0.5 : Math.max(0, 1 - met.doubleValue());
	}

	/** What the town pays a player for one unit. Rises with scarcity, capped. */
	public int buyPrice(final int itemId)
	{
		final NeedDef need = EconomyDataTable.getInstance().getNeedForItem(itemId);

		if (need == null)
		{
			return 0;
		}

		final double mult = Math.min(EconomyConfig.BUY_CAP, 1 + EconomyConfig.BUY_SCARCITY_K * scarcityOf(need));
		return (int) Math.max(1, referencePrice(itemId) * mult / 2);
	}

	/** What the town charges a player for one unit. */
	public int sellPrice(final int itemId)
	{
		final NeedDef need = EconomyDataTable.getInstance().getNeedForItem(itemId);
		final double scarcity = need == null ? 0 : scarcityOf(need);
		return (int) Math.max(1, referencePrice(itemId) * (1 + EconomyConfig.SELL_SCARCITY_K * scarcity));
	}

	/**
	 * Can the town afford to buy this, and does it still want it?<BR>
	 * Bounded two ways, both essential: never below the treasury reserve, and never past the growth
	 * target. A town that is broke or full simply stops buying - prices fall and players sell elsewhere.
	 */
	public boolean willBuy(final int itemId, final int qty)
	{
		final NeedDef need = EconomyDataTable.getInstance().getNeedForItem(itemId);

		if (need == null || qty <= 0)
		{
			return false;
		}

		final long cost = (long) buyPrice(itemId) * qty;

		if (treasury - cost < EconomyConfig.TREASURY_RESERVE)
		{
			return false;
		}

		final double growPop = EconomyConfig.POPULATION_PER_TIER * Math.min(5, tier + 1);
		final double want = growPop * need.getRatePerHead() * EconomyConfig.TIER_COVER_CYCLES;

		return stockFor(need) < want;
	}

	private static int referencePrice(final int itemId)
	{
		final L2Item item = ItemTable.getInstance().getTemplate(itemId);
		return item == null ? 1 : Math.max(1, item.getReferencePrice());
	}

	private static NeedDef findNeed(final List<NeedDef> needs, final String id)
	{
		for (final NeedDef n : needs)
		{
			if (n.getId().equals(id))
			{
				return n;
			}
		}

		return null;
	}

	public double outputMultiplier()
	{
		return Math.max(0.5, Math.min(1.25, 0.75 + satisfaction / 100));
	}

	// ------------------------------------------------------------------ accessors

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public int getTier()
	{
		return tier;
	}

	public void setTier(final int tier)
	{
		this.tier = Math.max(1, Math.min(5, tier));
		dirty = true;
	}

	public int getPopulation()
	{
		return (int) population;
	}

	public void setPopulation(final int pop)
	{
		population = Math.max(1, pop);
		dirty = true;
	}

	public long getTreasury()
	{
		return treasury;
	}

	public void addTreasury(final long amount)
	{
		treasury = Math.max(0, treasury + amount);
		dirty = true;
	}

	public void setTreasury(final long amount)
	{
		treasury = Math.max(0, amount);
		dirty = true;
	}

	public int getSatisfaction()
	{
		return (int) satisfaction;
	}

	public void setSatisfaction(final int value)
	{
		satisfaction = Math.max(0, Math.min(100, value));
		dirty = true;
	}

	public int getTierFloor()
	{
		return tierFloor;
	}

	public void setTierFloor(final int floor)
	{
		tierFloor = floor;
	}

	public double getLastOverall()
	{
		return lastOverall;
	}

	public Map<String, Double> getFulfilment()
	{
		return fulfilment;
	}

	public int getUpStreak()
	{
		return upStreak;
	}

	public int getDownStreak()
	{
		return downStreak;
	}

	public long getLastCycle()
	{
		return lastCycle;
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void clean()
	{
		dirty = false;
	}
}
