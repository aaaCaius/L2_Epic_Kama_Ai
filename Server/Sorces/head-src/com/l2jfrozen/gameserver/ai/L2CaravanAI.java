package com.l2jfrozen.gameserver.ai;

import java.util.List;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.datatables.csv.NpcWalkerRoutesTable;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.model.L2NpcWalkerNode;
import com.l2jfrozen.gameserver.model.L2Object;
import com.l2jfrozen.gameserver.model.actor.instance.L2NpcCaravanInstance;
import com.l2jfrozen.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jfrozen.gameserver.model.actor.position.L2CharPosition;

/**
 * AI for a travelling caravan.<BR>
 * <BR>
 * The caravan walks a route from <code>walker_routes.csv</code>, reusing the waypoint data
 * {@link NpcWalkerRoutesTable} already loads. When attacked it calls its escort and runs; only once
 * every escort is dead does it turn and fight.<BR>
 * <BR>
 * <B>Why this extends {@link L2AttackableAI} rather than {@link L2CharacterAI}:</B> mirroring
 * {@link L2NpcWalkerAI} would be the natural fit for route walking, but
 * {@link com.l2jfrozen.gameserver.model.L2Attackable} casts its own AI to {@link L2AttackableAI} on
 * the aggro and attack paths, so a caravan would throw {@link ClassCastException} the moment a player
 * hit it. The type is therefore kept and the inherited behaviour suppressed selectively - see
 * {@link #changeIntention} and {@link #onEvtThink}.
 */
public class L2CaravanAI extends L2AttackableAI
{
	private static final int DEFAULT_MOVE_DELAY = 0;

	/** Waypoints for this caravan, by npc id. Empty if none are defined. */
	private final List<L2NpcWalkerNode> route;

	private long nextMoveTime;

	public L2CaravanAI(final L2Character.AIAccessor accessor)
	{
		super(accessor);

		route = NpcWalkerRoutesTable.getInstance().getRouteForNpc(getActor().getNpcId());

		if (route == null || route.isEmpty())
		{
			LOGGER.warn("L2CaravanAI: no walker_routes.csv waypoints for npc " + getActor().getNpcId() + ", it will stand still.");
		}
	}

	/**
	 * Keep the AI alive on an empty road.<BR>
	 * <BR>
	 * {@link L2AttackableAI#changeIntention} detaches the AI outright when an idle attackable has no
	 * players in its knownlist, which throws away the caravan's journey. Promoting IDLE to ACTIVE
	 * before delegating skips that branch: the parent then finds a non-IDLE intention, so it keeps
	 * the AI and starts its task as normal.
	 * @param intention the requested intention
	 * @param arg0      first parameter
	 * @param arg1      second parameter
	 */
	@Override
	public void changeIntention(CtrlIntention intention, final Object arg0, final Object arg1)
	{
		if (intention == CtrlIntention.AI_INTENTION_IDLE && hasRoute() && !getActor().isDead())
		{
			intention = CtrlIntention.AI_INTENTION_ACTIVE;
		}

		super.changeIntention(intention, arg0, arg1);
	}

	@Override
	protected void onEvtThink()
	{
		// Cornered and undefended: hand back to the normal attackable brain so it fights for its life.
		if (isCornered())
		{
			super.onEvtThink();
			return;
		}

		// Otherwise the caravan travels. super.onEvtThink() is deliberately NOT called here:
		// thinkActive() random-walks the NPC around its spawn point, cancelling waypoint movement.
		if (!canWalk())
		{
			return;
		}

		if (getActor().isWalkingToNextPoint())
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
	 * Attacked while travelling: shout for the escort, then run for it.<BR>
	 * <BR>
	 * The stock faction call lives inside {@link L2AttackableAI}'s thinkAttack, which only runs once
	 * the NPC has entered AI_INTENTION_ATTACK - and entering ATTACK would abandon the route. So the
	 * same EVT_AGGRESSION notification is issued here directly, and the caravan keeps moving instead
	 * of turning to fight.
	 * @param attacker whoever hit the caravan
	 */
	@Override
	protected void onEvtAttacked(final L2Character attacker)
	{
		if (isCornered())
		{
			super.onEvtAttacked(attacker);
			return;
		}

		callEscortsForHelp(attacker);

		// Break into a run and stay on the road.
		getActor().setRunning();
	}

	/**
	 * Ignore aggression while the escort still stands - the guards answer for the caravan.
	 * @param target the aggressor
	 * @param aggro  amount of aggression
	 */
	@Override
	protected void onEvtAggression(final L2Character target, final int aggro)
	{
		if (isCornered())
		{
			super.onEvtAggression(target, aggro);
		}
	}

	/**
	 * Notify every living same-faction NPC in range to attack the caravan's assailant.<BR>
	 * <BR>
	 * Mirrors the faction-call loop in {@link L2AttackableAI}'s thinkAttack. Requires
	 * <code>faction_id</code> and a non-zero <code>faction_range</code> on both the caravan and its
	 * escort in the <code>npc</code> table.
	 * @param attacker the target to sic the escort on
	 */
	private void callEscortsForHelp(final L2Character attacker)
	{
		final L2NpcCaravanInstance caravan = getActor();
		final String factionId = caravan.getFactionId();

		if (factionId == null || attacker == null)
		{
			return;
		}

		for (final L2Object obj : caravan.getKnownList().getKnownObjects().values())
		{
			if (!(obj instanceof L2NpcInstance) || obj == caravan)
			{
				continue;
			}

			final L2NpcInstance escort = (L2NpcInstance) obj;

			if (escort.isDead() || escort.getFactionId() == null || !factionId.equalsIgnoreCase(escort.getFactionId()) || escort.getFactionRange() == 0)
			{
				continue;
			}

			if (!caravan.isInsideRadius(escort, escort.getFactionRange(), true, false) || escort.getAI() == null)
			{
				continue;
			}

			escort.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, attacker, 1);
		}
	}

