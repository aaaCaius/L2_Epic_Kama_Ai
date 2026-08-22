package com.l2jfrozen.gameserver.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.datatables.csv.NpcWalkerRoutesTable;
import com.l2jfrozen.gameserver.datatables.sql.NpcTable;
import com.l2jfrozen.gameserver.model.L2NpcWalkerNode;
import com.l2jfrozen.gameserver.model.actor.instance.L2EscortGardInstance;
import com.l2jfrozen.gameserver.model.actor.instance.L2NpcCaravanInstance;
import com.l2jfrozen.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jfrozen.gameserver.model.spawn.L2Spawn;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * Spawns and tracks caravan convoys on demand.<BR>
 * <BR>
 * Caravans are no longer permanent world spawns. The Caravan Manager NPC asks this class for a
 * convoy, which is one {@link L2NpcCaravanInstance} plus its {@link L2EscortGardInstance} guards,
 * placed at the first waypoint of the caravan's route. The convoy walks the route once and is
 * removed as a unit when the caravan reaches the final waypoint - see
 * {@link #despawnConvoy(L2NpcCaravanInstance)}.<BR>
 * <BR>
 * Convoys are deliberately NOT registered with
 * {@link com.l2jfrozen.gameserver.datatables.sql.SpawnTable}: they are transient, must not persist to
 * the database, and must never respawn on their own.
 */
public class CaravanSpawnManager
{
	private static final Logger LOGGER = Logger.getLogger(CaravanSpawnManager.class);

	/** Most convoys allowed on the road at once. */
	public static final int MAX_CONVOYS = 5;

	private static final int CARAVAN_NPC_ID = 100203;
	private static final int ESCORT_NPC_ID = 100204;

	/** Guards spawned alongside each caravan. */
	private static final int ESCORTS_PER_CONVOY = 3;

	/** How far from the caravan the guards appear. */
	private static final int ESCORT_SPAWN_OFFSET = 80;

	private static CaravanSpawnManager instance;

	private final List<Convoy> convoys = Collections.synchronizedList(new ArrayList<Convoy>());

	/** A caravan and the guards that travel with it. */
	private static class Convoy
	{
		private final L2NpcCaravanInstance caravan;
		private final List<L2NpcInstance> escorts = new ArrayList<>();

		Convoy(final L2NpcCaravanInstance caravan)
		{
			this.caravan = caravan;
		}
	}

	public static CaravanSpawnManager getInstance()
	{
		if (instance == null)
		{
			instance = new CaravanSpawnManager();
		}

		return instance;
	}

	/** Outcome of a spawn request, so the caller can explain itself to the player. */
	public enum SpawnResult
	{
		OK,
		AT_CAPACITY,
		NO_ROUTE,
		FAILED
	}

	/**
	 * Put a new convoy on the road at the start of the caravan's route.
	 * @return why the request succeeded or failed
	 */
	public SpawnResult spawnConvoy()
	{
		purgeDead();

		if (getActiveCount() >= MAX_CONVOYS)
		{
			return SpawnResult.AT_CAPACITY;
		}

		final List<L2NpcWalkerNode> route = NpcWalkerRoutesTable.getInstance().getRouteForNpc(CARAVAN_NPC_ID);

		if (route == null || route.isEmpty())
		{
			LOGGER.warn("CaravanSpawnManager: npc " + CARAVAN_NPC_ID + " has no walker_routes.csv waypoints, cannot spawn.");
			return SpawnResult.NO_ROUTE;
		}

		final L2NpcWalkerNode start = route.get(0);

		try
		{
			final L2NpcInstance npc = spawnOne(CARAVAN_NPC_ID, start.getMoveX(), start.getMoveY(), start.getMoveZ());

			if (!(npc instanceof L2NpcCaravanInstance))
			{
				LOGGER.warn("CaravanSpawnManager: npc " + CARAVAN_NPC_ID + " did not spawn as a caravan, check its type column.");
				return SpawnResult.FAILED;
			}

			final Convoy convoy = new Convoy((L2NpcCaravanInstance) npc);

			// Guards ring the caravan so they do not all appear on the same tile.
			for (int i = 0; i < ESCORTS_PER_CONVOY; i++)
			{
				final double angle = 2 * Math.PI * i / ESCORTS_PER_CONVOY;
				final int x = start.getMoveX() + (int) (Math.cos(angle) * ESCORT_SPAWN_OFFSET);
				final int y = start.getMoveY() + (int) (Math.sin(angle) * ESCORT_SPAWN_OFFSET);

				final L2NpcInstance escort = spawnOne(ESCORT_NPC_ID, x, y, start.getMoveZ());

				if (escort != null)
				{
					convoy.escorts.add(escort);
				}
			}

			convoys.add(convoy);

			LOGGER.info("CaravanSpawnManager: convoy spawned with " + convoy.escorts.size() + " escort(s). Active: " + getActiveCount() + "/" + MAX_CONVOYS + ".");

			return SpawnResult.OK;
		}
		catch (final Exception e)
		{
			LOGGER.error("CaravanSpawnManager: failed to spawn a convoy", e);
			return SpawnResult.FAILED;
		}
	}

	/**
	 * Remove the convoy this caravan belongs to - the caravan and every guard travelling with it.
	 * Called when the caravan finishes its route.
	 * @param caravan the caravan that reached the end of its journey
	 */
	public void despawnConvoy(final L2NpcCaravanInstance caravan)
	{
		if (caravan == null)
		{
			return;
		}

		Convoy found = null;

		synchronized (convoys)
		{
			for (final Convoy convoy : convoys)
			{
				if (convoy.caravan == caravan)
				{
					found = convoy;
					break;
				}
			}

			if (found != null)
			{
				convoys.remove(found);
			}
		}

		if (found == null)
		{
			// Not one of ours - a caravan placed by hand or left over from a spawn table row.
			remove(caravan);
			return;
		}

		for (final L2NpcInstance escort : found.escorts)
		{
			remove(escort);
		}

		remove(found.caravan);

		LOGGER.info("CaravanSpawnManager: convoy despawned at the end of its route. Active: " + getActiveCount() + "/" + MAX_CONVOYS + ".");
	}

	/** @return how many convoys are currently on the road */
	public int getActiveCount()
	{
		purgeDead();

		return convoys.size();
	}

	/**
	 * Create and spawn a single NPC, without registering it in the spawn table so it neither persists
	 * nor respawns.
	 * @param  npcId     which NPC to spawn
	 * @param  x         where
	 * @param  y         where
	 * @param  z         where
	 * @return           the spawned NPC, or null if the template is unknown
	 * @throws Exception if the spawn itself fails
	 */
	private L2NpcInstance spawnOne(final int npcId, final int x, final int y, final int z) throws Exception
	{
		final L2NpcTemplate template = NpcTable.getInstance().getTemplate(npcId);

		if (template == null)
		{
			LOGGER.warn("CaravanSpawnManager: no template for npc " + npcId + ".");
			return null;
		}

		final L2Spawn spawn = new L2Spawn(template);
		spawn.setLocx(x);
		spawn.setLocy(y);
		spawn.setLocz(z);
		spawn.setAmount(1);
		spawn.setHeading(0);
		spawn.setRespawnDelay(0);
		spawn.stopRespawn();

		final L2NpcInstance npc = spawn.doSpawn();

		if (npc != null)
		{
			npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());
		}

		return npc;
	}

	/** Take an NPC out of the world and stop its spawn from bringing it back. */
	private void remove(final L2NpcInstance npc)
	{
		if (npc == null)
		{
			return;
		}

		try
		{
			if (npc.getSpawn() != null)
			{
				npc.getSpawn().stopRespawn();
			}

			npc.deleteMe();
		}
		catch (final Exception e)
		{
			LOGGER.error("CaravanSpawnManager: failed to remove npc " + npc.getNpcId(), e);
		}
	}

	/** Drop convoys whose caravan is already gone, so the cap reflects reality. */
	private void purgeDead()
	{
		synchronized (convoys)
		{
			final List<Convoy> stale = new ArrayList<>();

			for (final Convoy convoy : convoys)
			{
				if (convoy.caravan == null || convoy.caravan.isDead() || !convoy.caravan.isVisible())
				{
					stale.add(convoy);
				}
			}

			for (final Convoy convoy : stale)
			{
				// The caravan is gone but its guards may still be standing about.
				for (final L2NpcInstance escort : convoy.escorts)
				{
					remove(escort);
				}

				convoys.remove(convoy);
			}
		}
	}
}
