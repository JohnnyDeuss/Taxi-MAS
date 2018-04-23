package utils;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;

public class LeuvenDistribution extends MixtureMultivariateNormalDistribution {
	
	// Hotspot 1: around Leuven station, high weight.
	// Hotspot 2: around Leuven center, lower weight, slightly elongated along the y=-x axis.
	// Hotspot 3: Near uniform distribution.
	final static double[] HOTSPOT_WEIGHTS = {0.15, 0.475, 0.375};
	final static double[][] HOTSPOT_MEANS = {
		{0.66, 0.5},
		{0.585, 0.545},
		{0.585, 0.545}};
	final static double[][][] HOTSPOT_COVARIANCES = {
		{{0.0003,0},{0,0.0003}},
		{{0.006,0.002},{0.002,0.006}},
		{{0.2,0},{0,0.2}}};
	public LeuvenDistribution() {
		super(
			HOTSPOT_WEIGHTS,
			HOTSPOT_MEANS,
			HOTSPOT_COVARIANCES
		);
	}
}
