package taxi;

import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.github.rinde.rinsim.geom.Point;

abstract public class Messages {
	static public class RequestMessage implements MessageContents {
		private Point _pickup;
		private Point _destination;
		
		public RequestMessage(Point pickup, Point destination) {
			_pickup = pickup;
			_destination = destination;
		}

		public Point getPickup() { return _pickup; }
		public Point getDestination() { return _destination; }
	}
	
	// Offer message from Customer to Taxi.
	static public class OfferMessage implements MessageContents {
		private double _offer;
		private int _id;
		
		public OfferMessage(double offer, int offerId) {
			_offer = offer;
			_id = offerId;
		}

		public double getOffer() { return _offer; }
		public int getId() { return _id; }
	}
	
	// Accept offer message from Customer to taxi.
	static public class AcceptMessage implements MessageContents {
		private int _id;
		
		public AcceptMessage(int offerId) {
			_id = offerId;
		}
		
		public int getId() { return _id; }
	}
	
	// When the Taxi already confirmed another pickup, send this message to let customers that accepted an offer.
	static public class NoLongerAvailableMessage implements MessageContents {
		public NoLongerAvailableMessage() { }
	}
	
	// Confirm a pickup.
	static public class ConfirmMessage implements MessageContents {
		public ConfirmMessage() {};
	}
}
