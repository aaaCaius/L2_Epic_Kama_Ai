package com.l2jfrozen.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.datatables.EconomyDataTable;
import com.l2jfrozen.gameserver.economy.managers.EconomyTickManager;
import com.l2jfrozen.gameserver.economy.managers.SettlementManager;
import com.l2jfrozen.gameserver.economy.model.NeedDef;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.economy.telemetry.EconomyLog;
import com.l2jfrozen.gameserver.handler.IAdminCommandHandler;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;

/**
 * Inspect and steer the economy from in game.<BR>
 * <BR>
 * This is not a convenience. Every balance constant in the design has to be found by watching real
 * play, and states worth testing - a starving town, a maxed city, a blockaded port - would otherwise
 * take days of play to reach. Being able to force them turns a day into a minute.<BR>
 * <BR>
 * <code>//eco</code> · <code>//eco &lt;id&gt;</code> · <code>//eco set &lt;id&gt; &lt;field&gt;
 * &lt;value&gt;</code> · <code>//eco stock &lt;id&gt; &lt;item&gt; &lt;qty&gt;</code> ·
 * <code>//eco cycle</code> · <code>//eco snapshot</code>
 */
public class AdminEconomy implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_eco"
	};

	@Override
	public boolean useAdminCommand(final String command, final L2PcInstance activeChar)
	{
		if (activeChar == null)
		{
			return false;
		}

		final StringTokenizer st = new StringTokenizer(command);
		st.nextToken();

		if (!st.hasMoreTokens())
		{
			summary(activeChar);
			return true;
		}

		final String arg = st.nextToken();

		if ("cycle".equals(arg))
		{
			EconomyTickManager.getInstance().runCycle();
			activeChar.sendMessage("Economy: one cycle run.");
			summary(activeChar);
			return true;
		}

		if ("snapshot".equals(arg))
		{
			for (final Settlement s : SettlementManager.getInstance().getAll())
			{
				EconomyLog.snapshot(s);
			}

			activeChar.sendMessage("Economy: indicators snapshotted.");
			return true;
		}

		if ("set".equals(arg))
		{
			return set(activeChar, st);
		}

		if ("stock".equals(arg))
		{
			return stock(activeChar, st);
		}

		try
		{
			detail(activeChar, SettlementManager.getInstance().get(Integer.parseInt(arg)));
		}
		catch (final NumberFormatException e)
		{
			activeChar.sendMessage("Usage: //eco [id | set | stock | cycle | snapshot]");
		}

		return true;
	}

	private static void summary(final L2PcInstance activeChar)
	{
		activeChar.sendMessage("=== Economy " + (EconomyConfig.ENABLED ? "ENABLED" : "DISABLED")
			+ " - next cycle in " + EconomyTickManager.getInstance().getMinutesToNextCycle() + " min ===");

		for (final Settlement s : SettlementManager.getInstance().getAllSorted())
		{
			activeChar.sendMessage(s.getId() + " " + s.getName()
				+ " | tier " + s.getTier() + " " + EconomyDataTable.getInstance().getTierName(s.getTier())
				+ " | pop " + s.getPopulation()
				+ " | treasury " + s.getTreasury()
				+ " | sat " + s.getSatisfaction()
				+ " | fulfil " + String.format("%.2f", Double.valueOf(s.getLastOverall())));
		}
	}

	private static void detail(final L2PcInstance activeChar, final Settlement s)
	{
		if (s == null)
		{
			activeChar.sendMessage("No such settlement.");
			return;
		}

		activeChar.sendMessage("=== " + s.getName() + " (" + s.getId() + ") ===");
		activeChar.sendMessage("tier " + s.getTier() + " " + EconomyDataTable.getInstance().getTierName(s.getTier())
			+ "  floor " + s.getTierFloor() + "  up-streak " + s.getUpStreak() + "  down-streak " + s.getDownStreak());
		activeChar.sendMessage("pop " + s.getPopulation() + "  treasury " + s.getTreasury()
			+ "  satisfaction " + s.getSatisfaction() + "  output x" + String.format("%.2f", Double.valueOf(s.outputMultiplier())));

		for (final NeedDef n : EconomyDataTable.getInstance().getNeedsUpTo(s.getTier()))
		{
			activeChar.sendMessage("  " + n.getId() + ": held " + s.stockFor(n)
				+ "  met " + (int) Math.round(100 * (1 - s.scarcityOf(n))) + "%");
		}
	}

	private static boolean set(final L2PcInstance activeChar, final StringTokenizer st)
	{
		try
		{
			final Settlement s = SettlementManager.getInstance().get(Integer.parseInt(st.nextToken()));
			final String field = st.nextToken();
			final long value = Long.parseLong(st.nextToken());

			if (s == null)
			{
				activeChar.sendMessage("No such settlement.");
				return true;
			}

			if ("tier".equals(field))
			{
				s.setTier((int) value);
			}
			else if ("population".equals(field) || "pop".equals(field))
			{
				s.setPopulation((int) value);
			}
			else if ("treasury".equals(field))
			{
				s.setTreasury(value);
			}
			else if ("satisfaction".equals(field) || "sat".equals(field))
			{
				s.setSatisfaction((int) value);
			}
			else
			{
				activeChar.sendMessage("Fields: tier, population, treasury, satisfaction");
				return true;
			}

			SettlementManager.getInstance().store(true);
			activeChar.sendMessage("Set " + field + " = " + value + " on " + s.getName() + ".");
			detail(activeChar, s);
		}
		catch (final Exception e)
		{
			activeChar.sendMessage("Usage: //eco set <id> <tier|population|treasury|satisfaction> <value>");
		}

		return true;
	}

	private static boolean stock(final L2PcInstance activeChar, final StringTokenizer st)
	{
		try
		{
			final Settlement s = SettlementManager.getInstance().get(Integer.parseInt(st.nextToken()));
			final int itemId = Integer.parseInt(st.nextToken());
			final int qty = Integer.parseInt(st.nextToken());

			if (s == null)
			{
				activeChar.sendMessage("No such settlement.");
				return true;
			}

			final int have = s.getStock(itemId);

			if (qty >= have)
			{
				s.addStock(itemId, qty - have);
			}
			else
			{
				s.removeStock(itemId, have - qty);
			}

			SettlementManager.getInstance().store(true);
			activeChar.sendMessage("Stock of " + itemId + " in " + s.getName() + " is now " + s.getStock(itemId) + ".");
		}
		catch (final Exception e)
		{
			activeChar.sendMessage("Usage: //eco stock <id> <itemId> <qty>");
		}

		return true;
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
