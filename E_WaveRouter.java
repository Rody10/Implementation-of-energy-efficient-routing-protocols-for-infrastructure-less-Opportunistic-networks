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

import routing.util.EnergyModel;
import routing.util.RoutingInfo;
import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Epidemic-like message router making waves of messages.
 * Work in progress.
 */

public class E_WaveRouter extends ActiveRouter{
	
	/** 
	 * Immunity time -setting id ({@value}). Defines how long time a node
	 * will reject incoming messages it has already received 
	 */
	
	public Map<String, Integer> delivered; 
	
	private void initDelivered()  
	{
		this.delivered = new HashMap<>(200);
	}
	
	private static double battery_level_threshold; 
	
	public static final String IMMUNITY_S = "immunityTime";
	/** 
	 * Custody fraction -setting id ({@value}). Defines how long (compared to
	 * immunity time) nodes accept custody for new incoming messages. 
	 */
	public static final String CUSTODY_S = "custodyFraction";
	private double immunityTime;
	private double custodyFraction;
	/** map of recently received messages and their receive times */
	private Map<String, Double> recentMessages;	
	/** IDs of the messages this host has custody for */
	private Map<String, Double> custodyMessages;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public E_WaveRouter(Settings s) {
		super(s);
		this.immunityTime = s.getDouble(IMMUNITY_S);
		this.custodyFraction = s.getDouble(CUSTODY_S);
		battery_level_threshold = s.getInt("E_WaveRouter.battery_level_threshold");
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected E_WaveRouter(E_WaveRouter r) {
		super(r);
		recentMessages = new HashMap<String, Double>();
		this.immunityTime = r.immunityTime;
		this.custodyFraction = r.custodyFraction;
		this.custodyMessages = new HashMap<String, Double>();
		initDelivered(); ///
	}

	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		Double lastTime = this.recentMessages.get(m.getId());
			
		if (lastTime != null) {
			if (lastTime + this.immunityTime > SimClock.getTime()) {
				return DENIED_POLICY; /* still immune to the message */
			} else {
				/* immunity has passed; remove from recent */
				this.recentMessages.remove(m.getId()); 
			}
		}

		/* no last time or immunity passed; receive based on other checks */
		return super.checkReceiving(m, from);
	}
	
	/**
	 * Returns the oldest message that has been already sent forward 
	 */
	@Override
	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message oldest = null;
		
		for (Message m : messages) {
			Double custodyStartTime = this.custodyMessages.get(m.getId());
			if (custodyStartTime != null) {
				if (SimClock.getTime() > 
					custodyStartTime + immunityTime * custodyFraction) {
					this.custodyMessages.remove(m.getId()); /* time passed */
				} else {
					continue; /* skip messages that still have custody */					
				}
			}
				
			
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; /* skip the message(s) that router is sending */
			}
			
			if (oldest == null ) {
				oldest = m;
			}
			else if (oldest.getReceiveTime() > m.getReceiveTime()) {
				oldest = m;
			}
		}
		
		return oldest;
	}
	
	@Override
	public void update() {
		super.update();
		
		if (isTransferring() || !canStartTransfer()) {
			return; /* transferring, don't try other connections yet */
		}
		
		/* Try first the messages that can be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return; 
		}		
		this.tryOtherMessages();
	}
	
	
	private Tuple<Message, Connection> tryOtherMessages(){
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = getMessageCollection();
		
		Collection<Message> msg_to_be_deleted = new HashSet<Message>();
		
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			E_WaveRouter othRouter = (E_WaveRouter) other.getRouter();
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
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		/* store received message IDs for immunity */
		this.recentMessages.put(m.getId(), new Double(SimClock.getTime()));
		this.custodyMessages.put(id, SimClock.getTime());
		return m;
	}
	
	@Override
	protected void transferDone(Connection con) { 
		/* remove from custody messages (if it was there) */
		this.custodyMessages.remove(con.getMessage().getId()); 
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = super.getRoutingInfo();
		RoutingInfo immunity = new RoutingInfo("Immune to " + 
				this.recentMessages.size() + " messages");
		
		for (String id : recentMessages.keySet()) {
			RoutingInfo m = new RoutingInfo(id + " until " + 
					String.format("%.2f", 
							recentMessages.get(id) + this.immunityTime));
			immunity.addMoreInfo(m);
		}		
		ri.addMoreInfo(immunity);
		
		return ri;
	}
	
	@Override
	public E_WaveRouter replicate() {
		return new E_WaveRouter(this);
	}


}
