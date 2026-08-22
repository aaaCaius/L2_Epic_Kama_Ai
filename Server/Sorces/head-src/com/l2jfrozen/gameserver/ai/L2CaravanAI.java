package com.l2jfrozen.gameserver.ai;

import java.util.List;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.datatables.csv.NpcWalkerRoutesTable;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.model.L2NpcWalkerNode;
import com.l2jfrozen.gameserver.model.actor.position.L2CharPosition;
import com.l2jfrozen.gameserver.thread.ThreadPoolManager;

/**
 * AI for a travelling caravan.<BR>
 * <BR>
 * Extends {@link L2AttackableAI} so the caravan keeps every combat behaviour of a normal attackable
 * NPC - it can be targeted, damaged and killed - and adds route walking on top, reusing the waypoint
 * data already loaded by {@link NpcWalkerRoutesTable} from <code>walker_routes.csv</code>.<BR>
 * <BR>
 * Movement only runs while the caravan is idle. The moment it acquires a combat intention the
 * inherited attackable behaviour takes over, and walking resumes once the fight ends.
 */
public class L2CaravanAI extends L2AttackableAI
{
	private static final int DEFAULT_MOVE_DELAY = 0;
	
	/** Waypoints for this caravan, by npc id. Empty if none are defined. */
	private final List<L2NpcWalkerNode> route;
	
	private long nextMoveTime;
	
	private boolean walkingToNextPoint;
	
	private int currentPos = -1;
	
	public L2CaravanAI(final L2Character.AIAccessor accessor)
	{
		super(accessor);
		
		route = NpcWalkerRoutesTable.getInstance().getRouteForNpc(getActor().getNpcId());
		
		if (route == null || route.isEmpty())
		{
			LOGGER.warn("L2CaravanAI: no walker_routes.csv waypoints for npc " + getActor().getNpcId() + ", it will stand still.");
		}
		
		ThreadPoolManager.getInstance().scheduleAiAtFixedRate(this, 1000, 1000);
	}
	
	@Override
	public void run()
	{
		onEvtThink();
	}
	
	@Override
	protected void onEvtThink()
	{
		// Combat and everything else stays with the inherited attackable behaviour.
		super.onEvtThink();
		
		if (!canWalk())
		{
			return;
		}
		
		if (walkingToNextPoint)
		{
			checkArrived();
			return;
		}
		
		if (nextMoveTime < System.currentTimeMillis())
		{
			walkToLocation();
		}
	}
	
	/**
	 * @return true when the caravan is alive, idle and has somewhere to go
	 */
	private boolean canWalk()
	{
		if (route == null || route.isEmpty())
		{
			return false;
		}
		
		if (!Config.ALLOW_NPC_WALKERS)
		{
			return false;
		}
		
		final L2Character actor = getActor();
		
		if (actor == null || actor.isDead() || actor.isMovementDisabled() || actor.isAfraid())
		{
			return false;
		}
		
		// Busy fighting or casting - let L2AttackableAI drive.
		final CtrlIntention intention = getIntention();
		
		return intention != CtrlIntention.AI_INTENTION_ATTACK && intention != CtrlIntention.AI_INTENTION_CAST;
	}
	
	/**
	 * If the caravan cannot path to its next waypoint, teleport it there rather than let it wedge on
	 * geometry - the same fallback {@link L2NpcWalkerAI} uses.
	 * @param blocked_at_pos where it got stuck
	 */
	@Override
	protected void onEvtArrivedBlocked(final L2CharPosition blocked_at_pos)
	{
		if (route != null && !route.isEmpty() && currentPos >= 0 && currentPos < route.size())
		{
			final L2NpcWalkerNode node = route.get(currentPos);
			
			LOGGER.warn("L2CaravanAI: npc " + getActor().getNpcId() + " blocked at waypoint " + currentPos + " (" + blocked_at_pos.x + ", " + blocked_at_pos.y + ", " + blocked_at_pos.z + "), teleporting to it.");
			
			getActor().teleToLocation(node.getMoveX(), node.getMoveY(), node.getMoveZ(), false);
			walkingToNextPoint = false;
		}
		
		super.onEvtArrivedBlocked(blocked_at_pos);
	}
	
	private void checkArrived()
	{
		if (currentPos < 0 || currentPos >= route.size())
		{
			walkingToNextPoint = false;
			return;
		}
		
		final L2NpcWalkerNode node = route.get(currentPos);
		final L2Character actor = getActor();
		
		if (actor.getX() != node.getMoveX() || actor.getY() != node.getMoveY() || actor.getZ() != node.getMoveZ())
		{
			return;
		}
		
		announce(node.getChatText());
		
		long delay = node.getDelay() * 1000L;
		
		if (delay < 0)
		{
			delay = DEFAULT_MOVE_DELAY;
			
			if (Config.DEVELOPER)
			{
				LOGGER.warn("L2CaravanAI: negative delay on waypoint " + currentPos + " of npc " + getActor().getNpcId() + ", using " + DEFAULT_MOVE_DELAY + " instead.");
			}
		}
		
		nextMoveTime = System.currentTimeMillis() + delay;
		walkingToNextPoint = false;
	}
	
	private void walkToLocation()
	{
		currentPos = currentPos < route.size() - 1 ? currentPos + 1 : 0;
		
		final L2NpcWalkerNode node = route.get(currentPos);
		
		if (node.getRunning())
		{
			getActor().setRunning();
		}
		else
		{
			getActor().setWalking();
		}
		
		walkingToNextPoint = true;
		
		setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(node.getMoveX(), node.getMoveY(), node.getMoveZ(), 0));
	}
	
	/**
	 * Broadcast a waypoint's chat line, if it has one.
	 * @param chat text from the route, may be null or the literal "NULL"
	 */
	private void announce(final String chat)
	{
		if (chat == null || chat.equalsIgnoreCase("NULL") || chat.trim().isEmpty())
		{
			return;
		}
		
		try
		{
			getActor().broadcastCaravanChat(chat);
		}
		catch (final Exception e)
		{
			LOGGER.warn("L2CaravanAI: could not broadcast waypoint text for npc " + getActor().getNpcId(), e);
		}
	}
	
	@Override
	public com.l2jfrozen.gameserver.model.actor.instance.L2NpcCaravanInstance getActor()
	{
		return (com.l2jfrozen.gameserver.model.actor.instance.L2NpcCaravanInstance) super.getActor();
	}
}
