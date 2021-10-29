/* 
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 * 
 * Modified by Rodney Tholanah, 2021
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import core.DTNHost;
import core.Message;
import core.Settings;
import routing.util.EnergyModel;
import util.Tuple;
import core.Connection;

/**
 * Router module mimicking the game-of-life behavior
 */
public class E_LifeRouter extends ActiveRouter {
	
	/** 
	 * Neighboring message count -setting id ({@value}). Two comma
	 * separated values: min and max. Only if the amount of connected nodes
	 * with the given message is between the min and max value, the message
	 * is accepted for transfer and kept in the buffer. 
	 */
	
	public Map<String, Integer> delivered; 
	
	private void initDelivered()  
	{
		this.delivered = new HashMap<>(200);
	}
	
	private static double battery_level_threshold; 
	
	
	public static final String NM_COUNT_S = "nmcount";
	private int countRange[];
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public E_LifeRouter(Settings s) {
		super(s);
		countRange = s.getCsvInts(NM_COUNT_S, 2);
		battery_level_threshold = s.getInt("E_LifeRouter.battery_level_threshold"); 
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected E_LifeRouter(E_LifeRouter r) {
		super(r);
		this.countRange = r.countRange;
		initDelivered(); ///
	}

	/**
	 * Counts how many of the connected peers have the given message
	 * @param m The message to check
	 * @return Amount of connected peers with the message
	 */
	private int getPeerMessageCount(Message m) {
		DTNHost me = getHost();
		String id = m.getId();
		int peerMsgCount = 0;
		
		for (Connection c : getConnections()) {
			if (c.getOtherNode(me).getRouter().hasMessage(id)) {
				peerMsgCount++;
			}	
		}
		
		return peerMsgCount;
	}
	
	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		int peerMsgCount = getPeerMessageCount(m);
		
		if (peerMsgCount < this.countRange[0] || 
				peerMsgCount > this.countRange[1]) {
			return DENIED_POLICY;
		}
		
		/* peer message count check OK; receive based on other checks */
		return super.checkReceiving(m, from);
	}
	
	@Override
	public void update() {
		int peerMsgCount;
		Vector<String> messagesToDelete = new Vector<String>();
		super.update();
		
		if (isTransferring() || !canStartTransfer()) {
			return; /* transferring, don't try other connections yet */
		}
		
		/* Try first the messages that can be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return; 
		}	
		this.tryOtherMessages();
		
		/* see if need to drop some messages... */
		for (Message m : getMessageCollection()) {
			peerMsgCount = getPeerMessageCount(m);
			if (peerMsgCount < this.countRange[0] || 
					peerMsgCount > this.countRange[1]) {
				messagesToDelete.add(m.getId());				
			}
		}		
		for (String id : messagesToDelete) { /* ...and drop them */
			this.deleteMessage(id, true);
		}
		
	}
	
	
	
	
	private Tuple<Message, Connection> tryOtherMessages(){
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = getMessageCollection(); 
		
		Collection<Message> msg_to_be_deleted = new HashSet<Message>();
		
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			E_LifeRouter othRouter = (E_LifeRouter) other.getRouter();
			if (othRouter.isTransferring())
			{
				continue;
			}
			// obtain neighbour node's energy value
			double nn_energy = (double) othRouter.getHost().getComBus().getProperty(EnergyModel.ENERGY_VALUE_ID); 
			// go through all messages in current node's buffer
			for (Message m : msgCollection)
			{
				if(othRouter.hasMessage(m.getId())) 
				{
					continue;
				}
				DTNHost dest = m.getTo();
				
				//check if neighbour node's energy value is less than 
				//minimum energy threshold and not the destination node
				String key = m.getId ()+"<−>"+m. getFrom().toString()+"<−>"+dest.toString();
				
				if(othRouter.delivered.containsKey(key))
				{
					int cnt = (int)othRouter.delivered.get(key);
					this.delivered.put(key, ++cnt); 
					msg_to_be_deleted.add(m); 
					continue; 
					
				}		
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
	public int receiveMessage(Message m, DTNHost from) 
	{
		//-1 means the message is an acknowledgement message
		if (m.getSize() == -1)
		{
			String ack_m = m.getId();
			this.delivered.put(ack_m,1); 
			String[] parts = ack_m.split("<−>");
			String m_Id = parts[0];
			//delete the delivered message from the buffer
			this.deleteMessage(m_Id,false);
			return 0;
		}
		
		
		int i = super.receiveMessage (m, from ) ;
		
		if(m.getTo().equals(this.getHost()) && i ==RCV_OK)
		{
			String ack_m = m.getId()+"<−>"+m.getFrom().toString()+"<−>"+m.getTo().toString();
			//message with with size -1 is created indicating that it is an 
			//acknowledgement message
			Message ack_mes = new Message ( this.getHost(),from,ack_m,-1);
			//last sending node is is send the acknowledgement message
			from.receiveMessage(ack_mes,this.getHost());
			this.delivered.put(ack_m,1);
		}
		return i;
		
	}
	
	
	
	

	@Override
	public E_LifeRouter replicate() {
		return new E_LifeRouter(this);
	}

}