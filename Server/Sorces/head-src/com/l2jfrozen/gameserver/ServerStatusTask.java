package com.l2jfrozen.gameserver;

import java.util.concurrent.ScheduledFuture;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.model.L2World;
import com.l2jfrozen.gameserver.thread.ThreadPoolManager;
import com.l2jfrozen.util.Memory;
import com.l2jfrozen.util.Util;

/**
 * @author  Nefer
 * @author ReynalDev
 */
public class ServerStatusTask
{
	protected static final Logger LOGGER = Logger.getLogger(ServerStatusTask.class);
	protected ScheduledFuture<?> scheduledTask;
	
	protected ServerStatusTask()
	{
		scheduledTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(() -> {
			Util.printSection("Server Status");
			LOGGER.info("Players Online: " + L2World.getAllPlayersCount());
			LOGGER.info("Threads: " + Thread.activeCount());
			LOGGER.info("Free memory: " + Memory.getFreeMemory() + " MB of " + Memory.getTotalMemory() + " MB");
			LOGGER.info("Used memory: " + Memory.getUsedMemory() + " MB of " + Memory.getTotalMemory() + " MB");
			Util.printSection("Server Status");
		}, 3600000, 3600000);
	}
	
	public static ServerStatusTask getInstance()
	{
		return SingletonHolder.instance;
	}
	
	private static class SingletonHolder
	{
		protected static final ServerStatusTask instance = new ServerStatusTask();
	}
}