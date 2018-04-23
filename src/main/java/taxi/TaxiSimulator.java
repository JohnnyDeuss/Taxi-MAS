package taxi;

import java.io.FileNotFoundException;
import java.io.IOException;
import javax.annotation.Nullable;

import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.comm.CommModel;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.MultiAttributeData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.io.DotGraphIO;
import com.github.rinde.rinsim.geom.io.Filters;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

import gradient.GradientFieldRenderer;
import gradient.GradientModel;
import utils.GraphUtils;
import utils.LeuvenDistribution;
import utils.MultivariateEmpiricalDistribution;
import utils.MultivariateUniformDistribution;

/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * 
 * @author Rinde van Lon
 */
public final class TaxiSimulator {

	// Units: time(ms), distance(dm), volume(ml).
	public static final int NUM_GAS_STATIONS = 8;
	public static final int NUM_TAXIS = 30;
	private static final long SERVICE_DURATION = 60000L;
	private static final int TAXI_CAPACITY = 1;
	// private static final int GAS_STATION_CAPACITY = 100;
	private static final long TEST_STOP_TIME = 24L * 60L * 60* 1000L;
	private static final int TEST_SPEED_UP = 64;
	private static final int SPEED_UP = 4;
	// private static final int MAX_CAPACITY = 1;
	private static final double CUSTOMER_INTERVAL = 24*60*60*1000 / 500;	// 500 a day.
	private static final String MAP_FILE = "/data/maps/leuven-simple.dot";

	private static double _timeSinceLastCustomer = 0;
	
	private TaxiSimulator() {
		
	}

	/**
	 * Starts the {@link TaxiSimulator}.
	 * 
	 * @param args
	 *            The first option may optionally indicate the end time of the
	 *            simulation.
	 */
	public static void main(@Nullable String[] args) {
		long endTime = 1000*60*60*24;
		final String graphFile = args != null && args.length >= 2 ? args[1] : MAP_FILE;
		final boolean testing = false;
		run(testing, endTime, graphFile, null /* new Display() */, null, null);
	}

	/**
	 * Run the example.
	 * 
	 * @param testing
	 *            If <code>true</code> enables the test mode.
	 */
	public static void run(boolean testing) {
		run(testing, Long.MAX_VALUE, MAP_FILE, null, null, null);
	}

