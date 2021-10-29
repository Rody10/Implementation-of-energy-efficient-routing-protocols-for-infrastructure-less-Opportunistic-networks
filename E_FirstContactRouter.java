/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 * 
 * Modified by Rodney Tholanah, 2021
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map; ///
import java.util.Set;

import routing.util.EnergyModel; ///
import util.Tuple;

/**
 * First contact router which uses only a single copy of the message 
 * (or fragments) and forwards it to the first available contact.
 */
public class E_FirstContactRouter extends ActiveRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public Map<String, Integer> delivered; 
	private static double battery_level_threshold; 
	
	static 
	{
		Settings s = new Settings(); 
		battery_level_threshold = s.getInt("E_FirstContactRouter.battery_level_threshold"); 
	}
	
	
	public E_FirstContactRouter(Settings s) {
		super(s);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected E_FirstContactRouter(E_FirstContactRouter r) {
		super(r);
	}
	
	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		int recvCheck = super.checkReceiving(m, from); 
		
		
		if (recvCheck == RCV_OK) { //\i.e ==0
			/* don't accept a message that has already traversed this node */
			if (m.getHops().contains(getHost())) {  
				recvCheck = DENIED_OLD;
			}
		}
		
		return recvCheck;
	}
			
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; 
		}		
			
		if (exchangeDeliverableMessages() != null) {
			return; 
		}
		
		tryOtherMessages();
	}
	
	private Tuple<Message, Connection> tryOtherMessages(){
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = getMessageCollection();
		
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			E_FirstContactRouter othRouter = (E_FirstContactRouter) other.getRouter();
			if (othRouter.isTransferring())
			{
				continue;
			}
			// obtain neighbour node's energy value
			double nn_energy = (double) othRouter.getHost().getComBus().getProperty(EnergyModel.ENERGY_VALUE_ID);
			// go through all messages in current node's buffer
			for (Message m : msgCollection)
			{
				
				DTNHost dest = m.getTo();
				
				//check if neighbour node's energy value is less than 
				//minimum energy threshold and not the destination node
				if (nn_energy < this.battery_level_threshold && !dest.equals(other))
				{
					continue;
				}
				if(dest.equals(other))
				{
					messages.add(new Tuple<Message, Connection>(m, con));
				}
				
				else
				{
					messages.add(new Tuple<Message, Connection>(m, con)); 
				}										
			}				
		}
		return tryMessagesForConnected(messages);
	}
	
		
	
	
	
	@Override
	protected void transferDone(Connection con) {
		/* don't leave a copy for the sender */
		this.deleteMessage(con.getMessage().getId(), false);
	}
		
	@Override
	public E_FirstContactRouter replicate() {
		return new E_FirstContactRouter(this);
	}

}