package com.l2jfrozen.gameserver.model.actor.instance;

import com.l2jfrozen.gameserver.managers.CaravanSpawnManager;
import com.l2jfrozen.gameserver.managers.CaravanSpawnManager.SpawnResult;
import com.l2jfrozen.gameserver.network.serverpackets.ActionFailed;
import com.l2jfrozen.gameserver.network.serverpackets.NpcHtmlMessage;
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

	private static final String BYPASS_SPAWN = "spawn_caravan";

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

		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + page + ".htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%active%", String.valueOf(CaravanSpawnManager.getInstance().getActiveCount()));
		html.replace("%max%", String.valueOf(CaravanSpawnManager.MAX_CONVOYS));
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
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

		if (command.startsWith(BYPASS_SPAWN))
		{
			final CaravanSpawnManager manager = CaravanSpawnManager.getInstance();
			final SpawnResult result = manager.spawnConvoy();

			switch (result)
			{
				case OK:
					player.sendMessage("Caravan dispatched. " + manager.getActiveCount() + " of " + CaravanSpawnManager.MAX_CONVOYS + " on the road.");
					break;

				case AT_CAPACITY:
					player.sendMessage("No more caravans can be dispatched - " + CaravanSpawnManager.MAX_CONVOYS + " are already on the road.");
					break;

				case NO_ROUTE:
					player.sendMessage("The caravan has no route defined. Check walker_routes.csv.");
					break;

				default:
					player.sendMessage("The caravan could not be dispatched. See the server log.");
					break;
			}

			// Reopen so the active count on the page is current.
			showChatWindow(player, 0);
			return;
		}

		super.onBypassFeedback(player, command);
	}
}
