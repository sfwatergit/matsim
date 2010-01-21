/* *********************************************************************** *
 * project: org.matsim.*
 * PersonAgent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.ptproject.qsim;

import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.events.ActivityEndEventImpl;
import org.matsim.core.events.ActivityStartEventImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.population.routes.RouteWRefs;
import org.matsim.core.utils.misc.Time;

/**
 * @author dgrether
 */
public class PersonAgent implements DriverAgent {

	private static final Logger log = Logger.getLogger(PersonAgent.class);

	private final Person person;
	private QueueVehicle vehicle;
	protected Id cachedNextLinkId = null;

	private final QueueSimulation simulation;

	private double activityDepartureTime = Time.UNDEFINED_TIME;

	private Id currentLinkId = null;

	private int currentPlanElementIndex = 0;

	private transient Id destinationLinkId;

	private LegImpl currentLeg;
	private List<Node> cacheRouteNodes = null;

	private int currentNodeIndex;

	public PersonAgent(final Person p, final QueueSimulation simulation) {
		this.person = p;
		this.simulation = simulation;
	}

	public Person getPerson() {
		return this.person;
	}

	/**
	 * Convenience method delegating to person's selected plan
	 * @return list of {@link ActivityImpl}s and {@link LegImpl}s of this agent's plan
	 */
	private List<? extends PlanElement> getPlanElements() {
		return this.person.getSelectedPlan().getPlanElements();
	}

	public void setVehicle(final QueueVehicle veh) {
		this.vehicle = veh;
	}

	public QueueVehicle getVehicle() {
		return this.vehicle;
	}

	public double getDepartureTime() {
		return this.activityDepartureTime;
	}

	private void setDepartureTime(final double seconds) {
		this.activityDepartureTime = seconds;
	}

	protected Id getCurrentLinkId() {
		return this.currentLinkId;
	}

	public Leg getCurrentLeg() {
		return this.currentLeg;
	}

	protected void setCurrentLeg(final LegImpl leg) {
		this.currentLeg  = leg;
		this.cacheRouteNodes = null;
	}

	public int getCurrentNodeIndex() {
		return this.currentNodeIndex;
	}

	@Override
	public Id getDestinationLinkId() {
		return this.destinationLinkId;
	}

	public boolean initialize() {
		List<? extends PlanElement> planElements = this.getPlanElements();
		this.currentPlanElementIndex = 0;
		ActivityImpl firstAct = (ActivityImpl) planElements.get(0);
		double departureTime = firstAct.getEndTime();
		
		this.currentLinkId = firstAct.getLinkId();
		if ((departureTime != Time.UNDEFINED_TIME) && (planElements.size() > 1)) {
			setDepartureTime(departureTime);
//			SimulationTimer.updateSimStartTime(departureTime);
			this.simulation.scheduleActivityEnd(this, this.currentPlanElementIndex);
			Simulation.incLiving();
			return true;
		}
		return false; // the agent has no leg, so nothing more to do
	}

	private void initNextLeg(double now, final LegImpl leg) {
		RouteWRefs route = leg.getRoute();
		if (route == null) {
			log.error("The agent " + this.getPerson().getId() + " has no route in its leg. Removing the agent from the simulation.");
			Simulation.decLiving();
			Simulation.incLost();
			return;
		}
		this.destinationLinkId = route.getEndLinkId();

		// set the route according to the next leg
		this.currentLeg = leg;
		this.cacheRouteNodes = null;
		this.currentNodeIndex = 1;
		this.cachedNextLinkId = null;

		this.simulation.agentDeparts(now, this, this.currentLinkId);
	}

	/**
	 * Notifies the agent that it leaves its current activity location (and
	 * accordingly starts moving on its current route).
	 *
	 * @param now the current time
	 */
	public void activityEnds(final double now) {
		ActivityImpl act = (ActivityImpl) this.getPlanElements().get(this.currentPlanElementIndex);
		this.simulation.getEventsManager().processEvent(new ActivityEndEventImpl(now, this.getPerson().getId(), act.getLinkId(), act));
		advancePlanElement(now);
	}

	public void legEnds(final double now) {
		this.simulation.handleAgentArrival(now, this);
		if (this.currentLinkId != this.destinationLinkId) {
			log.error("The agent " + this.getPerson().getId() + " has destination link " + this.destinationLinkId
					+ ", but arrived on link " + this.currentLinkId + ". Removing the agent from the simulation.");
			Simulation.decLiving();
			Simulation.incLost();
			return;
		}
		advancePlanElement(now);
	}

	public void teleportToLink(final Id linkId) {
		this.currentLinkId = linkId;
	}

