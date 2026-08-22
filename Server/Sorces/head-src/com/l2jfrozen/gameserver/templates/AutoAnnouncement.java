package com.l2jfrozen.gameserver.templates;

import java.util.concurrent.ScheduledFuture;

import com.l2jfrozen.gameserver.model.entity.Announcements;
import com.l2jfrozen.gameserver.thread.ThreadPoolManager;

/**
 * @author ReynalDev
 */
public class AutoAnnouncement
{
	public static final int DEFAULT_ANNOUNCEMENT_DELAY = 180000; // 30 minutes
	private int id;
	private String text;
	private long delay; // time in miliseconds
	
	private ScheduledFuture<?> announceTask;
	
	public AutoAnnouncement(int id, String text, long delay)
	{
		this.id = id;
		this.text = text;
		this.delay = delay;
		
		announceTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(() -> 
		{
			Announcements.getInstance().announceToAll(text);
			
		}, delay, delay);
	}

	public int getId()
	{
		return id;
	}
	
	public String getText()
	{
		return text;
	}
	
	public long getDelay()
	{
		return delay;
	}
	
	public ScheduledFuture<?> getTask()
	{
		return announceTask;
	}
}
