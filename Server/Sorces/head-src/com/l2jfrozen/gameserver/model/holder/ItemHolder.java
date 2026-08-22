package com.l2jfrozen.gameserver.model.holder;

import com.l2jfrozen.gameserver.datatables.sql.ItemTable;
import com.l2jfrozen.gameserver.templates.L2Item;

/**
 * @author ReynalDev
 */
public class ItemHolder
{
	private int itemId;
	private int itemAmount;
	
	public ItemHolder(int itemId, int itemAmount)
	{
		this.itemId = itemId;
		this.itemAmount = itemAmount;
	}
	
	public void setItemId(int id)
	{
		itemId = id;
	}
	
	public int getItemId()
	{
		return itemId;
	}
	
	public void setItemAmount(int amount)
	{
		itemAmount = amount;
	}
	
	public int getItemAmount()
	{
		return itemAmount;
	}
	
	public L2Item getItemTemplate()
	{
		return ItemTable.getInstance().getTemplate(itemId);
	}
}
