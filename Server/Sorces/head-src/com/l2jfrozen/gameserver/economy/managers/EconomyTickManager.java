package com.l2jfrozen.gameserver.economy.managers;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.economy.telemetry.EconomyLog;
import com.l2jfrozen.gameserver.model.L2World;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.thread.ThreadPoolManager;

/**
 * The clock.<BR>
 * <BR>
 * Ticks every minute and runs a full economic cycle every <code>CycleMinutes</code>, mirroring
 * {@link com.l2jfrozen.gameserver.managers.CastleManorManager}'s proven shape. The frequent tick exists
 * so cheap work can happen often and expensive work rarely.
 */
public class EconomyTickManager
{
	private static final Logger LOGGER = Logger.getLogger(EconomyTickManager.class);

	private static final long TICK_MS = 60000L;

	private static EconomyTickManager instance;

	private int minutesSinceCycle;
	private int cyclesRun;

	public static EconomyTickManager getInstance()
	{
		if (instance == null)
		{
			instance = new EconomyTickManager();
		}

		return instance;
	}

	private EconomyTickManager()
	{
		ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new EconomyTask(), TICK_MS, TICK_MS);
		LOGGER.info("EconomyTickManager: started, one cycle every " + EconomyConfig.CYCLE_MINUTES + " minute(s).");
	}

	private class EconomyTask implements Runnable
	{
		@Override
		public void run()
		{
			if (!EconomyConfig.ENABLED)
			{
				return;
			}

			minutesSinceCycle++;

			if (minutesSinceCycle < EconomyConfig.CYCLE_MINUTES)
			{
				return;
			}

			minutesSinceCycle = 0;
			runCycle();
		}
	}

	/** Run one cycle across every settlement. Also reachable from <code>//eco cycle</code>. */
	public void runCycle()
	{
		final int online = countOnline();
		cyclesRun++;

		for (final Settlement s : SettlementManager.getInstance().getAll())
		{
			final int before = s.getTier();

			try
			{
				final double overall = s.runCycle(online);

				if (s.getTier() != before)
				{
					LOGGER.info("Economy: " + s.getName() + " moved from tier " + before + " to tier " + s.getTier()
						+ " (fulfilment " + String.format("%.2f", Double.valueOf(overall)) + ").");
				}

				if (EconomyConfig.SNAPSHOT_EVERY_CYCLES > 0 && cyclesRun % EconomyConfig.SNAPSHOT_EVERY_CYCLES == 0)
				{
					EconomyLog.snapshot(s);
				}
			}
			catch (final Exception e)
			{
				LOGGER.error("Economy: cycle failed for settlement " + s.getId(), e);
			}
		}

		SettlementManager.getInstance().store(false);
	}

	/**
	 * @return players currently online. Activity scales both consumption and production, so a quiet
	 *         server slows down rather than drifting out of balance.
	 */
	private static int countOnline()
	{
		try
		{
			int n = 0;

			for (final L2PcInstance p : L2World.getInstance().getAllPlayers())
			{
				if (p != null && p.isOnline())
				{
					n++;
				}
			}

			return n;
		}
		catch (final Exception e)
		{
			return 0;
		}
	}

	public int getCyclesRun()
	{
		return cyclesRun;
	}

	public int getMinutesToNextCycle()
	{
		return Math.max(0, EconomyConfig.CYCLE_MINUTES - minutesSinceCycle);
	}
}
