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
package gradient;

import org.apache.commons.math3.linear.RealVector;

import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public interface FieldEmitter {
	
	void setModel(GradientModel model);

	Optional<Point> getPosition();

	double getStrength();
	
	double getRange();
	
	RealVector getField(Point p);
}