	/**
	 * @return true when no living escort remains, so the caravan must defend itself
	 */
	private boolean isCornered()
	{
		final L2NpcCaravanInstance caravan = getActor();
		final String factionId = caravan.getFactionId();

		if (factionId == null)
		{
			// No escort was ever configured - it is on its own by definition.
			return true;
		}

		for (final L2Object obj : caravan.getKnownList().getKnownObjects().values())
		{
			if (!(obj instanceof L2NpcInstance) || obj == caravan)
			{
				continue;
			}

			final L2NpcInstance escort = (L2NpcInstance) obj;

			if (!escort.isDead() && factionId.equalsIgnoreCase(escort.getFactionId()))
			{
				return false;
			}
		}

		return true;
	}

	private boolean hasRoute()
	{
		return route != null && !route.isEmpty();
	}

	/**
	 * @return true when the caravan is alive, able to move and has somewhere to go
	 */
	private boolean canWalk()
	{
		if (!hasRoute() || !Config.ALLOW_NPC_WALKERS)
		{
			return false;
		}

		final L2NpcCaravanInstance actor = getActor();

		return actor != null && !actor.isDead() && !actor.isMovementDisabled() && !actor.isAfraid();
	}

	/**
	 * Arrival event - the reliable signal that a waypoint was reached. The coordinate comparison in
	 * {@link #checkArrived()} is kept as a fallback for moves that finish without this firing.
	 */
	@Override
	protected void onEvtArrived()
	{
		super.onEvtArrived();

		if (getActor().isWalkingToNextPoint())
		{
			arriveAtWaypoint();
		}
	}

	/**
	 * If the caravan cannot path to its next waypoint, teleport it there rather than let it wedge on
	 * geometry - the same fallback {@link L2NpcWalkerAI} uses.
	 * @param blocked_at_pos where it got stuck
	 */
	@Override
	protected void onEvtArrivedBlocked(final L2CharPosition blocked_at_pos)
	{
		final int pos = getActor().getRoutePos();

		if (hasRoute() && pos >= 0 && pos < route.size())
		{
			final L2NpcWalkerNode node = route.get(pos);

			LOGGER.warn("L2CaravanAI: npc " + getActor().getNpcId() + " blocked at waypoint " + pos + " (" + blocked_at_pos.x + ", " + blocked_at_pos.y + ", " + blocked_at_pos.z + "), teleporting to it.");

			getActor().teleToLocation(node.getMoveX(), node.getMoveY(), node.getMoveZ(), false);
			arriveAtWaypoint();
		}

		super.onEvtArrivedBlocked(blocked_at_pos);
	}

	/** Coordinate-based arrival check, used when no arrival event turns up. */
	private void checkArrived()
	{
		final int pos = getActor().getRoutePos();

		if (pos < 0 || pos >= route.size())
		{
			getActor().setWalkingToNextPoint(false);
			return;
		}

		final L2NpcWalkerNode node = route.get(pos);
		final L2Character actor = getActor();

		if (actor.getX() == node.getMoveX() && actor.getY() == node.getMoveY() && actor.getZ() == node.getMoveZ())
		{
			arriveAtWaypoint();
		}
	}

	/** Announce the waypoint, then hold for its configured delay before moving on. */
	private void arriveAtWaypoint()
	{
		final int pos = getActor().getRoutePos();

		if (pos < 0 || pos >= route.size())
		{
			getActor().setWalkingToNextPoint(false);
			return;
		}

		final L2NpcWalkerNode node = route.get(pos);

		announce(node.getChatText());

		long delay = node.getDelay() * 1000L;

		if (delay < 0)
		{
			delay = DEFAULT_MOVE_DELAY;

			if (Config.DEVELOPER)
			{
				LOGGER.warn("L2CaravanAI: negative delay on waypoint " + pos + " of npc " + getActor().getNpcId() + ", using " + DEFAULT_MOVE_DELAY + " instead.");
			}
		}

		nextMoveTime = System.currentTimeMillis() + delay;
		getActor().setWalkingToNextPoint(false);
	}

	private void walkToLocation()
	{
		final L2NpcCaravanInstance caravan = getActor();

		final int pos = caravan.getRoutePos() < route.size() - 1 ? caravan.getRoutePos() + 1 : 0;
		caravan.setRoutePos(pos);

		final L2NpcWalkerNode node = route.get(pos);

		if (node.getRunning())
		{
			caravan.setRunning();
		}
		else
		{
			caravan.setWalking();
		}

		caravan.setWalkingToNextPoint(true);

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
	public L2NpcCaravanInstance getActor()
	{
		return (L2NpcCaravanInstance) super.getActor();
	}
}
