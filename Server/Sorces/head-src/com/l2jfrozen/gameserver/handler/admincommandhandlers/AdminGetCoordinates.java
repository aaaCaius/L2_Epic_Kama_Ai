package com.l2jfrozen.gameserver.handler.admincommandhandlers;

import com.l2jfrozen.gameserver.handler.IAdminCommandHandler;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;

/**
 * Report the GM's own position in chat.<BR>
 * <BR>
 * <code>//get_coordinates</code> prints X, Y, Z and heading, followed by a line already formatted for
 * <code>data/csv/walker_routes.csv</code> - stand where a waypoint belongs, run the command, and the
 * row can be pasted straight into the route file.
 */
public class AdminGetCoordinates implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_get_coordinates",
		"admin_getcoordinates",
		"admin_loc"
	};

	@Override
	public boolean useAdminCommand(final String command, final L2PcInstance activeChar)
	{
		if (activeChar == null)
		{
			return false;
		}

		final int x = activeChar.getX();
		final int y = activeChar.getY();
		final int z = activeChar.getZ();
		final int heading = activeChar.getHeading();

		activeChar.sendMessage("Position: X=" + x + " Y=" + y + " Z=" + z + " Heading=" + heading);

		// route_id;npc_id;move_point;chatText;move_x;move_y;move_z;delay;running
		activeChar.sendMessage("walker_routes.csv: <route>;<npc>;<point>;NULL;" + x + ";" + y + ";" + z + ";5;False");

		return true;
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
