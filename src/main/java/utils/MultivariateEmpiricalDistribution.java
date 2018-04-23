package utils;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;

import com.github.rinde.rinsim.geom.Point;

/**
 * Very basic empirical distribution, density is based on the bins surrounding
 * the given input, i.e. if x = 8.5, then the value of bins 8 and 9 will be averaged.
 */
public class MultivariateEmpiricalDistribution implements MultivariateRealDistribution {
	
	static final int BINS = 1000;
	private double _peak = 0;
	private double _total = 0;
	private double[][] _bins = new double[BINS][BINS];
	
	public MultivariateEmpiricalDistribution() { }
	
	public double density(double[] p) {
		if (_total == 0)
			return 0;
		
		double xCenterWeight = p[0]-Math.floor(p[0]);
		double yCenterWeight = p[1]-Math.floor(p[1]);
		
		
		int x = (int)(p[0]*(BINS-2));
		int y = (int)(p[1]*(BINS-2));
		// Interpolate.
		return (_bins[y][x]*(1-xCenterWeight) + _bins[y][x+1]*xCenterWeight +	// Horizontal average.
				_bins[y][x]*(1-yCenterWeight) + _bins[y+1][x]*yCenterWeight) / 2 / _total;
	}

	public int getDimension() {
		return 2;
	}

	public void reseedRandomGenerator(long seed) {
		throw new NotImplementedException("Not implemented, not needed.");
	}

	public double[] sample() {
		throw new NotImplementedException("Not implemented, not needed.");
	}

	public double[][] sample(int sampleSize) throws NotStrictlyPositiveException {
		double[][] r = new double[sampleSize][2];
		for (int i = 0; i < sampleSize; i++) {
			double[] s = sample();
			r[i][0] = s[0];
			r[i][1] = s[1];
		}
		return r;
	}
	
	/**
	 * Load 
	 * @param in
	 */
	public void load(double[] in) {
		// Update peak.
		double[] gp = GraphUtils.scaleFromGraph(new Point(in[0],in[1]));
		MultivariateNormalDistribution dist = new MultivariateNormalDistribution(gp, new double[][]{{0.01,0},{0,0.01}});
		double interval = 1d/BINS;
		
		double xVal = 0d;
		for (int i = 0; i < BINS; i++) {
			double yVal = 0d;
			for (int j = 0; j < BINS; j++) {
				double d = dist.density(new double[]{xVal,yVal});
				_bins[j][i] += d;
				_total += d;
				yVal += interval;
			}
			xVal += interval;
		}
		int x = (int)Math.round(gp[0]*BINS);
		int y = (int)Math.round(gp[1]*BINS);
		/*_bins[y][x]++;
		_total++;¨*/
		if (_bins[y][x] > _peak)
			_peak = _bins[y][x];
	}

	public double getPeak() {
		return _peak;
	}
}
