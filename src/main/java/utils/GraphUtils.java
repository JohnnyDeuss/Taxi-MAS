package utils;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.distribution.MultivariateRealDistribution;

import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.Point;

/**
 * Class to combine functionality to do with working with the road graphs.
 */
public class GraphUtils {
	static Graph<?> _graph;
	static RoadModel _rm;
	static Point _bounds[];
	
	/**
	 * Constructor.
	 * @param graph The graph to use for calculations.
	 */
	public static void init(Graph<?> graph, RoadModel rm) {
		_graph = graph;
		_rm = rm;
		initBounds();
	}

	public static Graph<?> getGraph() {
		return _graph;
	}
	
	/**
	 * Calculate the bounds of the graph and remember them.
	 */
	static private void initBounds() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		
		for (Point p : _graph.getNodes()) {
			if (p.x < minX)
				minX = p.x;
			if (p.y < minY)
				minY = p.y;
			if (p.x > maxX)
				maxX = p.x;
			if (p.y > maxY)
				maxY = p.y;
		}
		_bounds = new Point[]{new Point(minX, minY), new Point(maxX, maxY)};
	}

	/**
	 * Get a random position on the graph.
	 * @param generator A multivariate real generator.
	 * @return A position on the graph.
	 */
	static public Point getRandomNode(MultivariateRealDistribution generator) {
		Point randomPoint = getRandomPoint2D(generator);
		return getClosestNode(randomPoint);
	}
	
	static public Point getClosestNode(Point p) {
		Point closestPoint = null;
		double shortestDistance = Double.MAX_VALUE;
		
		for (Point n : _graph.getNodes()) {
			double distance = Point.distance(n, p);
			if (distance < shortestDistance) {
				shortestDistance = distance;
				closestPoint = n;
			}
		}
		return closestPoint;
	}

	/**
	 * Get a random point within the bound of the map.
	 * @param generator A multivariate real generator.
	 * @return A point on the map, not a position on the graph.
	 */
	static public Point getRandomPoint2D(MultivariateRealDistribution generator) {
		double [] p = generator.sample();
		while (p[0] < 0 || p[0] > 1 || p[1] < 0 || p[1] > 1)
			p = generator.sample();
		return scaleToGraph(p);
	}
	
	/**
	 * Scale a point from the range 0~1 to the fit on the graph,
	 * where 0 is the left bound and 1 the right bound.
	 * @param p The point to scale to the graph, in the range 0~1.
	 * @return A point on the graph.
	 */
	static public Point scaleToGraph(double[] p) {
		p[0] *= _bounds[1].x-_bounds[0].x;		// Scale to bounds.
		p[1] *= _bounds[1].y-_bounds[0].y;
		p[0] += _bounds[0].x;					// Offset bounds.
		p[1] += _bounds[0].y;
		return new Point(p[0], p[1]);
	}
	
	/**
	 * Scale a point on the map to the range 0~1,
	 * where 0 is the left bound and 1 the right bound.
	 * @param p The point on the graph.
	 * @return A point on in the range 0~1.
	 */
	static public double[] scaleFromGraph(Point p) {
		double[] r = {p.x, p.y};
		r[0] -= _bounds[0].x;					// Offset bounds.
		r[1] -= _bounds[0].y;
		r[0] /= _bounds[1].x-_bounds[0].x;		// Scale to bounds.
		r[1] /= _bounds[1].y-_bounds[0].y;
		return r;
	}

	/**
	 * Get the length of a path.
	 * @param path The path to calculate the distance of.
	 * @return The given path's total length.
	 */
	static public double getPathLength(List<Point> path) {
		double length = 0;
		Iterator<Point> it = path.iterator();
		if (it.hasNext()) {
			Point prev = it.next();
			while(it.hasNext()) {
				Point curr = it.next();
				length += Point.distance(prev, curr);
				prev = curr;
			}
		}
		return length;
	}

	/**
	 * Get the length of the shortest path.
	 */
	static public double getShortestPathLength(Point from, Point to) {
		return getPathLength(_rm.getShortestPathTo(from, to));
	}
	static public double getShortestPathLength(RoadUser from, Point to) {
		return getPathLength(_rm.getShortestPathTo(from, to));
	}
	static public double getShortestPathLength(RoadUser from, RoadUser to) {
		return getPathLength(_rm.getShortestPathTo(from, to));
	}
	static public double getShortestPathLength(Point from, RoadUser to) {
		return getPathLength(_rm.getShortestPathTo(from, _rm.getPosition(to)));
	}
/*
	public static Point getSmallestAngle(Point p, RealVector v) {
		Collection<Point> l = _graph.getOutgoingConnections(p);
		double biggestCos = -2d;
		Point r = null;
		
		for (Point to : l) {
			double cos = v.cosine(new ArrayRealVector(new double[]{to.x, to.y}));
			if (cos > biggestCos) {
				biggestCos = cos;
				r = to;
			}
		}
		return r;
	}*/
}
