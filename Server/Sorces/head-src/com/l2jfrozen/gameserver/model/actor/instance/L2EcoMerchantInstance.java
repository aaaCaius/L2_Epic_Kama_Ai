package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.controllers.TradeController;
import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.datatables.EconomyDataTable;
import com.l2jfrozen.gameserver.economy.managers.SettlementManager;
import com.l2jfrozen.gameserver.economy.model.NeedDef;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.economy.telemetry.EconomyLog;
import com.l2jfrozen.gameserver.economy.trade.EcoTradeList;
import com.l2jfrozen.gameserver.network.serverpackets.ActionFailed;
import com.l2jfrozen.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jfrozen.gameserver.templates.L2Item;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * A merchant whose shelves are the town's actual stockpile.<BR>
 * <BR>
 * Extends {@link L2MerchantInstance} rather than replacing it, which is the whole reason this works
 * without touching the buy path: <code>RequestBuyItem</code>'s merchant check accepts any subclass, and
 * the buy window it inherits resolves lists through {@link TradeController}. Stock genuinely runs out
 * because {@link EcoTradeList} debits the settlement.<BR>
 * <BR>
 * It also offers the other half of the loop - selling <i>to</i> the town at need-scaled prices - through
 * an HTML page rather than the stock sell window, because the stock window hardcodes half reference
 * price and cannot show why the quartermaster is suddenly paying four times the usual rate for iron.<BR>
 * <BR>
 * Resolved by the <code>type</code> column: <code>L2EcoMerchant</code>.
 */
public class L2EcoMerchantInstance extends L2MerchantInstance
{
	/** Reserved so a generated list can never collide with a shop id from the database. */
	private static final int LIST_ID_BASE = 900000;

	private static final String HTML_PATH = "data/html/economy/";

	private EcoTradeList list;

