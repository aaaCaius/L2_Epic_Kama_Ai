package com.l2jfrozen.gameserver.network.clientpackets;

import com.l2jfrozen.gameserver.model.actor.instance.L2HennaInstance;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.network.SystemMessageId;

/**
 * format cd
 */
public final class RequestHennaRemove extends L2GameClientPacket
{
	private int _symbolId;
	
	@Override
	protected void readImpl()
	{
		_symbolId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		for (int i = 1; i <= 3; i++)
		{
			L2HennaInstance henna = activeChar.getHennas(i);
			
			if (henna != null && henna.getSymbolId() == _symbolId)
			{
				if (activeChar.getAdena() >= (henna.getCancelFee()))
				{
					activeChar.getInventory().reduceAdena("Henna", henna.getCancelFee(), activeChar, activeChar.getLastFolkNPC());
					activeChar.removeHenna(i);
					break;
				}
				activeChar.sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			}
		}
	}

	@Override
	public String getType()
	{
		return null;
	}

}