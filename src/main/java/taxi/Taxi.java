package taxi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.measure.unit.SI;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.MoveProgress;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import gradient.FieldEmitter;
import gradient.GradientModel;
import taxi.TaxiSimulator.GasStation;
import utils.GraphUtils;

public class Taxi extends Vehicle implements CommUser, FieldEmitter {
	private static int _idGen = 0;
	public int _id = 0;
	
	static final double SPEED = 4000d;								// 60km/h 
	private static final double FUEL_EFFICIENCY = 0.006d;			// 6l/100km = 6000ml/1000000dm = 0.006ml/dm
	public static final double FUEL_CAPACITY = 40000d;				// 40l (40000ml)
	public static final double REFUEL_THRESHOLD = 5000d;			// At 5l (5000ml) gas left, switch from gradient descent to refueling.
	// Always try to make sure we don't go below this amount of fuel, to account for variance in consumption
	// and to make sure that not too much fuel is used while idling and waiting for an offer to be accepted (100ml).
	public static final double REFUEL_BUFFER = 1000d;
	public static final double REFUEL_RATE = 5d/6d;					// 50l/minute = 50000ml/60000ms = 5/6 ml/ms
	
	private static final double RANGE = 500000d;					// Comm range, 50km .
	private static final double RELIABILITY = 1d;

	// A linear rate at which the strength of the field diminishes over distance.
	private static final double FIELD_DIMINISH_RATE = 0.05;
	private static final double FIELD_STRENGTH_MULTIPLIER = 2000d;	// A scalar for the strength of a field.
	private static final double FIELD_STRENGTH_PADDING = 50d;
	private static final double REDISTRIBUTION_THRESHOLD = 4000d;	// The resultant field must be at least this strong to force a move.
	// When 0, the field strength can go all the way to 0 at a peak distribution, this circumvents that.
	public static final double DENSITY_EXPONENTIAL_SCALE = 3;		// Map densities using an exponential.

	private CommDevice _comm;
	private GradientModel _gm;
	private List<Customer> _queue = new ArrayList<Customer>();
	private int _queuePosition = 0;			// Keep track of whether an accept message still matches the offer it was made for.
	private List<Point> _path = new ArrayList<Point>();
	private TaxiState _state = TaxiState.IDLE;
	private double _fuelGauge = FUEL_CAPACITY;
	public double _totalFuelUsed = 0;
	private Point _nearestStationPos;
	private Point _redistributionPoint;
	private RealVector _redistributionVector;

	/**
	 * Constructor
	 * @param startPosition The starting position.
	 * @param capacity The amount of passengers that can be taken.
	 */
	Taxi(Point startPosition, int capacity) {
		super(VehicleDTO.builder().capacity(capacity).startPosition(startPosition).speed(SPEED).build());
		_id = _idGen++;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		if (!time.hasTimeLeft())
			return;
		
		List<Message> messages = new ArrayList<Message>(_comm.getUnreadMessages());

		// Check for requests and accept messages.
		for (Iterator<Message> it = messages.iterator(); it.hasNext();) {
			Message message = it.next();
			MessageContents contents = message.getContents();
			// If we get an accept message, add the customer to the queue and start moving.
			if (contents instanceof Messages.AcceptMessage) {
				it.remove();
				handleAcceptRequest(message);
			}
			// Handle a request for pickup by sending an offer, equal to the distance that has to be traveled before reaching the customer.
			if (contents instanceof Messages.RequestMessage) {
				it.remove();
				sendOffer(message);
			}
		}
		if (_state == TaxiState.PICKING_UP)
			performPickup(time);
		if (_state == TaxiState.DELIVERING)
			performDelivery(time);
		if (_state == TaxiState.IDLE) {
			// Go to a gas station if low on fuel.
			while(_fuelGauge < REFUEL_THRESHOLD && time.hasTimeLeft()) {
				moveTo(_nearestStationPos, time);
				if (_nearestStationPos.equals(this.getPosition().get())) {
					_state = TaxiState.REFUELING;
					break;
				}
			}
			// Distribute along gradients if nothing else.
			moveToDistribution(time);
		}
		if (_state == TaxiState.REFUELING) {
			// Use ceil and min to ensure that the gas tank is actually filled at some point (might not because of floating point errors.
			long useTime = (long)Math.ceil((FUEL_CAPACITY-_fuelGauge)*REFUEL_RATE);
			if (useTime > time.getTimeLeft())
				useTime = time.getTimeLeft();
			time.consume(useTime);
			_fuelGauge = Math.min(FUEL_CAPACITY, _fuelGauge + useTime/REFUEL_RATE+1);
			if (_fuelGauge == FUEL_CAPACITY)
				_state = TaxiState.IDLE;
		}
	}