	/**
	 * Starts the example.
	 * 
	 * @param testing
	 *            Indicates whether the method should run in testing mode.
	 * @param endTime
	 *            The time at which simulation should stop.
	 * @param graphFile
	 *            The graph that should be loaded.
	 * @param display
	 *            The display that should be used to show the ui on.
	 * @param m
	 *            The monitor that should be used to show the ui on.
	 * @param list
	 *            A listener that will receive callbacks from the ui.
	 * @return The simulator instance.
	 */
	public static Simulator run(boolean testing, final long endTime, String graphFile, @Nullable Display display,
			@Nullable Monitor m, @Nullable Listener list) {

		System.out.println("timestamp,accepted,pathLength,bestOffer,idlePercent");
				
		final View.Builder view = createGui(testing, display, m, list);
		Graph<MultiAttributeData> graph = loadGraph(graphFile);
		// Use map of Leuven.
		final Simulator simulator = Simulator.builder()
				.addModel(RoadModelBuilders.staticGraph(graph))
				.addModel(DefaultPDPModel.builder())
				.addModel(CommModel.builder())
				.addModel(GradientModel.builder())
				.addModel(view).build();
		final RandomGenerator rng = simulator.getRandomGenerator();
		final RoadModel roadModel = simulator.getModelProvider().getModel(RoadModel.class);
		final MultivariateRealDistribution rng2D = new LeuvenDistribution();
		final MultivariateRealDistribution rng2DTaxi = rng2D;
		//final MultivariateRealDistribution rng2DTaxi = new MultivariateUniformDistribution();
		//final MultivariateRealDistribution rng2DTaxi = new MultivariateEmpiricalDistribution();
		simulator.getModelProvider().getModel(GradientModel.class).setMapDistribution(rng2DTaxi);
		GraphUtils.init(graph, roadModel);

		// Ensure deterministic execution.
		rng2D.reseedRandomGenerator(0);
		
		// add depots, taxis and parcels to simulator
		for (int i = 0; i < NUM_GAS_STATIONS; i++)
			simulator.register(new GasStation(GraphUtils.getRandomNode(rng2D)));
		for (int i = 0; i < NUM_TAXIS; i++)
			simulator.register(new Taxi(roadModel.getRandomPosition(rng), TAXI_CAPACITY));

		simulator.addTickListener(new TickListener() {
			public void tick(TimeLapse time) {
				_timeSinceLastCustomer += time.getTickLength();
				if (time.getStartTime() > endTime) {
					for (Taxi t : simulator.getModelProvider().getModel(RoadModel.class).getObjectsOfType(Taxi.class))
						System.out.println(t._totalFuelUsed);
					simulator.stop();
				} else if (_timeSinceLastCustomer > CUSTOMER_INTERVAL) {
					_timeSinceLastCustomer = _timeSinceLastCustomer % CUSTOMER_INTERVAL;
					ParcelDTO builder = Parcel
							.builder(GraphUtils.getRandomNode(rng2D), roadModel.getRandomPosition(rng))
							.serviceDuration(SERVICE_DURATION)
							// larger groups? More than 1?
							.neededCapacity(1) // + rng.nextInt(MAX_CAPACITY)
							.buildDTO();
					simulator.register(new Customer(builder, simulator));
				}
			}

			public void afterTick(TimeLapse timeLapse) {
			}
		});
		simulator.start();

		return simulator;
	}

	static View.Builder createGui(boolean testing, @Nullable Display display, @Nullable Monitor m,
			@Nullable Listener list) {

		View.Builder view = View.builder().with(GraphRoadModelRenderer.builder())
				.with(RoadUserRenderer.builder()
						.withImageAssociation(GasStation.class, "/graphics/flat/gas-station-32.png")
						.withImageAssociation(Taxi.class, "/graphics/flat/taxi-32.png")
						.withImageAssociation(Customer.class, "/graphics/flat/person-red-32.png"))
				.with(TaxiRenderer.builder())
				.with(CustomerRenderer.builder())
				.with(GradientFieldRenderer.builder())
				.withTitleAppendix("Taxi Simulator");

		if (testing) {
			view = view.withAutoClose()
					.withAutoPlay()
					.withSimulatorEndTime(TEST_STOP_TIME)
					.withSpeedUp(TEST_SPEED_UP);
		} else if (m != null && list != null && display != null) {
			view = view.withMonitor(m)
					.withSpeedUp(SPEED_UP)
					.withResolution(m.getClientArea().width, m.getClientArea().height)
					.withDisplay(display)
					.withCallback(list)
					.withAsync()
					.withAutoPlay()
					.withAutoClose();
		}
		return view;
	}

	// load the graph file
	static Graph<MultiAttributeData> loadGraph(String name) {
		try {
			final Graph<MultiAttributeData> g = DotGraphIO.getMultiAttributeGraphIO(Filters.selfCycleFilter())
					.read(TaxiSimulator.class.getResourceAsStream(name));
			return g;
		} catch (final FileNotFoundException e) {
			throw new IllegalStateException(e);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static class TaxiBase extends Depot {
		TaxiBase(Point position, double capacity) {
			super(position);
			setCapacity(capacity);
		}

		@Override
		public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		}
	}

	static class GasStation implements RoadUser {
		Point _startPosition;

		GasStation(Point position) {
			_startPosition = position;
		}

		public void initRoadUser(RoadModel model) {
			model.addObjectAt(this, _startPosition);
		}
	}
}
