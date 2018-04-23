package taxi;

import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import gradient.GradientModel;
import taxi.Taxi.TaxiState;
import utils.GraphUtils;
import utils.MultivariateEmpiricalDistribution;

class Customer extends Parcel implements CommUser, TickListener {

	private static final double RANGE = 500000d;					// Comm. range, 50km.
	private static final double RELIABILITY = 1d;
	private static final double PATIENCE_MEAN = 8*60*1000d;			// The mean Customer patience (in terms of offers).
	private static final double PATIENCE_VARIANCE = 1*60*1000d;		// The mean Customer patience (in terms of offers).
	private static final double MIN_DISTANCE = 5000d;				// The minimum that a customer will take a taxi for.
	private static final double MIN_PATIENCE = 4*60*1000d;			// The minimum time any Customer is willing to wait.
	private static NormalDistribution PATIENCE_GENERATOR = new NormalDistribution(PATIENCE_MEAN, PATIENCE_VARIANCE);

	private double _patience;
	private double _pathLength;
	private double _savedOffer;
	private int _numOffers;			// Number of offers received.
	private CommDevice _comm;
	private Simulator _sim;
	private CustomerState _state = CustomerState.IDLE;
	private int _ticksSinceMissed = 0;
	
	public Customer(ParcelDTO buildDTO, Simulator simulator) {
		super(buildDTO);
		_sim = simulator;
		do {
			_patience = PATIENCE_GENERATOR.sample();
		} while (_patience < MIN_PATIENCE);
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		final GradientModel gm = _sim.getModelProvider().getModel(GradientModel.class);
		MultivariateRealDistribution dist = gm.getMapDistribution();

		// Add occurrence to empirical distribution.
		final Point p = getPosition().get();
		if (dist instanceof MultivariateEmpiricalDistribution)
			((MultivariateEmpiricalDistribution)dist).load(new double[]{p.x,p.y});
	}

	public Optional<Point> getPosition() {
		final RoadModel rm = _sim.getModelProvider().getModel(RoadModel.class);
		if (rm.containsObject(this))
			return Optional.fromNullable(rm.getPosition(this));
		else
			return Optional.absent();
	}

	public void setCommDevice(CommDeviceBuilder builder) {
		_comm = builder.setMaxRange(RANGE).setReliability(RELIABILITY).build();
	}

	private void sendRequest() {
		final RandomGenerator rng = _sim.getRandomGenerator();
		final RoadModel rm = _sim.getModelProvider().getModel(RoadModel.class);
		Point from, to;
		from = rm.getPosition(this);

		// Make sure you don't go to the same point.
		do {
			to = rm.getRandomPosition(rng);
			_pathLength = GraphUtils.getShortestPathLength(from, to);
		} while (_pathLength < MIN_DISTANCE);
			
		_comm.broadcast(new Messages.RequestMessage(from, to));
		_numOffers = 0;
		_state = CustomerState.WAITING_FOR_OFFERS;
	}

	public void tick(TimeLapse time) {
		if (_state == CustomerState.IDLE)
			sendRequest();
		else if (_state == CustomerState.MISSED) {
			_ticksSinceMissed++;
			if (_ticksSinceMissed == 60)
				_sim.unregister(this);
		} else {
			// Check messages.
			ImmutableList<Message> messages = _comm.getUnreadMessages();

			// If waiting for offers, check if there are offers.
			if (_state == CustomerState.WAITING_FOR_OFFERS) {
				Message bestOfferMessage = null;
				double bestOffer = Double.POSITIVE_INFINITY;
				
				for (Message message : messages) {
					MessageContents contents = message.getContents();
					// Make sure it's an offer message.
					if (contents instanceof Messages.OfferMessage) {
						_numOffers++;
						Messages.OfferMessage offerMessage = (Messages.OfferMessage)contents;
						// Check if the new offer is better.						
						if (offerMessage.getOffer() < bestOffer) {
							bestOfferMessage = message;
							bestOffer = offerMessage.getOffer();
						}
					}
				}
				if (_numOffers == TaxiSimulator.NUM_TAXIS) {
					// dm/(dm/h)*ms/h = ms
					if (bestOffer/100/Taxi.SPEED*60*60*1000 < _patience) {
						_savedOffer = bestOffer;
						Messages.OfferMessage offerMessage = (Messages.OfferMessage)bestOfferMessage.getContents();
						_comm.send(new Messages.AcceptMessage(offerMessage.getId()), bestOfferMessage.getSender());
						_state = CustomerState.WAITING_FOR_ACCEPTANCE;
					}
					else {
						// If no offer is good enough, the user gives up and uses a different method.
						_state = CustomerState.MISSED;
						_ticksSinceMissed++;
						
						System.out.println(time.getTime()+",0,"+_pathLength+","+bestOffer+","+idlePercentage());
					}
				}
			}
			
			// If waiting for offers, check if there are offers.
			if (_state == CustomerState.WAITING_FOR_ACCEPTANCE) {
				for (Message message : messages) {
					MessageContents contents = message.getContents();
					// Make sure it's an offer message.
					if (contents instanceof Messages.ConfirmMessage) {
						_state = CustomerState.WAITING_FOR_PICKUP;
						System.out.println(time.getTime()+",1,"+_pathLength+","+_savedOffer+","+idlePercentage());
					}
					else if (contents instanceof Messages.NoLongerAvailableMessage)
						sendRequest();
				}
			}
		}
	}
	
	private double idlePercentage() {
		// Give occupancy statistics.
		int idleTaxis = 0;
		for (Taxi t : getRoadModel().getObjectsOfType(Taxi.class))
			if (t.getState() != TaxiState.IDLE)
				idleTaxis++;
		return (double)idleTaxis/TaxiSimulator.NUM_TAXIS;
	}

	public void afterTick(TimeLapse timeLapse) { }
	
	public CustomerState getState() {
		return _state;
	}
	
	public static enum CustomerState {
		IDLE, WAITING_FOR_OFFERS, WAITING_FOR_ACCEPTANCE, WAITING_FOR_PICKUP, MISSED
	}
}
