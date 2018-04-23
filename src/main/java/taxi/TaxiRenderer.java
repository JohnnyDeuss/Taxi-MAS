/*
 * Copyright (C) 2011-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package taxi;

import java.util.Iterator;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleState;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.CanvasRenderer.AbstractCanvasRenderer;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.google.auto.value.AutoValue;

import taxi.Taxi.TaxiState;

/**
 * @author Rinde van Lon
 *
 */
public class TaxiRenderer extends AbstractCanvasRenderer {
	
	static final int ROUND_RECT_ARC_HEIGHT = 5;
	static final int X_OFFSET = -5;
	static final int Y_OFFSET = -30;
	static final int FUEL_BAR_WIDTH = 26;
	static final int FUEL_BAR_HEIGHT = 2;
	static final int FUEL_BAR_OFFSET = 40;
	static final int REFUEL_ICON_WIDTH = 8;
	static final int REFUEL_ICON_OFFSET = 30;

	Composite viewer;
	final RoadModel _rm;
	final PDPModel _pm;

	TaxiRenderer(RoadModel r, PDPModel p) {
		_rm = r;
		_pm = p;
	}

	public void renderStatic(GC gc, ViewPort vp) {
		viewer = getViewer();
		/*Display.getCurrent().addFilter(SWT.MouseMove, new Listener() {
			public void handleEvent(Event e) {
				System.out.println("MOUSE");
				((AbstractCanvasRenderer)viewer).;
			}
		});¨*/
	}
	
	public void renderDynamic(GC gc, ViewPort vp, long time) {
		org.eclipse.swt.graphics.Point cursorLocation = Display.getCurrent().getCursorLocation();
		final Set<Taxi> taxis = _rm.getObjectsOfType(Taxi.class);

		synchronized (taxis) {
			for (final Taxi t : taxis) {
				final Point p = _rm.getPosition(t);
				final int x = vp.toCoordX(p.x) + X_OFFSET;
				final int y = vp.toCoordY(p.y) + Y_OFFSET;
				final VehicleState vs = _pm.getVehicleState(t);

				// Draw the path if the mouse is over a taxi, for that we need the origin of the window.
				org.eclipse.swt.graphics.Point origin = viewer.toDisplay(0, 0);
				if (Math.max(Math.abs(cursorLocation.x - origin.x - vp.toCoordX(p.x)+4),
						Math.abs(cursorLocation.y - origin.y - vp.toCoordY(p.y)+6)) < 18) {
					// Color paths gray.
					gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
					gc.setLineWidth(2);
					Iterator<Point> it = t.getPlannedPath().iterator();
					if (it.hasNext()) {
						Point prev = it.next();
						while(it.hasNext()) {
							Point curr = it.next();
							gc.drawLine(vp.toCoordX(prev.x), vp.toCoordY(prev.y), vp.toCoordX(curr.x), vp.toCoordY(curr.y));
							prev = curr;
						}
					}
					gc.setLineWidth(1);
					// Mark endpoints.
					boolean first = true;
					for (Customer c : t.getQueue()) {
						if (!first || t.getState() != TaxiState.DELIVERING) {
							// Pick-up point.
							Point pickupLoc = c.getPickupLocation();
							gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_GREEN));
							int pickupX = vp.toCoordX(pickupLoc.x), pickupY = vp.toCoordY(pickupLoc.y);
							gc.fillOval(pickupX-3, pickupY-3, 6, 6);
						}
						first = false;
						// Delivery point.
						Point deliveryLoc = c.getDeliveryLocation();
						gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_RED));
						int deliveryX = vp.toCoordX(deliveryLoc.x), deliveryY = vp.toCoordY(deliveryLoc.y);
						gc.fillOval(deliveryX-3, deliveryY-3, 6, 6);
					}
				}
					
				String text = null;
				final int inTaxi = (int) _pm.getContentsSize(t);
				final int size = (int)t.getQueue().size();
				if (vs == VehicleState.DELIVERING) {
					text = "DELIVERING";
				} else if (vs == VehicleState.PICKING_UP) {
					text = "PICKUP";
				} else {
					text = "#"+t._id+" "+Integer.toString(inTaxi) + " (" + Integer.toString(size-inTaxi) + ")";
				}

				if (text != null) {
					final org.eclipse.swt.graphics.Point extent = gc.textExtent(text);

					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_BLUE));
					gc.fillRoundRectangle(x - extent.x / 2, y - extent.y / 2, extent.x + 2, extent.y + 2,
							ROUND_RECT_ARC_HEIGHT, ROUND_RECT_ARC_HEIGHT);
					gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));

					gc.drawText(text, x - extent.x / 2 + 1, y - extent.y / 2 + 1, true);
				}
				// Render fuel gauge.
				int fuelOffset = (int)(t.getFuelGauge() / t.getFuelCapacity() * FUEL_BAR_WIDTH);
				gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_GREEN));
				gc.fillRectangle(x-FUEL_BAR_WIDTH/2+1, y+FUEL_BAR_OFFSET,
						fuelOffset, FUEL_BAR_HEIGHT);
				gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
				gc.fillRectangle(x-FUEL_BAR_WIDTH/2+1+fuelOffset, y+FUEL_BAR_OFFSET,
						FUEL_BAR_WIDTH-fuelOffset, FUEL_BAR_HEIGHT);
				if (t.getFuelGauge() < Taxi.REFUEL_THRESHOLD) {
					// Show refuel icon.
					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
					gc.fillOval(x-REFUEL_ICON_WIDTH/2+10, y+REFUEL_ICON_OFFSET,
							REFUEL_ICON_WIDTH, REFUEL_ICON_WIDTH);
				}
			}
		}
	}

	static Builder builder() {
		return new AutoValue_TaxiRenderer_Builder();
	}

	@AutoValue
	abstract static class Builder extends AbstractModelBuilder<TaxiRenderer, Void> {
 
		Builder() {
			setDependencies(RoadModel.class, PDPModel.class);
		}

		public TaxiRenderer build(DependencyProvider dependencyProvider) {
			final RoadModel rm = dependencyProvider.get(RoadModel.class);
			final PDPModel pm = dependencyProvider.get(PDPModel.class);
			return new TaxiRenderer(rm, pm);
		}
	}
	
	public static Composite getViewer() {
	    Display display = Display.getDefault();
	    Shell shell = display.getActiveShell();
	    return getViewer(shell);
	}
	
	public static Composite getViewer(Widget w) {
		Composite result = null;
		if (w == null)
			return null;
		if (w.getClass().getSimpleName().equals("SimulationViewer"))
			return (Composite)w;
		else if (w instanceof Composite)
			for (Widget w2 : ((Composite)w).getChildren()) {
				result = getViewer(w2);
				if (result != null)
					return result;
			}
		return result;
	}
}
