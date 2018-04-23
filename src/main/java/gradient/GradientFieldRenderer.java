package gradient;

import java.util.List;

import org.apache.commons.math3.distribution.MultivariateRealDistribution;
import org.apache.commons.math3.linear.RealVector;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.CanvasRenderer.AbstractCanvasRenderer;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.google.auto.value.AutoValue;

import taxi.Taxi;
import taxi.Taxi.TaxiState;

public class GradientFieldRenderer extends AbstractCanvasRenderer {

	final static private int HOTMAP_INTERVAL = 4;
	
	GradientModel _gm;

	GradientFieldRenderer(GradientModel gm) {
		_gm = gm;
	}

	public void renderStatic(GC gc, ViewPort vp) {
		MultivariateRealDistribution dist = _gm.getMapDistribution();
		double maxDensity = _gm.getPeakDensity();
		int xMax = vp.toCoordX(vp.rect.max.x);
		int yMax = vp.toCoordY(vp.rect.max.y);
		
		gc.setAlpha(127);
		for (int x = 0; x < xMax; x += HOTMAP_INTERVAL) {
			for (int y = 0; y < yMax; y += HOTMAP_INTERVAL) {
				int intensity = (int)(dist.density(new double[]{x/(double)xMax, y/(double)yMax})/maxDensity*255);
				gc.setBackground(new Color(gc.getDevice(), intensity, 0, 255-intensity));
				gc.fillRectangle(x, y, HOTMAP_INTERVAL, HOTMAP_INTERVAL);
			}
		}
		
	}

	public void renderDynamic(GC gc, ViewPort vp, long time) {
		final List<Taxi> taxis = _gm.getTaxis();

		synchronized (taxis) {
			for (final Taxi t : taxis) {
				final Point p = t.getPosition().get();
				final RealVector v = t.getRedustributionVector();
				if (t.getState() == TaxiState.IDLE && v != null) {
					// Gradient field resultant vector.
					gc.setAlpha(255);
					gc.setLineWidth(2);
					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_MAGENTA));
					gc.fillOval(vp.toCoordX(p.x)-2, vp.toCoordY(p.y)-2, 4, 4);
					gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_YELLOW));
					gc.drawLine(vp.toCoordX(p.x), vp.toCoordY(p.y), vp.toCoordX(p.x+v.getEntry(0)), vp.toCoordY(p.y+v.getEntry(1)));
					// Gradient field.
					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
					gc.setAlpha(20);
					int d = vp.scale(t.getRange())*2;
					gc.fillOval(vp.toCoordX(p.x)-d/2, vp.toCoordY(p.y)-d/2, d, d);
				}
			}
		}
	}

	public static Builder builder() {
		return new AutoValue_GradientFieldRenderer_Builder();
	}

	@AutoValue
	abstract static class Builder extends AbstractModelBuilder<GradientFieldRenderer, Void> {

		Builder() {
			setDependencies(GradientModel.class);
		}

		public GradientFieldRenderer build(DependencyProvider dependencyProvider) {
			final GradientModel gm = dependencyProvider.get(GradientModel.class);
			return new GradientFieldRenderer(gm);
		}
	}
}