	private void advancePlanElement(final double now) {
		this.currentPlanElementIndex++;

		PlanElement pe = this.getPlanElements().get(this.currentPlanElementIndex);
		if (pe instanceof ActivityImpl) {
			reachActivity(now, (ActivityImpl) pe);

			if ((this.currentPlanElementIndex+1) < this.getPlanElements().size()) {
				// there is still at least on plan element left
				this.simulation.scheduleActivityEnd(this, this.currentPlanElementIndex);
			} else {
				// this is the last activity
				Simulation.decLiving();
			}

		} else if (pe instanceof LegImpl) {
			initNextLeg(now, (LegImpl) pe);
		} else {
			throw new RuntimeException("Unknown PlanElement of type " + pe.getClass().getName());
		}
	}

	/**
	 * Notifies the agent that it reaches its aspired activity location.
	 *
	 * @param now the current time
	 * @param act the activity the agent reaches
	 */
	private void reachActivity(final double now, final ActivityImpl act) {
		QueueSimulation.getEvents().processEvent(new ActivityStartEventImpl(now, this.getPerson().getId(),  this.currentLinkId, act));
		/* schedule a departure if either duration or endtime is set of the activity.
		 * Otherwise, the agent will just stay at this activity for ever...
		 */
		if ((act.getDuration() == Time.UNDEFINED_TIME) && (act.getEndTime() == Time.UNDEFINED_TIME)) {
			setDepartureTime(Double.POSITIVE_INFINITY);
		} else {

			double departure = 0;

			if (this.simulation.isUseActivityDurations()) {
				/* The person leaves the activity either 'actDur' later or
				 * when the end is defined of the activity, whatever comes first. */
				if (act.getDuration() == Time.UNDEFINED_TIME) {
					departure = act.getEndTime();
				} else if (act.getEndTime() == Time.UNDEFINED_TIME) {
					departure = now + act.getDuration();
				} else {
					departure = Math.min(act.getEndTime(), now + act.getDuration());
				}
			}
			else {
				if (act.getEndTime() != Time.UNDEFINED_TIME) {
					departure = act.getEndTime() ;
				}
				else {
					throw new IllegalStateException("Can not use activity end time as new departure time as it is not set for person: " + this.getPerson().getId());
				}
			}



			if (departure < now) {
				// we cannot depart before we arrived, thus change the time so the timestamp in events will be right
				departure = now;
				// actually, we will depart in (now+1) because we already missed the departing in this time step
			}
			setDepartureTime(departure);
		}
	}

	public void moveOverNode() {
		this.currentLinkId = this.cachedNextLinkId;
		this.currentNodeIndex++;
		this.cachedNextLinkId = null; //reset cached nextLink
	}

	/**
	 * Returns the next link the vehicle will drive along.
	 *
	 * @return The next link the vehicle will drive on, or null if an error has happened.
	 */
	public Id chooseNextLinkId() {
		if (this.cachedNextLinkId != null) {
			return this.cachedNextLinkId;
		}
		if (this.cacheRouteNodes == null) {
			this.cacheRouteNodes = ((NetworkRouteWRefs) this.currentLeg.getRoute()).getNodes();
		}

		if (this.currentNodeIndex >= this.cacheRouteNodes.size() ) {
			// we have no more information for the route, so we should have arrived at the destination link
			Link currentLink = this.simulation.networkLayer.getLinks().get(this.currentLinkId);
			Link destinationLink = this.simulation.networkLayer.getLinks().get(this.destinationLinkId);
			if (currentLink.getToNode().equals(destinationLink.getFromNode())) {
				this.cachedNextLinkId = destinationLink.getId();
				return this.cachedNextLinkId;
			}
			if (!(this.currentLinkId.equals(this.destinationLinkId))) {
				// there must be something wrong. Maybe the route is too short, or something else, we don't know...
				log.error("The vehicle with driver " + this.getPerson().getId() + ", currently on link " + this.currentLinkId.toString()
						+ ", is at the end of its route, but has not yet reached its destination link " + this.destinationLinkId.toString());
			}
			return null;
		}

		Node destNode = this.cacheRouteNodes.get(this.currentNodeIndex);

		Link currentLink = this.simulation.networkLayer.getLinks().get(this.currentLinkId);
		for (Link link :  currentLink.getToNode().getOutLinks().values()) {
			if (link.getToNode() == destNode) {
				this.cachedNextLinkId = link.getId(); //save time in later calls, if link is congested
				return this.cachedNextLinkId;
			}
		}
		log.warn(this + " [no link to next routenode found: routeindex= " + this.currentNodeIndex + " ]");
		return null;
	}

}