	private void moveToDistribution(TimeLapse time) {
		// TODO: recompute fields less.
		_redistributionVector = _gm.getResultantField(this);
		if (_redistributionVector.getNorm() > REDISTRIBUTION_THRESHOLD) {
			while (time.hasTimeLeft()) {
				Point p = getPosition().get();
				if (p.equals(_redistributionPoint) || _redistributionPoint == null) {
					Point newPoint = GraphUtils.getClosestNode(new Point(p.x+_redistributionVector.getEntry(0), p.y+_redistributionVector.getEntry(1)));
//					Point newPoint = GraphUtils.getSmallestAngle(p, _redistributionVector);
					// Don't do anything if being pushed to the same point.
					if (newPoint.equals(_redistributionPoint))
						break;
					_redistributionPoint = newPoint;
				}
				moveTo(_redistributionPoint, time);
			}
		}
	}
	
	public RealVector getRedustributionVector() {
		return _redistributionVector;
	}

	/**
	 * Perform steps needed to pick up a Customer.
	 * @param time The TimeLapse object for the current simulation tick.
	 */
	private void performPickup(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();
		while (time.hasTimeLeft()) {
			moveTo(_path.get(0), time);
			// Check if we've made it to the next point.
			if (rm.getPosition(this).equals(_path.get(0))) {
				_path.remove(0);
				if (rm.getPosition(this).equals(_queue.get(0).getPickupLocation())) {
					// pickup customer
					pm.pickup(this, _queue.get(0), time);
					_state = TaxiState.DELIVERING;
				}
			}
		}
	}

	/**
	 * Perform steps needed to deliver a Customer.
	 * @param time The TimeLapse object for the current simulation tick.
	 */
	private void performDelivery(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();
		while (time.hasTimeLeft()) {
			moveTo(_path.get(0), time);
			if (rm.getPosition(this).equals(_path.get(0))) {
				_path.remove(0);
				if (rm.getPosition(this).equals(_queue.get(0).getDeliveryLocation())) {
					// deliver when we arrive
					pm.deliver(this, _queue.remove(0), time);
					if (_queue.isEmpty()) {
						_state = TaxiState.IDLE;
						_nearestStationPos = rm.getPosition(RoadModels.findClosestObject(this.getPosition().get(), rm, GasStation.class));
					}
					else
						_state = TaxiState.PICKING_UP;
				}
			}
		}
	}

	/**
	 * Send an offer message.
	 * @param message The request message to respond to.
	 */
	private void sendOffer(Message message) {
		final RoadModel rm = getRoadModel();
		Customer customer = (Customer)message.getSender();
		double offer;
		
		double distanceTasks = GraphUtils.getPathLength(_path);		// Distance that has to be traveled to complete current tasks.
		double distanceFromEndpoint = distanceAfterFree(customer);	// Distance from the first moment the taxi is free.
	
		// Check how far it will be to go to a fueling station after the delivery.
		_nearestStationPos = rm.getPosition(RoadModels.findClosestObject(customer.getDeliveryLocation(), rm, GasStation.class));
		double refuelDistance = GraphUtils.getPathLength(rm.getShortestPathTo(customer.getDeliveryLocation(), _nearestStationPos));
		// Check if the aggregate distance doesn't bring us in a too low fuel state.
		if (_fuelGauge - fuelNeeded(distanceTasks + distanceFromEndpoint + refuelDistance) < REFUEL_BUFFER) {
			// The taxi must refuel first, so the refueling has to be done first and computed along with the current offer.
			if (_queue.isEmpty())
				_nearestStationPos = rm.getPosition(RoadModels.findClosestObject(this.getPosition().get(), rm, GasStation.class));
			else
				_nearestStationPos = rm.getPosition(RoadModels.findClosestObject(getFreeLocation(), rm, GasStation.class));
			refuelDistance = GraphUtils.getShortestPathLength(customer.getDeliveryLocation(), _nearestStationPos) +
					GraphUtils.getShortestPathLength(customer.getDeliveryLocation(), _nearestStationPos);
			offer = distanceTasks + distanceFromEndpoint + refuelDistance;
		} else	// No fuel problems, take the tasks distance and distance after tasks to customer.
			offer = distanceTasks + distanceFromEndpoint;
		
		try {
			_comm.send(new Messages.OfferMessage(offer, _queuePosition), message.getSender());
		} catch(IllegalArgumentException e) { }		// Receiver has moved on and is no longer listening.
	}

