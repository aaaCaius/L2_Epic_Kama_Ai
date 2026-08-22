package com.l2jfrozen.gameserver.model.actor.instance;

import java.util.List;

import com.l2jfrozen.gameserver.datatables.CaravanCargoTable;
import com.l2jfrozen.gameserver.datatables.CaravanCargoTable.CargoItem;
import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.managers.CaravanSpawnManager;
import com.l2jfrozen.gameserver.managers.CaravanSpawnManager.SpawnResult;
import com.l2jfrozen.gameserver.network.serverpackets.ActionFailed;
import com.l2jfrozen.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jfrozen.gameserver.templates.L2Item;
import com.l2jfrozen.gameserver.templates.L2NpcTemplate;

/**
 * Caravan manager for Gludio.<BR>
 * <BR>
 * Dispatches caravan convoys and reports what they carry. Pages live in
 * <code>data/html/caravan_manager/</code>, private to this NPC category, and are resolved from the
 * npc id so another manager only needs its own file - no code change.<BR>
 * <BR>
 * Layout stays in the HTML; only genuinely dynamic values are substituted from here. The cargo list
 * is the one exception that cannot live in a static file, since its length varies and item ids have
 * to be resolved to display names, so the page carries a <code>%cargo%</code> placeholder that this
 * class fills.<BR>
 * <BR>
 * The core resolves an NPC's <code>type</code> column by appending "Instance", so this class name is
 * fixed by <code>type = 'L2GludioCaravMng'</code> in the <code>npc</code> table.
 */
public class L2GludioCaravMngInstance extends L2FolkInstance
{
	/** Private HTML folder for this NPC category. */
	private static final String HTML_PATH = "data/html/caravan_manager/";

	/** The caravan whose manifest this manager reports. */
	private static final int CARAVAN_NPC_ID = 100203;

	private static final String BYPASS_CLOSE = "close";
	private static final String BYPASS_SPAWN = "spawn_caravan";
	private static final String BYPASS_CARGO = "cargo";
	private static final String BYPASS_MAIN = "main";

	public L2GludioCaravMngInstance(final int objectId, final L2NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void showChatWindow(final L2PcInstance player, final int val)
	{
		showMain(player);
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
			dispatch(player);
			return;
		}

		if (command.startsWith(BYPASS_CARGO))
		{
			showCargo(player);
			return;
		}

		if (command.startsWith(BYPASS_MAIN))
		{
			showMain(player);
			return;
		}

		super.onBypassFeedback(player, command);
	}

	/** Put a convoy on the road and report the outcome. */
	private void dispatch(final L2PcInstance player)
	{
		final CaravanSpawnManager manager = CaravanSpawnManager.getInstance();
		final SpawnResult result = manager.spawnConvoy();

		switch (result)
		{
			case OK:
				player.sendMessage("Caravan dispatched - " + manager.getActiveCount() + " of " + CaravanSpawnManager.MAX_CONVOYS + " on the road.");
				break;

			case AT_CAPACITY:
				player.sendMessage("All " + CaravanSpawnManager.MAX_CONVOYS + " caravans are already on the road.");
				break;

			case NO_ROUTE:
				player.sendMessage("The caravan has no route. Check walker_routes.csv for npc " + CARAVAN_NPC_ID + ".");
				break;

			default:
				player.sendMessage("The caravan could not be dispatched. See the server log.");
				break;
		}

		// Reopen so the count on the page is current.
		showMain(player);
	}

	private void showMain(final L2PcInstance player)
	{
		if (player == null)
		{
			return;
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + getNpcId() + ".htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%active%", String.valueOf(CaravanSpawnManager.getInstance().getActiveCount()));
		html.replace("%max%", String.valueOf(CaravanSpawnManager.MAX_CONVOYS));
		html.replace("%cargolines%", String.valueOf(CaravanCargoTable.getInstance().getCargo(CARAVAN_NPC_ID).size()));

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	/** The manifest page: what a caravan is carrying, by name. */
	private void showCargo(final L2PcInstance player)
	{
		if (player == null)
		{
			return;
		}

		final CaravanCargoTable cargoTable = CaravanCargoTable.getInstance();
		final List<CargoItem> manifest = cargoTable.getCargo(CARAVAN_NPC_ID);

		final StringBuilder rows = new StringBuilder();

		if (manifest.isEmpty())
		{
			rows.append("<tr><td>The caravan is carrying nothing.</td></tr>");
		}
		else
		{
			for (final CargoItem cargo : manifest)
			{
				rows.append("<tr><td width=150>").append(itemName(cargo.getItemId()));
				rows.append("</td><td width=60 align=right><font color=\"LEVEL\">").append(cargo.getCount());
				rows.append("</font></td></tr>");
			}
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + getNpcId() + "-cargo.htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%cargo%", rows.toString());
		html.replace("%drops%", cargoTable.isEnabled() ? "Enabled" : "Disabled");

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	/**
	 * @param  itemId the item to name
	 * @return        its display name, or the raw id if the template is unknown
	 */
	private String itemName(final int itemId)
	{
		final L2Item item = ItemTable.getInstance().getTemplate(itemId);

		return item == null ? "Unknown item " + itemId : item.getName();
	}
}
