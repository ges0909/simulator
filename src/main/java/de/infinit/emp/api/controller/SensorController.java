package de.infinit.emp.api.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import de.infinit.emp.ApplicationConfig;
import de.infinit.emp.Status;
import de.infinit.emp.api.domain.Capability;
import de.infinit.emp.api.domain.OldState;
import de.infinit.emp.api.domain.OldValue;
import de.infinit.emp.api.domain.Sensor;
import de.infinit.emp.api.domain.Session;
import de.infinit.emp.api.domain.State;
import de.infinit.emp.api.domain.Tag;
import de.infinit.emp.api.domain.TagSensor;
import de.infinit.emp.api.domain.User;
import de.infinit.emp.api.domain.Value;
import de.infinit.emp.api.model.CapabilityModel;
import de.infinit.emp.api.model.EventModel;
import de.infinit.emp.api.model.SensorModel;
import de.infinit.emp.api.model.SessionModel;
import de.infinit.emp.api.model.StateModel;
import de.infinit.emp.api.model.TagModel;
import de.infinit.emp.api.model.TagSensorModel;
import de.infinit.emp.api.model.UserModel;
import de.infinit.emp.api.model.ValueModel;
import spark.Request;
import spark.Response;

public class SensorController extends Controller {
	private static SensorController instance = null;
	static final ApplicationConfig config = ConfigCache.getOrCreate(ApplicationConfig.class);
	final SensorModel sensorModel = SensorModel.instance();
	final CapabilityModel capabilityModel = CapabilityModel.instance();
	final SessionModel sessionModel = SessionModel.instance();
	final UserModel userModel = UserModel.instance();
	final TagModel tagModel = TagModel.instance();
	final EventModel eventModel = EventModel.instance();
	final TagSensorModel tagSensorModel = TagSensorModel.instance();
	final ValueModel valueModel = ValueModel.instance();

	private SensorController() {
		super();
	}

	public static SensorController instance() {
		if (instance == null) {
			instance = new SensorController();
		}
		return instance;
	}

	class CreateOrUpdateSensorRequest {
		@Expose
		String code;
		@Expose
		String description;
	}

	class GetSensorResponse {
		class Capability {
			@Expose
			List<String> data;
			@Expose
			List<String> action;
		}

		class State {
			@Expose
			List<Long> data;
			@Expose
			List<Object> action;
		}

		@Expose
		UUID owner;
		@Expose
		long time;
		@Expose
		String description;
		@Expose
		String sdevice;
		@Expose
		String model;
		@Expose
		@SerializedName("recv_interval")
		int recvInterval;
		@Expose
		@SerializedName("recv_time")
		long recvTime;
		@Expose
		@SerializedName("battery_ok")
		boolean batteryOk;
		@Expose
		Capability capabilities;
		@Expose
		State state;
	}

	private String generateHexId(int length) {
		Random random = new Random();
		StringBuilder sb = new StringBuilder();
		while (sb.length() < length) {
			sb.append(Integer.toHexString(random.nextInt(0xff)));
		}
		return sb.toString();
	}

	public Sensor createSensor(User user, String code, String description) {
		// create sensor
		Sensor sensor = new Sensor();
		sensor.setOwner(user);
		sensor.setCode(code);
		sensor.setSdevice(generateHexId(8));
		sensor.setDescription(description);
		sensor.setRecvInterval(config.recvInterval());
		sensor.setBatteryOk(true);
		State state = new State(sensor);
		state.setRecvTime(Instant.now().getEpochSecond());
		StateModel.instance().create(state);
		sensor.setState(state);
		if (sensorModel.create(sensor) == null) {
			return null;
		}

		// create capabilities, e.g. for 'EnergyCam'
		Collection<Capability> capabilites = sensor.getCapabilities();

		Capability c = new Capability(sensor, 1, "binary_8bit", null);
		capabilityModel.create(c);
		capabilites.add(c);

		c = new Capability(sensor, 2, "binary_32bit", config.defaultDelta());
		capabilityModel.create(c);
		capabilites.add(c);

		c = new Capability(sensor, 3, "binary_16bit", null);
		capabilityModel.create(c);
		capabilites.add(c);

		c = new Capability(sensor, 4, "binary_32bit", null);
		capabilityModel.create(c);
		capabilites.add(c);

		// .. values
		Collection<Value> values = state.getValues();

		Value v = new Value(state, 1, 1L);
		valueModel.create(v);
		values.add(v);

		v = new Value(state, 2, 0L);
		valueModel.create(v);
		values.add(v);

		v = new Value(state, 3, 0L);
		valueModel.create(v);
		values.add(v);

		v = new Value(state, 4, 2214814041L);
		valueModel.create(v);
		values.add(v);

		// tag sensor with user's 'tag_all' tag
		Tag tag = user.getTagAll();
		TagSensor tagSensor = new TagSensor(tag, sensor);
		tagSensorModel.create(tagSensor);

		//
		sensor.startSimulation();
		return sensor;
	}