	/**
	 * Handle an accept message, by replying with a "no longer available" or "confirm".
	 * @param message The accept message to respond to.
	 */
	private void handleAcceptRequest(Message message) {
		// Check if the offer that has been responded to is still valid.
		if (((Messages.AcceptMessage)message.getContents()).getId() != _queuePosition)
			_comm.send(new Messages.NoLongerAvailableMessage(), message.getSender());
		else {
			// If the offer is still valid, always accept.
			final RoadModel rm = getRoadModel();
			Customer customer = (Customer)message.getSender();
			_comm.send(new Messages.ConfirmMessage(), customer);
			// Add the new path.
			if (_path.isEmpty())
				_state = TaxiState.PICKING_UP;
			// Add different path depending on whether anything is queued.
			if (_queue.isEmpty())
				_path.addAll(rm.getShortestPathTo(this, customer.getPickupLocation()));	
			else
				_path.addAll(rm.getShortestPathTo(getFreeLocation(), customer.getPickupLocation()));	
			_path.addAll(rm.getShortestPathTo(customer.getPickupLocation(), customer.getDeliveryLocation()));
			_queue.add(customer);
			_queuePosition++;
		}
	}

	/**
	 * Calculate distance to the customer from current position or
	 * from the position after completing current tasks.
	 * @param customer The customer to calculate the distance to.
	 * @return The distance to the customer.
	 */
	private double distanceAfterFree(Customer customer) {
		if (_queue.isEmpty())
			return GraphUtils.getShortestPathLength(this, customer.getPickupLocation());
		else
			return GraphUtils.getShortestPathLength(getFreeLocation(), customer.getPickupLocation());
	}

	public void setCommDevice(CommDeviceBuilder builder) {
		_comm = builder.setMaxRange(RANGE).setReliability(RELIABILITY).build();
	}

	public Optional<Point> getPosition() {
		return Optional.fromNullable(getRoadModel().getPosition(this));
	}

	public ImmutableList<Customer> getQueue() {
		return ImmutableList.copyOf(_queue);
	}
	
	/**
	 * Move towards a point, considering fuel constraints.
	 */
	public void moveTo(Point p, TimeLapse time) {
		final RoadModel rm = getRoadModel();
		MoveProgress progress = rm.moveTo(this, p, time);
		double d = progress.distance().doubleValue(SI.CENTIMETER)/1000;	// / 1000, TODO: something is wrong with the units, of by x100.
		double fuelUsed = fuelNeeded(d);
		_fuelGauge -= fuelUsed;
		_totalFuelUsed += fuelUsed;
	}
	
	/**
	 * Calculate the fuel needed to travel the given distance.
	 * @param distance Distance traveled.
	 * @return Fueled needed to travel the distance.
	 */
	public static double fuelNeeded(double distance) {
		return distance*FUEL_EFFICIENCY;
	}
	
	/**
	 * Get the location where the task will finish its current last task.
	 * @return The location where the task will finish its current last task.
	 */
	private Point getFreeLocation() {
		return _queue.get(_queue.size()-1).getDeliveryLocation();
	}
	
	/**
	 * Get the fuel gauge's state.
	 * @return The state of the fuel gauge.
	 */
	public double getFuelGauge() {
		return _fuelGauge;
	}

	/**
	 * Return the path that the taxi is currently planning to follow for tasks.
	 * @return The path that the taxi is currently planning to follow for tasks.
	 */
	public List<Point> getPlannedPath() {
		return new ArrayList<Point>(_path);
	}
	
	/**
	 * Get the fuel capacity of the taxi.
	 * @return The taxi's fuel capacity.
	 */
	public double getFuelCapacity() {
		return FUEL_CAPACITY;
	}
	
	/**
	 * Get the taxi's movement state.
	 * @return The state of the taxi.
	 */
	public TaxiState getState() {
		return _state;
	}
	
	public static enum TaxiState {
		IDLE, PICKING_UP, DELIVERING, REFUELING
	}

	public void setModel(GradientModel model) {
		_gm = model;
	}
	
	/**
	 * The peak strength of the field.
	 * @return The peak strength of the field.
	 */
	public double getStrength() {
		if (_state != TaxiState.IDLE)
			return 0d;
		Point p = getPosition().get();
		double peak = _gm.getPeakDensity();
		double density = _gm.getMapDistribution().density(GraphUtils.scaleFromGraph(p));
		// Map the density 0~1 to an exponential, at the same time increasing the power difference in low density areas.
		return Math.pow(1 - density/(peak+FIELD_STRENGTH_PADDING), DENSITY_EXPONENTIAL_SCALE)*FIELD_STRENGTH_MULTIPLIER;
	}

	public RealVector getField(Point p) {
		Point from = getPosition().get();
		RealVector v = new ArrayRealVector(new double[]{p.x-from.x, p.y-from.y});
		double l = v.getNorm();
		double strength = getStrength();								// Undiminished field strength.
		double fieldDrop = Math.min(strength, l*FIELD_DIMINISH_RATE);	// Diminished strength, to a max of the maximum strength.
		strength -= fieldDrop;
		// The drop in field strength is linearly correlated to the distance.
		if (l != 0)
			v = v.unitVector().mapMultiply(strength);
		return v;
	}

	public double getRange() {
		return getStrength()/FIELD_DIMINISH_RATE;
	}
}
