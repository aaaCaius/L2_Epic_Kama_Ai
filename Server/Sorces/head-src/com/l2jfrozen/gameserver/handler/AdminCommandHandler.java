package com.l2jfrozen.gameserver.handler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.datatables.xml.AdminCommandAccessRights;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminAdmin;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminAnnouncements;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminBBS;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminBan;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminBoat;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminBuffs;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminCache;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminChangeAccessLevel;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminCharSupervision;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminChristmas;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminClanHall;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminCreateItem;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminCursedWeapons;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminDelete;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminDoorControl;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminEditChar;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminEditNpc;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminEffects;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminEnchant;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminExpSp;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminFightCalculator;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminFortSiege;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminGeodata;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminGm;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminGmChat;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminHeal;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminHelpPage;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminInvul;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminKick;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminKill;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminLevel;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminLogin;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMammon;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminManor;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMassControl;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMassRecall;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMenu;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMobGroup;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminMonsterRace;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminNoble;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminPForge;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminPetition;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminPledge;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminPolymorph;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminReload;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminRepairChar;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminRes;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminRideWyvern;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminScript;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminServerUpdates;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminShop;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminShutdown;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminSiege;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminSkill;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminSpawn;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminTarget;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminTeleport;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminTest;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminTownWar;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminWho;
import com.l2jfrozen.gameserver.handler.admincommandhandlers.AdminZone;

/**
 * @author ReynalDev
 */
public class AdminCommandHandler
{
	protected static final Logger LOGGER = Logger.getLogger(AdminCommandHandler.class);
	private static AdminCommandHandler instance;
	private Map<String, IAdminCommandHandler> adminCommands = new HashMap<>();
	
	public static AdminCommandHandler getInstance()
	{
		if (instance == null)
		{
			instance = new AdminCommandHandler();
		}
		return instance;
	}
	
	private AdminCommandHandler()
	{
		registerAdminCommandHandler(new AdminAdmin());
		registerAdminCommandHandler(new AdminAnnouncements());
		registerAdminCommandHandler(new AdminBBS());
		registerAdminCommandHandler(new AdminBan());
		registerAdminCommandHandler(new AdminBoat());
		registerAdminCommandHandler(new AdminBuffs());
		registerAdminCommandHandler(new AdminCache());
		registerAdminCommandHandler(new AdminChangeAccessLevel());
		registerAdminCommandHandler(new AdminCharSupervision());
		registerAdminCommandHandler(new AdminChristmas());
		registerAdminCommandHandler(new AdminClanHall());
		registerAdminCommandHandler(new AdminCreateItem());
		registerAdminCommandHandler(new AdminCursedWeapons());
		registerAdminCommandHandler(new AdminDelete());
		registerAdminCommandHandler(new AdminDoorControl());
		registerAdminCommandHandler(new AdminEditChar());
		registerAdminCommandHandler(new AdminEditNpc());
		registerAdminCommandHandler(new AdminEffects());
		registerAdminCommandHandler(new AdminEnchant());
		registerAdminCommandHandler(new AdminExpSp());
		registerAdminCommandHandler(new AdminFightCalculator());
		registerAdminCommandHandler(new AdminFortSiege());
		registerAdminCommandHandler(new AdminGeodata());
		registerAdminCommandHandler(new AdminGm());
		registerAdminCommandHandler(new AdminGmChat());
		registerAdminCommandHandler(new AdminHeal());
		registerAdminCommandHandler(new AdminHelpPage());
		registerAdminCommandHandler(new AdminInvul());
		registerAdminCommandHandler(new AdminKick());
		registerAdminCommandHandler(new AdminKill());
		registerAdminCommandHandler(new AdminLevel());
		registerAdminCommandHandler(new AdminLogin());
		registerAdminCommandHandler(new AdminMammon());
		registerAdminCommandHandler(new AdminManor());
		registerAdminCommandHandler(new AdminMassControl());
		registerAdminCommandHandler(new AdminMassRecall());
		registerAdminCommandHandler(new AdminMenu());
		registerAdminCommandHandler(new AdminMobGroup());
		registerAdminCommandHandler(new AdminMonsterRace());
		registerAdminCommandHandler(new AdminNoble());
		registerAdminCommandHandler(new AdminPForge());
		registerAdminCommandHandler(new AdminPetition());
		registerAdminCommandHandler(new AdminPledge());
		registerAdminCommandHandler(new AdminPolymorph());
		registerAdminCommandHandler(new AdminReload());
		registerAdminCommandHandler(new AdminRepairChar());
		registerAdminCommandHandler(new AdminRes());
		registerAdminCommandHandler(new AdminRideWyvern());
		registerAdminCommandHandler(new AdminScript());
		registerAdminCommandHandler(new AdminServerUpdates());
		registerAdminCommandHandler(new AdminShop());
		registerAdminCommandHandler(new AdminShutdown());
		registerAdminCommandHandler(new AdminSiege());
		registerAdminCommandHandler(new AdminSkill());
		registerAdminCommandHandler(new AdminSpawn());
		registerAdminCommandHandler(new AdminTarget());
		registerAdminCommandHandler(new AdminTeleport());
		registerAdminCommandHandler(new AdminTest());
		registerAdminCommandHandler(new AdminTownWar());
		registerAdminCommandHandler(new AdminWho());
		registerAdminCommandHandler(new AdminZone());
		// ATTENTION: adding new command handlers, you have to add the new command into gameserver\config\access_level\adminCommands.xml
		
		LOGGER.info("AdminCommandHandler: Loaded " + adminCommands.size() + " handlers.");
		
		if (Config.DEBUG)
		{
			String[] commands = new String[adminCommands.keySet().size()];
			
			commands = adminCommands.keySet().toArray(commands);
			
			Arrays.sort(commands);
			
			for (final String command : commands)
			{
				if (AdminCommandAccessRights.getInstance().accessRightForCommand(command) < 0)
				{
					LOGGER.info("ATTENTION: admin command " + command + " has not an access right");
				}
			}
			
		}
		
	}
	
	public void registerAdminCommandHandler(IAdminCommandHandler handler)
	{
		String[] commands = handler.getAdminCommandList();
		
		for (String command : commands)
		{
			if (Config.DEBUG)
			{
				LOGGER.info("Adding handler for command " + command);
			}
			
			if (adminCommands.containsKey(command))
			{
				LOGGER.warn("Duplicated command \"" + command + "\" defined in " + handler.getClass().getName() + ".");
			}
			else
			{
				adminCommands.put(command, handler);
			}
		}
	}
	
	public IAdminCommandHandler getAdminCommandHandler(String adminCommand)
	{
		String command = adminCommand;
		
		if (adminCommand.indexOf(" ") != -1)
		{
			command = adminCommand.substring(0, adminCommand.indexOf(" "));
		}
		
		if (Config.DEBUG)
		{
			LOGGER.info("getting handler for command: " + command + " -> " + (adminCommands.get(command) != null));
		}
		
		return adminCommands.get(command);
	}
}