package com.l2jfrozen.gameserver.handler.admincommandhandlers;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.handler.IAdminCommandHandler;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * @author ReynalDev
 */
public class AdminServerUpdates implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_server_updates"
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (command.equals("admin_server_updates"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile("data/html/admin/updates.htm");
			html.replace("%revision%", Config.REVISION);
			html.replace("%build_date%", Config.BUILD_DATE);
			html.replace("%head_revision%", Config.HEAD_REVISION);
			html.replace("%head_pub_date%", Config.HEAD_PUB_DATE);
			activeChar.sendPacket(html);
		}
		return true;
	}
	
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
}
