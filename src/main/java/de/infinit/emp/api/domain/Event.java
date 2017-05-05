package de.infinit.emp.api.domain;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "EVENT")
public class Event {
	@DatabaseField(generatedId = true)
	UUID uuid;

	@DatabaseField(canBeNull = false, dataType = DataType.DATE_LONG)
	Date expiresAt;

	@NotNull
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private Sensor sensor;

	@NotNull
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private Session session;

	public Event() {
		// ORMLite needs a no-arg constructor
	}

	public Event(Session session, Sensor sensor, Date expiresAt) {
		this.session = session;
		this.sensor = sensor;
		this.expiresAt = expiresAt;
	}

	public UUID getUuid() {
		return uuid;
	}

	public void setExpiresAt(Date expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Sensor getSensor() {
		return sensor;
	}

	public boolean isExpired() {
		return this.expiresAt.toInstant().isBefore(Instant.now());
	}
}
