package com.l2jfrozen.gameserver.model.actor.instance;

import java.util.List;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.ai.CtrlIntention;
import com.l2jfrozen.gameserver.ai.L2CaravanAI;
import com.l2jfrozen.gameserver.ai.L2CharacterAI;
import com.l2jfrozen.gameserver.datatables.CaravanCargoTable;
import com.l2jfrozen.gameserver.datatables.CaravanCargoTable.CargoItem;
import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.model.L2Attackable;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.network.serverpackets.CreatureSay;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;
import com.l2jfrozen.gameserver.util.Broadcast;
import com.l2jfrozen.util.random.Rnd;

/**
 * A travelling trader.<BR>
 * <BR>
 * The caravan walks a route defined in <code>walker_routes.csv</code> (see {@link L2CaravanAI}) and
 * carries a cargo manifest defined in <code>config/functions/caravan.properties</code>. It is a
 * normal {@link L2Attackable}, so players can engage and kill it - and when it dies it spills its
 * <b>entire</b> cargo on the ground.<BR>
 * <BR>
 * The cargo drop deliberately bypasses the droplist pipeline: no chance roll, no rate multipliers,
 * no deep-blue penalty. What the manifest says is what drops, so the caravan is a predictable prize
 * rather than a lottery.
 */
public class L2NpcCaravanInstance extends L2Attackable
{
	/** Guards against a double death event dropping the cargo twice. */
	private volatile boolean cargoSpilled;
	
	/**
	 * Route progress lives on the NPC, not the AI.<BR>
	 * <BR>
	 * {@link com.l2jfrozen.gameserver.ai.L2AttackableAI#changeIntention} detaches the AI from an idle
	 * attackable whose knownlist is empty, and {@link #getAI()} then builds a fresh one. Holding the
	 * waypoint index here means the caravan resumes its journey instead of restarting it every time
	 * the road goes quiet.
	 */
	private int routePos = -1;
	
	private boolean walkingToNextPoint;
	
	public L2NpcCaravanInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	/**
	 * Start the AI on spawn, and clear per-life state.<BR>
	 * <BR>
	 * Nothing in {@link com.l2jfrozen.gameserver.model.spawn.L2Spawn} starts an NPC's AI task, and
	 * L2AttackableAI only schedules one from changeIntention. Without asking for an intention here the
	 * caravan's think cycle never runs and it stands still forever.<BR>
	 * <BR>
	 * L2Spawn reuses the same object on respawn, so cargoSpilled and the route position are reset -
	 * otherwise a respawned caravan would carry nothing and resume mid-journey.
	 */
	@Override
	public void onSpawn()
	{
		super.onSpawn();

		cargoSpilled = false;
		routePos = -1;
		walkingToNextPoint = false;

		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
	}

	@Override
	public L2CharacterAI getAI()
	{
		if (aiCharacter == null)
		{
			synchronized (this)
			{
				if (aiCharacter == null)
				{
					aiCharacter = new L2CaravanAI(new AIAccessor());
				}
			}
		}
		
		return aiCharacter;
	}
	
	@Override
	public boolean doDie(final L2Character killer)
	{
		if (!super.doDie(killer))
		{
			return false;
		}
		
		spillCargo(killer);
		
		return true;
	}
	
	/**
	 * Drop the whole manifest on the ground where the caravan fell.<BR>
	 * <BR>
	 * Runs after {@link L2Attackable#doDie} so the normal droplist and reward pass has already
	 * happened - the cargo is additional to anything the template drops.
	 * @param killer whoever landed the killing blow; may be null
	 */
	private void spillCargo(final L2Character killer)
	{
		if (cargoSpilled)
		{
			return;
		}
		
		final CaravanCargoTable cargoTable = CaravanCargoTable.getInstance();
		
		if (!cargoTable.isEnabled())
		{
			return;
		}
		
		final List<CargoItem> manifest = cargoTable.getCargo(getNpcId());
		
		if (manifest.isEmpty())
		{
			return;
		}
		
		cargoSpilled = true;
		
		final L2PcInstance looter = killer instanceof L2PcInstance ? (L2PcInstance) killer : null;
		final int radius = cargoTable.getDropRadius();
		
		int dropped = 0;
		
		for (final CargoItem cargo : manifest)
		{
			try
			{
				final int x = getX() + Rnd.get(radius * 2 + 1) - radius;
				final int y = getY() + Rnd.get(radius * 2 + 1) - radius;
				final int z = getZ() + 20;
				
				final L2ItemInstance item = ItemTable.getInstance().createItem("CaravanCargo", cargo.getItemId(), cargo.getCount(), looter, this);
				
				if (item == null)
				{
					LOGGER.warn("L2NpcCaravanInstance: npc " + getNpcId() + " has unknown item " + cargo.getItemId() + " in its manifest, skipping.");
					continue;
				}
				
				// Reserve the cargo for whoever earned it, same as normal loot.
				if (looter != null)
				{
					item.getDropProtection().protect(looter);
				}
				
				item.dropMe(this, x, y, z);
				item.setProtected(false);
				
				dropped++;
			}
			catch (final Exception e)
			{
				LOGGER.error("L2NpcCaravanInstance: failed to drop item " + cargo.getItemId() + " from npc " + getNpcId(), e);
			}
		}
		
		if (Config.DEBUG || Config.DEVELOPER)
		{
			LOGGER.info("L2NpcCaravanInstance: npc " + getNpcId() + " spilled " + dropped + "/" + manifest.size() + " cargo item(s) at " + getX() + ", " + getY() + ", " + getZ() + ".");
		}
	}
	
	public int getRoutePos()
	{
		return routePos;
	}
	
	public void setRoutePos(final int pos)
	{
		routePos = pos;
	}
	
	public boolean isWalkingToNextPoint()
	{
		return walkingToNextPoint;
	}
	
	public void setWalkingToNextPoint(final boolean value)
	{
		walkingToNextPoint = value;
	}
	
	/**
	 * Say a line to everyone who can see the caravan. Used for waypoint chatter.
	 * @param chat the line to say
	 */
	public void broadcastCaravanChat(final String chat)
	{
		Broadcast.toKnownPlayers(this, new CreatureSay(getObjectId(), 0, getName(), chat));
	}
	
	/**
	 * Attackable by players and their summons only.<BR>
	 * <BR>
	 * It must NOT be attackable by other NPCs: {@link L2Character#doAttack} splashes polearm and AoE
	 * hits onto every nearby target for which this returns true, so allowing NPCs would let an
	 * escort's swing clip the caravan, which then calls the rest of the escort onto that guard - a
	 * feedback loop that turns the convoy into a brawl.
	 * @param  attacker whoever is looking for a fight
	 * @return          true for a living caravan attacked by a player or summon
	 */
	@Override
	public boolean isAutoAttackable(final L2Character attacker)
	{
		return !isAlikeDead() && attacker instanceof L2PlayableInstance;
	}
	
	@Override
	public boolean isAggressive()
	{
		return getTemplate().aggroRange > 0;
	}
}
