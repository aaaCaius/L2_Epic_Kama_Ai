package com.l2jfrozen.gameserver.economy.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a town shop may sell.<BR>
 * <BR>
 * Only the catalogue lives here - <i>how much</i> is on the shelf is the settlement's actual stockpile.
 * That is the whole point: the shop stops being a list of infinite goods and becomes a window onto what
 * the town has managed to produce.
 */
public class ShopDef
{
	private final int npcId;
	private final String title;

	private final List<Integer> itemIds = new ArrayList<>();
	private final Set<Integer> essential = new HashSet<>();

	public ShopDef(final int npcId, final String title)
	{
		this.npcId = npcId;
		this.title = title;
	}

	public void add(final int itemId, final boolean isEssential)
	{
		itemIds.add(Integer.valueOf(itemId));

		if (isEssential)
		{
			essential.add(Integer.valueOf(itemId));
		}
	}

	public int getNpcId()
	{
		return npcId;
	}

	public String getTitle()
	{
		return title;
	}

	public List<Integer> getItemIds()
	{
		return itemIds;
	}

	/**
	 * @param  itemId the item
	 * @return        true if a player must never be blocked from buying this. The line stays on the
	 *                shelf even at zero stock; the town imports the shortfall and pays for it.
	 */
	public boolean isEssential(final int itemId)
	{
		return essential.contains(Integer.valueOf(itemId));
	}
}
