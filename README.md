# Multi-agent system for taxi distribution

The aim of this project is to implement a smart system in which taxis,
or similar transportation services, work together as a multi-agent system.
The aim of the system is to optimize service rates by distributing taxis
along the underlying distribution from which passengers are generated.
This is done by a combination of a contract net for taxi assignment,
empirical distribution learning and agent gradient fields.

The simulator takes use of fuel and refueling into account, as well as the
fact that users will move on if they are not picked up on time.

The assignment of taxis is done by use of a contract net. The assignment
to a taxi can be done using a number of weight functions:
* Minimize waiting time for passengers.
* Minimize distance travelled by taxis.
* Maximize distance travelled with passenger, especially when there's more
  passengers than taxis.

The system learns the distribution along which passengers spawn, e.g. more
users spawn at railway stations and business centers. The learned distribution
is then a 2D map that is overlayed on the city map and can be used to compute
gradients, e.g. the railway station would be at the bottom of a deep valley on
the inverted distribution. The agents then follow these gradients, causing them
to move to busy areas.

The real magic happens when we combine the learned distribution with agent
gradient fields. Without additional gradient fields, most taxis would find
themselves at the same local minima, meaning a lot of areas are poorly serviced.
To solve this, each taxi has a gradient field, which can be seen as hills on
the inverted distribution map, around the taxi, pushing other taxis away.
The shape and strenght of these fields can be configured. The fields can also
be adaptive, where sparse areas on the distribution map cause the gradient field
around a taxi to be stronger, to compensate for the area around the city center
increasing more the further out we go. The combined gradients cause taxis to
distribute themselves along the passenger distribution, but also giving each other
some space. Taxis move when the gradient they are on is steeper than a given
threshold.

## Examples:
The simulator, showing the underlying passenger distribution as the color of the map
(blue to red), gas stations, passengers, gradient fields (as circles around taxis),
the taxis themselves and the direction in which the system is pushing the taxis to move
(as a yellow line pointing out from a taxi).

![Five taxis](/images/5.png)
![Ten taxis](/images/10.png)
![Twenty taxis](/images/20.png)
