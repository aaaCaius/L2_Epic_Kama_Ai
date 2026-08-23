package com.l2jfrozen.gameserver.economy.trade;

import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.economy.managers.SettlementManager;
import com.l2jfrozen.gameserver.economy.model.NeedDef;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.economy.telemetry.EconomyLog;
import com.l2jfrozen.gameserver.model.actor.instance.L2ItemInstance;
import com.l2jfrozen.gameserver.model.L2TradeList;

/**
 * A merchant buylist backed by a settlement's stockpile.<BR>
 * <BR>
 * This is the whole finite-stock mechanic, and it needs <b>no change at all</b> to the purchase path.
 * {@link L2TradeList#decreaseCount} is public and called polymorphically from
 * <code>RequestBuyItem</code>, and the merchant type check there accepts any subclass of
 * <code>L2MerchantInstance</code> - so overriding one method is enough to make a shop genuinely run
 * out.<BR>
 * <BR>
 * The Interlude client already renders remaining counts and hides sold-out lines, so there is no client
 * work either.
 */
public class EcoTradeList extends L2TradeList
{
	private final int settlementId;

	public EcoTradeList(final int listId, final int settlementId)
	{
		super(listId);
		this.settlementId = settlementId;
	}

	/** Every line in an economy shop is finite. */
	@Override
	public boolean countDecrease(final int itemId)
	{
		return true;
	}

	/**
	 * Take the goods out of the town's stockpile, not just out of a display list.<BR>
	 * <BR>
	 * Note this returns false when the settlement cannot supply, which is what stops a player being
	 * charged for goods that are gone. The stock check must therefore happen <i>before</i> adena is
	 * taken - which is the ordering fix applied to <code>RequestBuyItem</code>.
	 */
	@Override
	public synchronized boolean decreaseCount(final int itemId, final int count)
	{
		if (count <= 0)
		{
			return false;
		}

		final Settlement s = SettlementManager.getInstance().get(settlementId);

		if (s == null || !s.removeStock(itemId, count))
		{
			return false;
		}

		// Keep the display list in step, but never let it veto a sale the town could honour.
		super.decreaseCount(itemId, count);

		final long adena = (long) s.sellPrice(itemId) * count;
		s.addTreasury(adena);
		EconomyLog.txn("buy", settlementId, 0, itemId, count, adena);

		return true;
	}

	/**
	 * Rebuild the visible list from what the settlement actually holds.<BR>
	 * Called before the buy window opens, so a player never sees stock that is already gone.
	 */
	public void refresh()
	{
		final Settlement s = SettlementManager.getInstance().get(settlementId);

		if (s == null)
		{
			return;
		}

		getItems().clear();

		for (final NeedDef need : com.l2jfrozen.gameserver.economy.datatables.EconomyDataTable.getInstance().getNeeds())
		{
			for (final Integer boxed : need.getItemIds())
			{
				final int itemId = boxed.intValue();
				final int held = s.getStock(itemId);

				if (held <= 0)
				{
					continue;
				}

				final L2ItemInstance dummy = ItemTable.getInstance().createDummyItem(itemId);

				if (dummy == null)
				{
					continue;
				}

				dummy.setPriceToSell(s.sellPrice(itemId));
				dummy.setCountDecrease(true);
				dummy.setInitCount(held);
				dummy.setCount(held);
				addItem(dummy);
			}
		}
	}

	public int getSettlementId()
	{
		return settlementId;
	}
}
