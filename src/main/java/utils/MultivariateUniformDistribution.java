package utils;

import java.util.Random;

import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;

public class MultivariateUniformDistribution implements MultivariateRealDistribution {
	
	private Random _rng;
	
	public MultivariateUniformDistribution() {
		_rng = new Random();
	}
	
	public double density(double[] x) {
		return 1;
	}

	public int getDimension() {
		return 2;
	}

	public void reseedRandomGenerator(long seed) {
		_rng = new Random(seed);
	}

	public double[] sample() {
		return new double[]{_rng.nextDouble(), _rng.nextDouble()};
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

}
