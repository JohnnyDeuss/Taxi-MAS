package gradient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModel;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.ModelProvider;
import com.github.rinde.rinsim.core.model.ModelReceiver;
import com.github.rinde.rinsim.geom.Point;
import com.google.auto.value.AutoValue;

import taxi.Taxi;
import utils.GraphUtils;
import utils.MultivariateEmpiricalDistribution;
import utils.MultivariateUniformDistribution;

//TODO: map pushes equally hard always, regardless of the amount of taxis.
/**
 * Model for gradient field implementation.
 */
public class GradientModel extends AbstractModel<FieldEmitter> implements ModelReceiver {

	// The number of directions to check.
	private static final int GRADIENT_RESOLUTION = 32;
	private static final double GRADIENT_MAP_WEIGHT = 4000d;		// How heavy to weigh the gradient generated by the map distribution.
	public static final double DENSITY_EXPONENTIAL_SCALE = 2;		// Map densities using an exponential.
	private static final double FIELD_RESULTANT_SCALE = 2d;			// Scale the resultant field by a scalar.
	private static double[][] SINE_CACHE;
	{
		SINE_CACHE = new double[GRADIENT_RESOLUTION][2];
		// Precompute sine and cosine offsets for unit distances.
		for (int i = 0; i < GRADIENT_RESOLUTION; i++) {
			double alpha = 2*Math.PI/GRADIENT_RESOLUTION*i;
			SINE_CACHE[i][0] = Math.sin(alpha);
			SINE_CACHE[i][1] = Math.cos(alpha);
		}
	}
	
	private final List<FieldEmitter> _emitters;
	private MultivariateRealDistribution _dist; 
	private double _peakDensity = -1;

	GradientModel() {
		_emitters = new CopyOnWriteArrayList<FieldEmitter>();
	}

	List<FieldEmitter> getEmitters() {
		return _emitters;
	}

	List<Taxi> getTaxis() {
		List<Taxi> taxis = new ArrayList<Taxi>();
		for (FieldEmitter e : _emitters)
			if (e instanceof Taxi)
				taxis.add((Taxi)e);
		return taxis;
	}
	
	public void setMapDistribution(MultivariateRealDistribution dist) {
		_dist = dist;
		// Calculate the peaks found at the means (not guaranteed absolute peak, but most likely).
		if (dist instanceof MixtureMultivariateNormalDistribution) {
			MixtureMultivariateNormalDistribution mmnd = (MixtureMultivariateNormalDistribution)dist;
			List<Pair<Double,MultivariateNormalDistribution>> components = mmnd.getComponents();
			double[] peaks = new double[components.size()];
			
			for (int i = 0; i < components.size(); i++) {
				MultivariateNormalDistribution normDist = components.get(i).getValue();
				peaks[i] += mmnd.density(normDist.getMeans());
			}
			for (int i = 0; i < components.size(); i++)
				if (peaks[i]> _peakDensity)
					_peakDensity = peaks[i];
		}
		else
			_peakDensity = _dist.density(new double[]{0,0});
	}
	
	public MultivariateRealDistribution getMapDistribution() {
		return _dist;
	}
	
	/**
	 * Get the maximum density of the means of the individual densities of the map distribution.
	 * @return The maximum peak density (not necessarily the absolute maximum).
	 */
	public double getPeakDensity() {
		if (_dist instanceof MultivariateEmpiricalDistribution) {
			return ((MultivariateEmpiricalDistribution)_dist).getPeak();
		}
		return _peakDensity;
	}
	
	/**
	 * Resultant vector of all fields together affecting a Taxi.
	 * @param taxi The taxi to compute the resultant field for.d
	 * @return The resultant field for the given Taxi.
	 */
	public RealVector getResultantField(Taxi taxi) {
		// Move randomly instead of staying still.
		if (_dist instanceof MultivariateUniformDistribution) {
			Point from = taxi.getPosition().get();
			Point to = GraphUtils.getRandomPoint2D(_dist);
			return new ArrayRealVector(new double[]{to.x-from.x, to.y-from.y});
		} else {
			Point p = taxi.getPosition().get();
			RealVector v = getMapGradient(p);
	
			for (final FieldEmitter emitter : _emitters) {
				if (emitter != taxi) {
					RealVector vEmitter = emitter.getField(p);
					v = v.add(vEmitter);
				}
			}
			return v.mapMultiplyToSelf(FIELD_RESULTANT_SCALE);
		}
	}
	/**
	 * Get an approximate 3D gradient of a point by checking the gradient between points
	 * at a fixed distance of the given point and check in a discrete number of directions.
	 * The magnitude of the 2D gradient determines has steep the gradient is.
	 * @param x The point to find the gradient for.
	 * @return The approximate 2D gradient for the given point.
	 */
	public RealVector getMapGradient(Point p) {
		if (_dist == null)
			return new ArrayRealVector(new double[]{0, 0});
		// If the peak is 0.0, we'll get a NaN issue if we don't check for it.
		double peak = getPeakDensity();
		if (peak == 0d)
			return new ArrayRealVector(new double[]{0, 0});
		
		double[] from = {p.x, p.y};
		double densityFrom = _dist.density(GraphUtils.scaleFromGraph(new Point(from[0], from[1])));
		double densityMax = Double.NEGATIVE_INFINITY;
		RealVector steepestGradient = null;

		for (int i = 0; i < GRADIENT_RESOLUTION; i++) {
			double[] to = from.clone();
			to[0] += SINE_CACHE[i][1];
			to[1] += SINE_CACHE[i][0];
			double densityTo = _dist.density(GraphUtils.scaleFromGraph(new Point(to[0], to[1])));
			if (densityTo > densityMax) {
				densityMax = densityTo;
				// Vector in the given direction with as length the equal to the an inverse square density. 
				steepestGradient = new ArrayRealVector(new double[]{to[0]-from[0], to[1]-from[1]});
			}
		}

		return steepestGradient.mapMultiply(Math.pow(1-densityFrom/peak, DENSITY_EXPONENTIAL_SCALE) * GRADIENT_MAP_WEIGHT);
	}

	public boolean register(FieldEmitter element) {
		_emitters.add(element);
		element.setModel(this);
		return true;
	}

	public boolean unregister(FieldEmitter element) {
		_emitters.remove(element);
		return false;
	}

	public void registerModelProvider(ModelProvider mp) { }

	@Override
	public <U> U get(Class<U> clazz) {
		return clazz.cast(this);
	}

	public static Builder builder() {
		return new AutoValue_GradientModel_Builder();
	}

	@AutoValue
	abstract static class Builder extends AbstractModelBuilder<GradientModel, FieldEmitter> implements Serializable {

		private static final long serialVersionUID = 4464819196521333718L;

		Builder() {
			setProvidingTypes(GradientModel.class);
		}

		public GradientModel build(DependencyProvider dependencyProvider) {
			return new GradientModel();
		}
	}
}
