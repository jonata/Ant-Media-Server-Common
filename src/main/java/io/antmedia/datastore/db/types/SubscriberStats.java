package io.antmedia.datastore.db.types;

import java.util.ArrayList;
import java.util.List;

import dev.morphia.annotations.Embedded;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="SubscriberStats", description="Statistics for each subsciber to the stream")
public class SubscriberStats {
	
	/**
	 * subscriber id to which this statistic belongs to
	 */
	@ApiModelProperty(value = "the subscriber id of the subscriber")
	private String subscriberId;
	
	/**
	 * related streamId with subscriber
	 */
	@ApiModelProperty(value = "the stream id of the token")
	private String streamId;
	
	/**
	 * connection events happened for this subscriber
	 */
	@ApiModelProperty(value = "list of connection events")
	@Embedded
	private List<ConnectionEvent> connectionEvents = new ArrayList<>(); 
	
	public String getSubscriberId() {
		return subscriberId;
	}

	public void setSubscriberId(String subscriberId) {
		this.subscriberId = subscriberId;
	}
	
	public String getStreamId() {
		return streamId;
	}

	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}

	public List<ConnectionEvent> getConnectionEvents() {
		return connectionEvents;
	}

	public void setConnectionEvents(List<ConnectionEvent> connectionEvents) {
		this.connectionEvents = connectionEvents;
	}
	
	public void addConnectionEvent(ConnectionEvent connectionEvent) {
		connectionEvents.add(connectionEvent);
	}
	
	
}
