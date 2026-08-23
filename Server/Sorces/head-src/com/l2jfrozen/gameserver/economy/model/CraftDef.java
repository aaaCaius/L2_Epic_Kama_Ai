package com.l2jfrozen.gameserver.economy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One conversion the town runs on itself: raw materials in, finished goods out.<BR>
 * <BR>
 * This is the missing middle of the production chain. A town does not need iron ore, it needs swords -
 * and without something turning one into the other, a stockpile of materials never reaches a shelf and
 * the shop half of the economy has nothing to sell.
 */
public class CraftDef
{
	/** An item id and how many of it. */
	public static class Part
	{
		public final int itemId;
		public final int count;

		public Part(final int itemId, final int count)
		{
			this.itemId = itemId;
			this.count = count;
		}
	}

	private final String id;
	private final int tier;
	private final int capacity;

	private final List<Part> inputs = new ArrayList<>();
	private Part output;

	public CraftDef(final String id, final int tier, final int capacity)
	{
		this.id = id;
		this.tier = tier;
		this.capacity = capacity;
	}

	public void addInput(final int itemId, final int count)
	{
		inputs.add(new Part(itemId, count));
	}

	public void setOutput(final int itemId, final int count)
	{
		output = new Part(itemId, count);
	}

	public String getId()
	{
		return id;
	}

	/** @return the settlement tier at which the town can run this at all */
	public int getTier()
	{
		return tier;
	}

	/** @return outputs per cycle at full productivity, before the satisfaction multiplier */
	public int getCapacity()
	{
		return capacity;
	}

	public List<Part> getInputs()
	{
		return inputs;
	}

	public Part getOutput()
	{
		return output;
	}
}