	public Object createSensor(Request request, Response response) {
		Session session = request.session().attribute(SessionController.SESSION);
		User user = session.getUser();
		if (user == null) {
			return status(Status.NO_AUTH);
		}
		CreateOrUpdateSensorRequest req = decode(request.body(), CreateOrUpdateSensorRequest.class);
		if (req.code == null || !Pattern.matches(config.devicePattern(), req.code)) {
			return status(Status.WRONG_CODE);
		}
		if (sensorModel.findFirstByColumn("code", req.code) != null) {
			return status(Status.DUPLICATE_CODE);
		}
		Sensor sensor = createSensor(user, req.code, req.description);
		if (sensor == null) {
			return fail();
		}
		return result("uuid", sensor.getUuid().toString());
	}

	public Object updateSensor(Request request, Response response) {
		Session session = request.session().attribute(SessionController.SESSION);
		User user = session.getUser();
		if (user == null) {
			return status(Status.NO_AUTH);
		}
		if (request.params(":uuid") == null) {
			return status(Status.WRONG_SENSOR);
		}
		UUID uuid = UUID.fromString(request.params(":uuid"));
		Sensor sensor = sensorModel.queryForId(uuid);
		if (sensor == null) {
			return status(Status.WRONG_SENSOR);
		}
		User owner = sensor.getOwner();
		if (!owner.equals(user)) {
			return status(Status.WRONG_USER);
		}
		CreateOrUpdateSensorRequest req = decode(request.body(), CreateOrUpdateSensorRequest.class);
		if (req.code != null) {
			if (!Pattern.matches(config.devicePattern(), req.code)) {
				return status(Status.WRONG_CODE);
			}
			if (sensorModel.findFirstByColumn("code", req.code) != null) {
				return status(Status.DUPLICATE_SENSOR);
			}
			sensor.setCode(req.code);
		}
		sensor.setDescription(req.description);
		if (sensorModel.update(sensor) == null) {
			return fail();
		}
		return ok();
	}

	public Object getSensor(Request request, Response response) {
		Session session = request.session().attribute(SessionController.SESSION);
		User user = session.getUser();
		if (user == null) {
			return status(Status.NO_AUTH);
		}
		if (request.params(":uuid") == null) {
			return status(Status.WRONG_SENSOR);
		}
		UUID uuid = UUID.fromString(request.params(":uuid"));
		Sensor sensor = sensorModel.queryForId(uuid);
		if (sensor == null) {
			return fail();
		}
		User owner = sensor.getOwner();
		if (!owner.equals(user)) {
			return status(Status.WRONG_USER);
		}
		GetSensorResponse res = convert(sensor, GetSensorResponse.class);
		res.owner = owner.getUuid();
		res.time = Instant.now().getEpochSecond();
		res.recvTime = sensor.getState().getRecvTime();
		//
		res.capabilities = res.new Capability();
		res.capabilities.data = sensor.getCapabilities().stream().map(Capability::getName).collect(Collectors.toList());
		res.capabilities.action = new ArrayList<>();
		//
		res.state = res.new State();
		res.state.data = sensor.getState().getValues().stream().map(Value::getValue).collect(Collectors.toList());
		res.state.action = new ArrayList<>();
		return result("sensor", res);
	}

	public Object deleteSensor(Request request, Response response) {
		Session session = request.session().attribute(SessionController.SESSION);
		User user = session.getUser();
		if (user == null) {
			return status(Status.NO_AUTH);
		}
		if (request.params(":uuid") == null) {
			return status(Status.WRONG_SENSOR);
		}
		UUID uuid = UUID.fromString(request.params(":uuid"));
		Sensor sensor = sensorModel.queryForId(uuid);
		if (sensor == null) {
			return status(Status.WRONG_SENSOR);
		}
		User owner = sensor.getOwner();
		if (!owner.equals(user)) {
			return status(Status.WRONG_USER);
		}
		// stop background simulation
		sensor.stopSimulation();
		// before sensor is deleted remove it from session event list
		if (!EventController.instance().unsubscribe(session, sensor)) {
			return fail();
		}
		// delete sensor
		if (sensorModel.delete(uuid) != 1) {
			return fail();
		}
		return ok();
	}

	public Object getSensorData(Request request, Response response) {
		Session session = request.session().attribute(SessionController.SESSION);
		User user = session.getUser();
		if (user == null) {
			return status(Status.NO_AUTH);
		}
		if (request.params(":uuid") == null) {
			return status(Status.WRONG_SENSOR);
		}
		UUID uuid = UUID.fromString(request.params(":uuid"));
		Sensor sensor = sensorModel.queryForId(uuid);
		if (sensor == null) {
			return status(Status.WRONG_SENSOR);
		}
		User owner = sensor.getOwner();
		if (!owner.equals(user)) {
			return status(Status.WRONG_USER);
		}
		Map<String, Object> data = new HashMap<>();
		for (OldState oldState : sensor.getOldStates()) {
			String recvTime = Long.toString(oldState.getRecvTime()) + "000";
			List<Long> values = oldState.getOldValues().stream().map(OldValue::getValue).collect(Collectors.toList());
			data.put(recvTime, values);
		}
		return result("data", data);
	}
}
