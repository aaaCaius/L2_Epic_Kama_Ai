package com.l2jfrozen.gameserver.economy.telemetry;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.util.CloseUtil;
import com.l2jfrozen.util.database.DatabaseUtils;
import com.l2jfrozen.util.database.L2DatabaseFactory;

/**
 * Transaction and indicator telemetry.<BR>
 * <BR>
 * This exists because every balance constant in the design has to be found by watching real play rather
 * than reasoning about it. Without a record of what actually happened, "tune it live" has nothing to
 * tune against.<BR>
 * <BR>
 * The single most valuable question it answers is what one player genuinely produces in an hour at this
 * server's rates - the number every other number depends on.
 */
public class EconomyLog
{
	private static final Logger LOGGER = Logger.getLogger(EconomyLog.class);

	private static final String TXN = "INSERT INTO eco_txn (ts,kind,settlement_id,char_id,item_id,qty,adena) VALUES (?,?,?,?,?,?,?)";
	private static final String IND = "INSERT INTO eco_indicator (ts,settlement_id,tier,population,treasury,satisfaction,fulfilment) VALUES (?,?,?,?,?,?,?)";

	/**
	 * Record one economic transaction.
	 * @param kind         buy, sell, contract, export, site_tax
	 * @param settlementId which settlement
	 * @param charId       the player, or 0 for the world acting on its own
	 * @param itemId       what moved
	 * @param qty          how much
	 * @param adena        what changed hands
	 */
	public static void txn(final String kind, final int settlementId, final int charId, final int itemId, final int qty, final long adena)
	{
		if (!EconomyConfig.LOG_TRANSACTIONS)
		{
			return;
		}

		Connection con = null;
		PreparedStatement st = null;

		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			st = con.prepareStatement(TXN);
			st.setLong(1, System.currentTimeMillis());
			st.setString(2, kind);
			st.setInt(3, settlementId);
			st.setInt(4, charId);
			st.setInt(5, itemId);
			st.setInt(6, qty);
			st.setLong(7, adena);
			st.execute();
			DatabaseUtils.close(st);
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyLog: failed to record a " + kind + " transaction", e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}

	/** Snapshot a settlement's vitals, so trends are visible rather than just the current instant. */
	public static void snapshot(final Settlement s)
	{
		Connection con = null;
		PreparedStatement st = null;

		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			st = con.prepareStatement(IND);
			st.setLong(1, System.currentTimeMillis());
			st.setInt(2, s.getId());
			st.setInt(3, s.getTier());
			st.setInt(4, s.getPopulation());
			st.setLong(5, s.getTreasury());
			st.setInt(6, s.getSatisfaction());
			st.setInt(7, (int) Math.round(s.getLastOverall() * 100));
			st.execute();
			DatabaseUtils.close(st);
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyLog: failed to snapshot settlement " + s.getId(), e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}
}
