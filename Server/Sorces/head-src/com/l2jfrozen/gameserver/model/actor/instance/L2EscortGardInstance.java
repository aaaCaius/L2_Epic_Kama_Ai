package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.ai.CtrlIntention;
import com.l2jfrozen.gameserver.model.L2Attackable;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.model.actor.position.L2CharPosition;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * A caravan escort guard.<BR>
 * <BR>
 * Travels with a {@link L2NpcCaravanInstance} and defends it. Unlike a town {@link L2GuardInstance},
 * an escort <b>is</b> attackable by players - raiding the caravan means fighting through its escort,
 * so making them invulnerable to players would defeat the point.<BR>
 * <BR>
 * {@link L2GuardInstance} is final, so this extends {@link L2Attackable} directly and reuses the
 * same home-position pattern: the guard drifts back to where it spawned once it has nothing to fight.
 */
public class L2EscortGardInstance extends L2Attackable
{
	private int homeX;
	private int homeY;
	private int homeZ;
	
	private boolean homeSet;
	
	public L2EscortGardInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		if (!homeSet)
		{
			homeX = getX();
			homeY = getY();
			homeZ = getZ();
			homeSet = true;
		}
	}
	
	/**
	 * Escorts are fair game - a caravan raid has to get through them.
	 * @param  attacker whoever is looking for a fight
	 * @return          true while the guard is alive
	 */
	@Override
	public boolean isAutoAttackable(final L2Character attacker)
	{
		return !isAlikeDead();
	}
	
	@Override
	public boolean isAggressive()
	{
		return getTemplate().aggroRange > 0;
	}
	
	/** Walk back to the spawn point once there is nothing left to fight. */
	public void returnHome()
	{
		if (!homeSet || isDead() || isMovementDisabled())
		{
			return;
		}
		
		if (getAI().getIntention() != CtrlIntention.AI_INTENTION_IDLE && getAI().getIntention() != CtrlIntention.AI_INTENTION_ACTIVE)
		{
			return;
		}
		
		if (!isInsideRadius(homeX, homeY, homeZ, 40, false, false))
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(homeX, homeY, homeZ, 0));
		}
	}
	
	public int getHomeX()
	{
		return homeX;
	}
	
	public int getHomeY()
	{
		return homeY;
	}
	
	public int getHomeZ()
	{
		return homeZ;
	}
}
