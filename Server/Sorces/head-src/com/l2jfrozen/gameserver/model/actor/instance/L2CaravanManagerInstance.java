package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * Caravan manager, generic type.<BR>
 * <BR>
 * Same role as {@link L2GludioCaravMngInstance} and inherits its behaviour wholesale - the caravan
 * control window and the dispatch button. Its page is
 * <code>data/html/caravan_manager/&lt;npcId&gt;.htm</code>, resolved from the npc id like every other
 * manager, so no code is needed per NPC.<BR>
 * <BR>
 * This exists because npc 70442 "Bools Dip" carries <code>type = 'L2CaravanManager'</code> in the
 * database. Without a matching class its spawn row threw ClassNotFoundException, and because
 * SpawnTable wraps the whole row loop in one try/catch that killed every custom spawn ordered after
 * it - including both Caravan Manager spawns.
 */
public class L2CaravanManagerInstance extends L2GludioCaravMngInstance
{
	public L2CaravanManagerInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}
}
