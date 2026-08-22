package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * Caravan manager for Gludio.<BR>
 * <BR>
 * Intended to dispatch caravans and decide what each one carries. That behaviour is not implemented
 * yet - this is the minimum needed for the NPC to spawn and be talked to, so the rest can be built on
 * top of a working NPC rather than against a ClassNotFoundException.<BR>
 * <BR>
 * The core resolves an NPC's <code>type</code> column by appending "Instance", so the name of this
 * class is fixed by <code>type = 'L2GludioCaravMng'</code> on npc 100205 and cannot be changed
 * independently of the database.<BR>
 * <BR>
 * Extends {@link L2FolkInstance} so it behaves as an ordinary talkable town NPC: targetable, shows an
 * HTML window on click, and is not attackable. When dispatch logic arrives it will most likely drive
 * {@link L2NpcCaravanInstance} spawns and populate the <code>active_caravans</code> table, which
 * already carries caravan_id, source, destination and way_point columns for exactly that purpose.
 */
public class L2GludioCaravMngInstance extends L2FolkInstance
{
	public L2GludioCaravMngInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}
}
