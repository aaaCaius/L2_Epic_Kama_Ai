package com.l2jfrozen.gameserver.economy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One of a town's needs, and the items that can satisfy it.<BR>
 * <BR>
 * A need is deliberately satisfied by <i>several</i> items rather than one. That is what lets players
 * choose how to meet a shortage, and what stops a single missing item id from starving a settlement.
 */
public class NeedDef
{
	private final String id;
	private final int unlockTier;
	private final double weight;
	private final double ratePerHead;
	private final List<Integer> itemIds = new ArrayList<>();

	public NeedDef(final String id, final int unlockTier, final double weight, final double ratePerHead)
	{
		this.id = id;
		this.unlockTier = unlockTier;
		this.weight = weight;
		this.ratePerHead = ratePerHead;
	}

	public void addItem(final int itemId)
	{
		itemIds.add(Integer.valueOf(itemId));
	}

	public String getId()
	{
		return id;
	}

	/** @return the tier at which the town starts to need this at all */
	public int getUnlockTier()
	{
		return unlockTier;
	}

	/** @return how heavily this need counts toward overall fulfilment */
	public double getWeight()
	{
		return weight;
	}

	/** @return units consumed per citizen per cycle */
	public double getRatePerHead()
	{
		return ratePerHead;
	}

	public List<Integer> getItemIds()
	{
		return itemIds;
	}

	public boolean accepts(final int itemId)
	{
		return itemIds.contains(Integer.valueOf(itemId));
	}
}
