/* *********************************************************************** *
 * project: org.matsim.*
 * RunEmissionToolOffline.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package playground.jbischoff.taxi.usability;

import java.util.List;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.MatsimVrpContext;
import org.matsim.contrib.dvrp.MatsimVrpContextImpl;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.data.VehicleImpl;
import org.matsim.contrib.dvrp.extensions.taxi.TaxiUtils;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.router.DistanceAsTravelDisutility;
import org.matsim.contrib.dvrp.router.LeastCostPathCalculatorWithCache;
import org.matsim.contrib.dvrp.router.VrpPathCalculator;
import org.matsim.contrib.dvrp.router.VrpPathCalculatorImpl;
import org.matsim.contrib.dvrp.run.VrpLauncherUtils;
import org.matsim.contrib.dvrp.util.time.TimeDiscretizer;
import org.matsim.contrib.dvrp.vrpagent.VrpLegs;
import org.matsim.contrib.dvrp.vrpagent.VrpLegs.LegCreator;
import org.matsim.contrib.dynagent.run.DynAgentLauncherUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

import playground.michalm.taxi.TaxiActionCreator;
import playground.michalm.taxi.TaxiRequestCreator;
import playground.michalm.taxi.optimizer.TaxiOptimizerConfiguration;
import playground.michalm.taxi.optimizer.TaxiOptimizerConfiguration.Goal;
import playground.michalm.taxi.optimizer.filter.DefaultFilterFactory;
import playground.michalm.taxi.optimizer.filter.FilterFactory;
import playground.michalm.taxi.optimizer.rules.RuleBasedTaxiOptimizer;
import playground.michalm.taxi.scheduler.TaxiScheduler;
import playground.michalm.taxi.scheduler.TaxiSchedulerParams;
import playground.michalm.taxi.vehreqpath.VehicleRequestPathFinder;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @author jbischoff
 *
 */

public class TaxiQSimProvider implements Provider<QSim> {
	private TaxiConfigGroup tcg;
	private MatsimVrpContextImpl context;
	private RuleBasedTaxiOptimizer optimizer;
	private EventsManager events;
	private TravelTime travelTime;

	@Inject
	TaxiQSimProvider(Config config, MatsimVrpContext context , EventsManager events, TravelTime travelTime) {
		this.tcg = (TaxiConfigGroup) config.getModule("taxiConfig");
		this.context = (MatsimVrpContextImpl) context;
		this.events=events;
		this.travelTime = travelTime;

	}

	private QSim createMobsim(Scenario sc, EventsManager eventsManager) {
		initiate();
		QSim qSim = DynAgentLauncherUtils.initQSim(sc, eventsManager);
		qSim.addQueueSimulationListeners(optimizer);
		context.setMobsimTimer(qSim.getSimTimer());
		PassengerEngine passengerEngine = VrpLauncherUtils.initPassengerEngine(
				TaxiUtils.TAXI_MODE, new TaxiRequestCreator(), optimizer,
				context, qSim);
		LegCreator legCreator = VrpLegs.createLegWithOfflineTrackerCreator(qSim
				.getSimTimer());
		TaxiActionCreator actionCreator = new TaxiActionCreator(
				passengerEngine, legCreator, tcg.getPickupDuration());
		VrpLauncherUtils.initAgentSources(qSim, context, optimizer,
				actionCreator);
		return qSim;
	}

	void initiate() {
		//this initiation takes place upon creating qsim for each iteration
		TravelDisutility travelDisutility = new DistanceAsTravelDisutility();
		
		
		TaxiSchedulerParams params = new TaxiSchedulerParams(tcg.isDestinationKnown(), tcg.isVehicleDiversion(),
				tcg.getPickupDuration(), tcg.getDropoffDuration());
		
		resetSchedules(context.getVrpData().getVehicles());
		
		LeastCostPathCalculator router = new Dijkstra(context.getScenario()
				.getNetwork(), travelDisutility, travelTime);

		LeastCostPathCalculatorWithCache routerWithCache = new LeastCostPathCalculatorWithCache(
				router, new TimeDiscretizer(31 * 4, 15 * 60, false));

		VrpPathCalculator calculator = new VrpPathCalculatorImpl(
				routerWithCache, travelTime, travelDisutility);
		TaxiScheduler scheduler = new TaxiScheduler(context, calculator, params);
		VehicleRequestPathFinder vrpFinder = new VehicleRequestPathFinder(
				calculator, scheduler);

		FilterFactory filterFactory = new DefaultFilterFactory(scheduler, tcg.getNearestRequestsLimit(), tcg.getNearestVehiclesLimit());

		TaxiOptimizerConfiguration optimConfig = new TaxiOptimizerConfiguration(
				context, calculator, scheduler, vrpFinder, filterFactory,
				Goal.MIN_WAIT_TIME, tcg.getOutputDir());
		optimizer = new RuleBasedTaxiOptimizer(optimConfig);

	}
	
	private void resetSchedules(List<Vehicle> vehicles) {

    	for (Vehicle v : vehicles){
    		VehicleImpl vi = (VehicleImpl) v;
    		vi.resetSchedule();
    		
    	}
	}

	@Override
	public QSim get() {
		return createMobsim(context.getScenario(), this.events);
	}

}
