package de.infinit.emp.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

import javax.validation.constraints.NotNull;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;

public class State {
	@DatabaseField(generatedId = true)
	int id;

	@NotNull
	@DatabaseField
	long recvTime;

	@NotNull
	@ForeignCollectionField(orderColumnName = "index", orderAscending = true)
	Collection<Value> values;

	@NotNull
	@DatabaseField(defaultValue = "false")
	boolean eventSent;

	@NotNull
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "sensor_id")
	private Sensor sensor;

	public State() {
		// ORMLite needs a no-arg constructor
		this.recvTime = Instant.now().getEpochSecond();
		this.values = new ArrayList<>();
		this.eventSent = false;
	}

	public State(Sensor sensor) {
		this();
		this.sensor = sensor;
	}

	public Collection<Value> getValues() {
		return values;
	}

	public void setValues(Collection<Value> values) {
		this.values = values;
	}

	public boolean isEventSent() {
		return eventSent;
	}

	public void setEventSent(boolean eventSent) {
		this.eventSent = eventSent;
	}
}
