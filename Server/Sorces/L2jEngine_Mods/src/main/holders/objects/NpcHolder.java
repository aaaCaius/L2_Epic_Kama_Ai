package main.holders.objects;

import com.l2jfrozen.gameserver.model.actor.instance.L2NpcInstance;

import main.enums.ChampionType;

/**
 * @author fissban
 */
public class NpcHolder extends CharacterHolder
{
	public NpcHolder(L2NpcInstance npc)
	{
		super(npc);
	}
	
	/**
	 * Obtain the Npc instance
	 * @return -> Npc
	 */
	@Override
	public L2NpcInstance getInstance()
	{
		return (L2NpcInstance) super.getInstance();
	}
	
	public int getId()
	{
		return getInstance().getNpcId();
	}
	
	// XXX CHAMPIONS ---------------------------------------------------------------------------------------------------
	
	private ChampionType championType = ChampionType.NONE;
	
	public boolean isChampion()
	{
		return championType != ChampionType.NONE;
	}
	
	public void setChampionType(ChampionType championType)
	{
		this.championType = championType;
	}
	
	public ChampionType getChampionType()
	{
		return championType;
	}
}