	public L2EcoMerchantInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}

	private Settlement settlement()
	{
		return SettlementManager.getInstance().getByLocation(this);
	}

	/** Build (once) and refresh the stock-backed buylist, then publish it for the inherited buy path. */
	private EcoTradeList ensureList(final Settlement s)
	{
		if (list == null)
		{
			list = new EcoTradeList(LIST_ID_BASE + getNpcId(), s.getId(), getNpcId());
			list.setNpcId(String.valueOf(getNpcId()));
			TradeController.getInstance().registerBuyList(list);
		}

		list.refresh();
		return list;
	}

	@Override
	public void showChatWindow(final L2PcInstance player, final int val)
	{
		if (!EconomyConfig.ENABLED)
		{
			super.showChatWindow(player, val);
			return;
		}

		showMain(player);
	}

	@Override
	public void onBypassFeedback(final L2PcInstance player, final String command)
	{
		if (player == null || command == null || !EconomyConfig.ENABLED)
		{
			super.onBypassFeedback(player, command);
			return;
		}

		if (command.startsWith("eco_shop"))
		{
			final Settlement s = settlement();

			if (s == null)
			{
				player.sendMessage("This town has no economy of its own.");
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}

			ensureList(s);
			// Hand back to the inherited merchant path, which resolves the list by npc id.
			super.onBypassFeedback(player, "Buy " + list.getListId());
			return;
		}

		if (command.startsWith("eco_sell"))
		{
			showSellToTown(player);
			return;
		}

		if (command.startsWith("eco_offer"))
		{
			offer(player, command);
			return;
		}

		if (command.startsWith("eco_main"))
		{
			showMain(player);
			return;
		}

		if (command.startsWith("eco_close"))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		super.onBypassFeedback(player, command);
	}

	private void showMain(final L2PcInstance player)
	{
		final Settlement s = settlement();

		if (s == null)
		{
			super.showChatWindow(player, 0);
			return;
		}

		final EconomyDataTable data = EconomyDataTable.getInstance();

		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "merchant.htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%town%", s.getName());
		html.replace("%tier%", data.getTierName(s.getTier()));
		html.replace("%pop%", String.valueOf(s.getPopulation()));
		html.replace("%sat%", String.valueOf(s.getSatisfaction()));
		html.replace("%needs%", shortagesOf(s));

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	/** The shortage list doubles as the price signal - what the town is short of, it pays well for. */
	private static String shortagesOf(final Settlement s)
	{
		final StringBuilder sb = new StringBuilder();

		for (final NeedDef need : EconomyDataTable.getInstance().getNeedsUpTo(s.getTier()))
		{
			final int pct = (int) Math.round(100 * (1 - s.scarcityOf(need)));
			final String colour = pct < 50 ? "FF6060" : pct < 85 ? "LEVEL" : "B0B0B0";

			sb.append("<tr><td width=110>").append(need.getId());
			sb.append("</td><td align=right width=60><font color=\"").append(colour).append("\">");
			sb.append(pct).append("%</font></td></tr>");
		}

		return sb.toString();
	}

	private void showSellToTown(final L2PcInstance player)
	{
		final Settlement s = settlement();

		if (s == null)
		{
			return;
		}

		final StringBuilder rows = new StringBuilder();

		for (final NeedDef need : EconomyDataTable.getInstance().getNeedsUpTo(s.getTier()))
		{
			for (final Integer boxed : need.getItemIds())
			{
				final int itemId = boxed.intValue();
				final int have = player.getInventory().getInventoryItemCount(itemId, -1);

				if (have <= 0 || !s.willBuy(itemId, 1))
				{
					continue;
				}

				final L2Item tpl = ItemTable.getInstance().getTemplate(itemId);
				final String label = tpl == null ? String.valueOf(itemId) : tpl.getName();

				rows.append("<tr><td width=130>").append(label);
				rows.append("</td><td align=right width=45><font color=\"LEVEL\">").append(s.buyPrice(itemId));
				rows.append("</font></td><td align=right width=40>").append(have);
				rows.append("</td><td width=55><button value=\"Sell\" action=\"bypass -h npc_%objectId%_eco_offer ");
				rows.append(itemId).append("\" width=55 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td></tr>");
			}
		}

		if (rows.length() == 0)
		{
			rows.append("<tr><td width=270>The town needs nothing you are carrying.</td></tr>");
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "sell.htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%town%", s.getName());
		html.replace("%rows%", rows.toString());

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	/** Sell everything of one item id that the town is still willing to take. */
	private void offer(final L2PcInstance player, final String command)
	{
		final Settlement s = settlement();

		if (s == null)
		{
			return;
		}

		int itemId;

		try
		{
			itemId = Integer.parseInt(command.substring(command.lastIndexOf(' ') + 1).trim());
		}
		catch (final Exception e)
		{
			return;
		}

		final int have = player.getInventory().getInventoryItemCount(itemId, -1);

		if (have <= 0)
		{
			showSellToTown(player);
			return;
		}

		// Sell down to whatever the treasury and the growth target will actually absorb.
		int qty = have;

		while (qty > 0 && !s.willBuy(itemId, qty))
		{
			qty /= 2;
		}

		if (qty <= 0)
		{
			player.sendMessage("The town cannot take any more of that.");
			showSellToTown(player);
			return;
		}

		if (!player.destroyItemByItemId("EcoSell", itemId, qty, this, true))
		{
			return;
		}

		final long paid = (long) s.buyPrice(itemId) * qty;
		s.addStock(itemId, qty);
		s.addTreasury(-paid);
		player.addAdena("EcoSell", (int) Math.min(Integer.MAX_VALUE, paid), this, true);

		EconomyLog.txn("sell", s.getId(), player.getObjectId(), itemId, qty, paid);

		if (EconomyConfig.SAVE_ALL_ACTIONS)
		{
			SettlementManager.getInstance().store(false);
		}

		player.sendMessage("Sold " + qty + " for " + paid + " adena.");
		showSellToTown(player);
	}
}
