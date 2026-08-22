package com.l2jfrozen.gameserver.ai;

import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.model.L2Object;
import com.l2jfrozen.gameserver.model.actor.instance.L2EscortGardInstance;
import com.l2jfrozen.gameserver.model.actor.instance.L2NpcCaravanInstance;

/**
 * AI for a caravan escort guard.<BR>
 * <BR>
 * The guard marches with its caravan and fights anyone who attacks it. Combat is entirely inherited
 * from {@link L2AttackableAI} - the caravan's {@link L2CaravanAI} notifies the escort with
 * EVT_AGGRESSION, and the stock handler turns that into an attack. This class adds only the
 * travelling half: keeping station on the caravan while there is nothing to fight.<BR>
 * <BR>
 * Like {@link L2CaravanAI} it must extend {@link L2AttackableAI}, because
 * {@link com.l2jfrozen.gameserver.model.L2Attackable} casts its own AI to that type on the aggro and
 * attack paths.
 */
public class L2EscortGardAI extends L2AttackableAI
{
	/** How far the guard may drift from its caravan before closing the gap. */
	private static final int FOLLOW_RANGE = 150;

	/**
	 * Cached caravan. Once found it is kept even if the caravan leaves the guard's knownlist -
	 * otherwise a guard that fell behind could never catch up, having lost sight of what it follows.
	 */
	private L2NpcCaravanInstance caravan;

	public L2EscortGardAI(final L2Character.AIAccessor accessor)
	{
		super(accessor);
	}

	/**
	 * Keep the AI alive when the road is empty.<BR>
	 * <BR>
	 * {@link L2AttackableAI#changeIntention} detaches the AI from an idle attackable with no players
	 * in its knownlist. A detached guard stops following and is left behind, so IDLE is promoted to
	 * ACTIVE to skip that branch - the same fix {@link L2CaravanAI} needs.
	 * @param intention the requested intention
	 * @param arg0      first parameter
	 * @param arg1      second parameter
	 */
	@Override
	public void changeIntention(CtrlIntention intention, final Object arg0, final Object arg1)
	{
		if (intention == CtrlIntention.AI_INTENTION_IDLE && !getActor().isDead() && findCaravan() != null)
		{
			intention = CtrlIntention.AI_INTENTION_ACTIVE;
		}

		super.changeIntention(intention, arg0, arg1);
	}

	@Override
	protected void onEvtThink()
	{
		// Fighting takes priority - let the inherited attackable brain run the engagement.
		if (getIntention() == CtrlIntention.AI_INTENTION_ATTACK)
		{
			super.onEvtThink();
			return;
		}

		if (!keepStationOnCaravan())
		{
			// No caravan to escort: behave like an ordinary guard.
			super.onEvtThink();
		}
	}

	/**
	 * Close on the caravan if the guard has drifted too far.
	 * @return true if a caravan was found and station keeping is handling this tick
	 */
	private boolean keepStationOnCaravan()
	{
		final L2EscortGardInstance guard = getActor();
		final L2NpcCaravanInstance target = findCaravan();

		if (target == null || target.isDead() || guard.isDead() || guard.isMovementDisabled())
		{
			return false;
		}

		if (guard.isInsideRadius(target, FOLLOW_RANGE, true, false))
		{
			// Close enough - hold position rather than jittering around the caravan.
			return true;
		}

		if (getIntention() != CtrlIntention.AI_INTENTION_FOLLOW || getTarget() != target)
		{
			guard.setRunning();
			setIntention(CtrlIntention.AI_INTENTION_FOLLOW, target);
		}

		return true;
	}

	/**
	 * Find the caravan this guard belongs to: the first living {@link L2NpcCaravanInstance} in the
	 * knownlist sharing this guard's faction.
	 * @return the caravan, or null if none has ever been seen
	 */
	private L2NpcCaravanInstance findCaravan()
	{
		if (caravan != null && !caravan.isDead())
		{
			return caravan;
		}

		// A dead caravan is forgotten so a respawned one can be picked up.
		caravan = null;

		final L2EscortGardInstance guard = getActor();
		final String factionId = guard.getFactionId();

		if (factionId == null)
		{
			return null;
		}

		for (final L2Object obj : guard.getKnownList().getKnownObjects().values())
		{
			if (!(obj instanceof L2NpcCaravanInstance))
			{
				continue;
			}

			final L2NpcCaravanInstance candidate = (L2NpcCaravanInstance) obj;

			if (!candidate.isDead() && factionId.equalsIgnoreCase(candidate.getFactionId()))
			{
				caravan = candidate;
				return caravan;
			}
		}

		return null;
	}

	@Override
	public L2EscortGardInstance getActor()
	{
		return (L2EscortGardInstance) super.getActor();
	}
}
