package com.l2jfrozen.gameserver.model.holder;

import com.l2jfrozen.gameserver.datatables.SkillTable;
import com.l2jfrozen.gameserver.model.L2Skill;

/**
 * @author ReynalDev
 */
public class NewbieGuideBuffHolder
{
	int skillId;
	int skillLevel;
	int minLevel;
	int maxLevel;
	boolean giveToMage;
	boolean giveToWarrior;
	
	public NewbieGuideBuffHolder(int skillId, int skillLevel, int minLevel, int maxLevel, boolean giveToMage, boolean giveToWarrior)
	{
		this.skillId = skillId;
		this.skillLevel = skillLevel;
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.giveToMage = giveToMage;
		this.giveToWarrior = giveToWarrior;
	}
	
	public int getMinLevel()
	{
		return minLevel;
	}
	
	public int getMaxLevel()
	{
		return maxLevel;
	}
	
	public boolean isForMage()
	{
		return giveToMage;
	}
	
	public boolean isForWarrior()
	{
		return giveToWarrior;
	}
	
	public L2Skill getSkill()
	{
		return SkillTable.getInstance().getInfo(skillId, skillLevel);
	}
	
}
