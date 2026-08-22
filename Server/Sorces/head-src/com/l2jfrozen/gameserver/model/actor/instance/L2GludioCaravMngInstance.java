package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.network.serverpackets.ActionFailed;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * Caravan manager for Gludio.<BR>
 * <BR>
 * Talking to this NPC opens its own window, loaded from
 * <code>data/html/caravan_manager/&lt;npcId&gt;.htm</code>. The folder is private to this NPC
 * category so its pages can be edited without touching any shared HTML, and adding another manager
 * only means dropping a new file named after its npc id alongside.<BR>
 * <BR>
 * The window currently just closes; dispatching caravans and editing their manifests come later.<BR>
 * <BR>
 * The core resolves an NPC's <code>type</code> column by appending "Instance", so this class name is
 * fixed by <code>type = 'L2GludioCaravMng'</code> in the <code>npc</code> table and cannot be renamed
 * on its own.<BR>
 * <BR>
 * Extends {@link L2FolkInstance} so it behaves as an ordinary town NPC - targetable, clickable and
 * not attackable.
 */
public class L2GludioCaravMngInstance extends L2FolkInstance
{
	/** Private HTML folder for this NPC category. */
	private static final String HTML_PATH = "data/html/caravan_manager/";

	private static final String BYPASS_CLOSE = "close";

	public L2GludioCaravMngInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void showChatWindow(final L2PcInstance player, final int val)
	{
		if (player == null)
		{
			return;
		}

		final String page = val == 0 ? String.valueOf(getNpcId()) : getNpcId() + "-" + val;

		showChatWindow(player, HTML_PATH + page + ".htm");
	}

	@Override
	public void onBypassFeedback(final L2PcInstance player, final String command)
	{
		if (player == null || command == null)
		{
			return;
		}

		if (command.startsWith(BYPASS_CLOSE))
		{
			// Sending ActionFailed with no replacement HTML is how this codebase dismisses a dialog -
			// see Repair.repair_close_win.
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		super.onBypassFeedback(player, command);
	}
}
