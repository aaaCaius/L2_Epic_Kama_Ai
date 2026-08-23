package com.l2jfrozen.gameserver.economy.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.datatables.csv.MapRegionTable;
import com.l2jfrozen.gameserver.economy.EconomyConfig;
import com.l2jfrozen.gameserver.economy.model.Settlement;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.util.CloseUtil;
import com.l2jfrozen.util.database.DatabaseUtils;
import com.l2jfrozen.util.database.L2DatabaseFactory;

/**
 * Loads settlements at boot, keeps them in memory, and writes them back.<BR>
 * <BR>
 * Follows {@link com.l2jfrozen.gameserver.managers.GrandBossManager}: read everything once at startup
 * because touching the database on the hot path is far slower than holding a few rows in RAM.<BR>
 * <BR>
 * Settlement identity comes from {@link MapRegionTable#getAreaCastle} and nowhere else. Three
 * overlapping town-to-castle maps exist in this codebase and they disagree with each other; mixing them
 * would put a player's purchase in one settlement and their sale in another.
 */
public class SettlementManager
{
	private static final Logger LOGGER = Logger.getLogger(SettlementManager.class);

	private static final String LOAD = "SELECT settlement_id,name,tier,population,treasury,satisfaction,tier_floor FROM eco_settlement";
	private static final String LOAD_STOCK = "SELECT item_id,qty FROM eco_stock WHERE settlement_id=?";
	private static final String SAVE = "UPDATE eco_settlement SET tier=?,population=?,treasury=?,satisfaction=?,last_cycle=? WHERE settlement_id=?";
	private static final String SAVE_STOCK = "INSERT INTO eco_stock (settlement_id,item_id,qty) VALUES (?,?,?) ON DUPLICATE KEY UPDATE qty=VALUES(qty)";

	private static SettlementManager instance;

	private final Map<Integer, Settlement> settlements = new HashMap<>();

	public static SettlementManager getInstance()
	{
		if (instance == null)
		{
			instance = new SettlementManager();
		}

		return instance;
	}

	private SettlementManager()
	{
		load();
	}

	private void load()
	{
		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			st = con.prepareStatement(LOAD);
			rs = st.executeQuery();

			while (rs.next())
			{
				final Settlement s = new Settlement(rs.getInt("settlement_id"), rs.getString("name"),
					rs.getInt("tier"), rs.getInt("population"), rs.getLong("treasury"), rs.getInt("tier_floor"));
				s.setSatisfaction(rs.getInt("satisfaction"));
				settlements.put(Integer.valueOf(s.getId()), s);
			}

			DatabaseUtils.close(rs);
			DatabaseUtils.close(st);

			for (final Settlement s : settlements.values())
			{
				st = con.prepareStatement(LOAD_STOCK);
				st.setInt(1, s.getId());
				rs = st.executeQuery();

				while (rs.next())
				{
					s.addStock(rs.getInt("item_id"), rs.getInt("qty"));
				}

				DatabaseUtils.close(rs);
				DatabaseUtils.close(st);
				s.clean();
			}

			LOGGER.info("SettlementManager: loaded " + settlements.size() + " settlement(s).");
		}
		catch (final Exception e)
		{
			LOGGER.error("SettlementManager: failed to load settlements - is eco_schema.sql applied?", e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}

	/** Persist everything that has changed. Called on the cycle and at shutdown. */
	public void store(final boolean force)
	{
		Connection con = null;
		PreparedStatement st = null;

		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();

			for (final Settlement s : settlements.values())
			{
				if (!s.isDirty() && !force)
				{
					continue;
				}

				st = con.prepareStatement(SAVE);
				st.setInt(1, s.getTier());
				st.setInt(2, s.getPopulation());
				st.setLong(3, s.getTreasury());
				st.setInt(4, s.getSatisfaction());
				st.setLong(5, s.getLastCycle());
				st.setInt(6, s.getId());
				st.execute();
				DatabaseUtils.close(st);

				for (final Map.Entry<Integer, Integer> e : s.getAllStock().entrySet())
				{
					st = con.prepareStatement(SAVE_STOCK);
					st.setInt(1, s.getId());
					st.setInt(2, e.getKey().intValue());
					st.setInt(3, e.getValue().intValue());
					st.execute();
					DatabaseUtils.close(st);
				}

				s.clean();
			}
		}
		catch (final Exception e)
		{
			LOGGER.error("SettlementManager: failed to store settlements", e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}

	public Settlement get(final int settlementId)
	{
		return settlements.get(Integer.valueOf(settlementId));
	}

	/**
	 * @param  character anyone standing in the world
	 * @return           the settlement whose economy governs where they are, or null
	 */
	public Settlement getByLocation(final L2Character character)
	{
		if (character == null)
		{
			return null;
		}

		try
		{
			return get(MapRegionTable.getInstance().getAreaCastle(character));
		}
		catch (final Exception e)
		{
			return null;
		}
	}

	public Collection<Settlement> getAll()
	{
		return settlements.values();
	}

	public List<Settlement> getAllSorted()
	{
		final List<Settlement> out = new ArrayList<>(settlements.values());
		out.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
		return out;
	}

	public boolean isReady()
	{
		return EconomyConfig.ENABLED && !settlements.isEmpty();
	}
}
