/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.julia.distribution.testScoringV3;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.scoring.ScoringFunction;

import playground.julia.exposure.ResponsibilityEvent;

public class ResponsibilityScoringFunction implements ScoringFunction {
	
	ScoringFunction delegate;
	private Plan plan;
	EmissionControlerListener ecl;
	
	public ResponsibilityScoringFunction(Plan plan, ScoringFunction scoringFunction, EmissionControlerListener ecl){
		this.plan=plan;
		this.delegate = scoringFunction;
		this.ecl=ecl;
	}

	@Override
	public void handleActivity(Activity activity) {
		delegate.handleActivity(activity);
	}

	@Override
	public void handleLeg(Leg leg) {
		delegate.handleLeg(leg);
	}

	@Override
	public void agentStuck(double time) {
		delegate.agentStuck(time);
	}

	@Override
	public void addMoney(double amount) {
		delegate.addMoney(amount);

	}

	@Override
	public void finish() {
		Id personId = plan.getPerson().getId();

		Double amount = new Double(.0);
		if(ecl!=null){
			if(ecl.getResp()!=null){
				for(ResponsibilityEvent re: ecl.getResp()){
					if(re.getResponsiblePersonId().equals(personId)){
						amount += re.getExposureValue();
					}
				}
			}
		}
		delegate.addMoney(-amount);
		delegate.finish();

	}

	@Override
	public double getScore() {
		return delegate.getScore();
	}

	@Override
	public void handleEvent(Event event) {
		delegate.handleEvent(event);

	}

}