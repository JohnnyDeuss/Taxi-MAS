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

import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.CanvasRenderer.AbstractCanvasRenderer;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.google.auto.value.AutoValue;

import taxi.Customer.CustomerState;

public class CustomerRenderer extends AbstractCanvasRenderer {

	private RoadModel _rm;
	
	CustomerRenderer(RoadModel rm) {
		_rm = rm;
	}

	public void renderStatic(GC gc, ViewPort vp) { }
	
	public void renderDynamic(GC gc, ViewPort vp, long time) {
		final Set<Customer> customers = _rm.getObjectsOfType(Customer.class);

		synchronized (customers) {
			for (Customer c : customers) {
				if (c.getState() == CustomerState.MISSED) {
					Point p = c.getPickupLocation();
					gc.setLineWidth(2);
					gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_RED));
					gc.drawLine(vp.toCoordX(p.x)-10, vp.toCoordY(p.y)-10, vp.toCoordX(p.x)+10, vp.toCoordY(p.y)+10);
					gc.drawLine(vp.toCoordX(p.x)-10, vp.toCoordY(p.y)+10, vp.toCoordX(p.x)+10, vp.toCoordY(p.y)-10);
				}
			}
		}
	}

	static Builder builder() {
		return new AutoValue_CustomerRenderer_Builder();
	}

	@AutoValue
	abstract static class Builder extends AbstractModelBuilder<CustomerRenderer, Void> {
 
		Builder() {
			setDependencies(RoadModel.class);
		}

		public CustomerRenderer build(DependencyProvider dependencyProvider) {
			final RoadModel rm = dependencyProvider.get(RoadModel.class);
			return new CustomerRenderer(rm);
		}
	}
}
