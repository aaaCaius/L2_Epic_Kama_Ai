package com.l2jfrozen.gameserver.handler.usercommandhandlers;

import com.l2jfrozen.gameserver.handler.IUserCommandHandler;
import com.l2jfrozen.gameserver.model.L2CommandChannel;
import com.l2jfrozen.gameserver.model.L2Party;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.network.SystemMessageId;
import com.l2jfrozen.gameserver.network.serverpackets.ExOpenMPCC;
import com.l2jfrozen.gameserver.network.serverpackets.SystemMessage;

/**
 * @author Chris
 * @author ReynalDev
 */
public class ChannelLeave implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		96
	};
	
	@Override
	public boolean useUserCommand(int id, L2PcInstance activeChar)
	{
		if (id != COMMAND_IDS[0])
		{
			return false;
		}
		
		if (activeChar == null)
		{
			return false;
		}
		
		if (activeChar.isInParty())
		{
			if (activeChar.getParty().isLeader(activeChar) && activeChar.getParty().isInCommandChannel())
			{
				L2CommandChannel channel = activeChar.getParty().getCommandChannel();
				L2Party party = activeChar.getParty();
				channel.removeParty(party);
				
				party.getLeader().sendPacket(new SystemMessage(SystemMessageId.LEFT_COMMAND_CHANNEL));
				channel.broadcastToChannelMembers(new SystemMessage(SystemMessageId.S1_PARTY_LEFT_COMMAND_CHANNEL).addString(party.getLeader().getName()));
				
				if(activeChar == channel.getChannelLeader())
				{
					if(channel.getPartys().size() >=  2)
					{
						L2PcInstance newCCLeader = channel.getPartys().get(0).getLeader();
						channel.setChannelLeader(newCCLeader);
						SystemMessage msg = new SystemMessage(SystemMessageId.COMMAND_CHANNEL_LEADER_NOW_S1);
						msg.addString(newCCLeader.getName());
						channel.broadcastToChannelMembers(msg);
						channel.broadcastToChannelMembers(new ExOpenMPCC());
					}
				}
				
				return true;
			}
		}
		return false;
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
