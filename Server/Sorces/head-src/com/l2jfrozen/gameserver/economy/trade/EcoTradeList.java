package com.l2jfrozen.gameserver.economy.trade;

import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.economy.datatables.EconomyDataTable;
import com.l2jfrozen.gameserver.economy.managers.SettlementManager;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.economy.model.ShopDef;
import com.l2jfrozen.gameserver.economy.telemetry.EconomyLog;
import com.l2jfrozen.gameserver.model.L2TradeList;
import com.l2jfrozen.gameserver.model.actor.instance.L2ItemInstance;
import com.l2jfrozen.gameserver.templates.L2Item;

/**
 * A merchant buylist whose quantities are the settlement's actual stockpile.<BR>
 * <BR>
 * This is the finite-stock mechanic, and it needs <b>no change at all</b> to the purchase path.
 * {@link L2TradeList#decreaseCount} is public and called polymorphically from
 * <code>RequestBuyItem</code>, and the merchant type check there accepts any subclass of
 * <code>L2MerchantInstance</code> - so overriding one method is enough to make a shop genuinely run out.
 * The Interlude client already renders remaining counts and hides sold-out lines, so there is no client
 * work either.<BR>
 * <BR>
 * The catalogue comes from <code>shops.xml</code>; the quantities come from the town. A shop is empty
 * because the town has not produced any, not because a timer has not elapsed.
 */
public class EcoTradeList extends L2TradeList
{
	/** What an essential line shows when the town is relying on emergency imports. */
	private static final int ESSENTIAL_SHOWN = 999;

	private final int settlementId;
	private final int npcId;

	public EcoTradeList(final int listId, final int settlementId, final int npcId)
	{
		super(listId);
		this.settlementId = settlementId;
		this.npcId = npcId;
	}

	/** Every line in an economy shop is finite - essentials included, they simply never hit zero. */
	@Override
	public boolean countDecrease(final int itemId)
	{
		return true;
	}

	/**
	 * Take the goods out of the town's stockpile, not just out of a display list.<BR>
	 * <BR>
	 * Returns false when the town genuinely cannot supply, which is what stops a player being charged
	 * for goods that are gone - paired with the ordering fix in <code>RequestBuyItem</code>, where stock
	 * is now reserved before any adena is taken.
	 */
	@Override
	public synchronized boolean decreaseCount(final int itemId, final int count)
	{
		if (count <= 0)
		{
			return false;
		}

		final Settlement s = SettlementManager.getInstance().get(settlementId);

		if (s == null)
		{
			return false;
		}

		final ShopDef shop = EconomyDataTable.getInstance().getShop(npcId);
		final boolean essential = shop != null && shop.isEssential(itemId);
		final int held = s.getStock(itemId);

		if (held >= count)
		{
			s.removeStock(itemId, count);
		}
		else if (essential)
		{
			// A player must never be blocked from buying shots or basic potions. The town covers the
			// shortfall by importing at cost - so the scarcity is real and expensive, but it lands on
			// the settlement's treasury rather than stranding the player.
			final int shortfall = count - held;

			if (held > 0)
			{
				s.removeStock(itemId, held);
			}

			s.addTreasury(-(long) shortfall * referencePrice(itemId));
			EconomyLog.txn("import", settlementId, 0, itemId, shortfall, (long) shortfall * referencePrice(itemId));
		}
		else
		{
			return false;
		}

		super.decreaseCount(itemId, count);

		final long adena = (long) s.sellPrice(itemId) * count;
		s.addTreasury(adena);
		EconomyLog.txn("buy", settlementId, 0, itemId, count, adena);

		return true;
	}

	/**
	 * Rebuild the visible list from the catalogue and what the town actually holds.<BR>
	 * Called before the buy window opens, so a player never sees stock that is already gone.
	 */
	public void refresh()
	{
		final Settlement s = SettlementManager.getInstance().get(settlementId);
		final ShopDef shop = EconomyDataTable.getInstance().getShop(npcId);

		if (s == null || shop == null)
		{
			return;
		}

		getItems().clear();

		for (final Integer boxed : shop.getItemIds())
		{
			final int itemId = boxed.intValue();
			final int held = s.getStock(itemId);
			final boolean essential = shop.isEssential(itemId);

			// Sold out and not essential: the line simply is not there. That is the whole point.
			if (held <= 0 && !essential)
			{
				continue;
			}

			final L2ItemInstance dummy = ItemTable.getInstance().createDummyItem(itemId);

			if (dummy == null)
			{
				continue;
			}

			final int shown = held > 0 ? held : ESSENTIAL_SHOWN;

			dummy.setPriceToSell(s.sellPrice(itemId));
			dummy.setCountDecrease(true);
			dummy.setInitCount(shown);
			dummy.setCount(shown);
			addItem(dummy);
		}
	}

	private static int referencePrice(final int itemId)
	{
		final L2Item item = ItemTable.getInstance().getTemplate(itemId);
		return item == null ? 1 : Math.max(1, item.getReferencePrice());
	}

	public int getSettlementId()
	{
		return settlementId;
	}
}
