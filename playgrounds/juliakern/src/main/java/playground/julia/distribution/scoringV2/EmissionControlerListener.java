/* *********************************************************************** *
 * project: org.matsim.*
 * EmissionControlerListener.java
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
package playground.julia.distribution.scoringV2;

import java.util.ArrayList;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ScoringEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ScoringListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.events.algorithms.EventWriterXML;

import playground.vsp.emissions.types.ColdPollutant;
import playground.vsp.emissions.types.WarmPollutant;

/**
 * @author benjamin
 *
 */
public class EmissionControlerListener implements StartupListener, IterationStartsListener, ShutdownListener, ScoringListener{
	private static final Logger logger = Logger.getLogger(EmissionControlerListener.class);
	
	Controler controler;
	String emissionEventOutputFile;
	Integer lastIteration;
	EmissionModule emissionModule;
	EventWriterXML emissionEventWriter;
	IntervalHandler intervalHandler = new IntervalHandler();
	GeneratedEmissionsHandler geh;

	private ArrayList<ResponsibilityEvent> resp;

	Double xMin = 4452550.25;
	Double xMax = 4479483.33;
	Double yMin = 5324955.00;
	Double yMax = 5345696.81;
	
	Integer noOfXCells = 160;
	Integer noOfYCells = 120;

	Double timeBinSize;
	Integer noOfTimeBins =30;
	Map<Id, Integer> links2xcells;
	Map<Id, Integer> links2ycells;

	public EmissionControlerListener() {

	}

	@Override
	public void notifyStartup(StartupEvent event) {
		controler = event.getControler();
		lastIteration = controler.getLastIteration();
		logger.info("emissions will be calculated for iteration " + lastIteration);
		
		logger.info("mapping links to cells");
		GridTools gt = new GridTools(controler.getNetwork().getLinks(), xMin, xMax, yMin, yMax);
		links2xcells = gt.mapLinks2Xcells(noOfXCells);
		links2ycells = gt.mapLinks2Ycells(noOfYCells);
		
		
		Scenario scenario = controler.getScenario() ;
		emissionModule = new EmissionModule(scenario);
		emissionModule.createLookupTables();
		emissionModule.createEmissionHandler();
		
		EventsManager eventsManager = controler.getEvents();
		eventsManager.addHandler(emissionModule.getWarmEmissionHandler());
		eventsManager.addHandler(emissionModule.getColdEmissionHandler());
		
		eventsManager.addHandler(intervalHandler);
		
		Double simulationEndTime = controler.getConfig().qsim().getEndTime();
		timeBinSize = simulationEndTime/noOfTimeBins;
		
		geh = new GeneratedEmissionsHandler(0.0, timeBinSize, links2xcells, links2ycells, WarmPollutant.NO2, ColdPollutant.NO2);
		//eventsManager.addHandler(geh);
		emissionModule.emissionEventsManager.addHandler(geh);
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		Integer iteration = event.getIteration();

		if(lastIteration.equals(iteration)){
			emissionEventOutputFile = controler.getControlerIO().getIterationFilename(iteration, "emission.events.xml.gz");
			logger.info("creating new emission events writer...");
			emissionEventWriter = new EventWriterXML(emissionEventOutputFile);
			logger.info("adding emission events writer to emission events stream...");
			emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);
		}
	}

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		logger.info("closing emission events file...");
		try {
			emissionEventWriter.closeFile();
		} catch (NullPointerException e) {
			logger.warn("No file to close. Is this intended?");
		}
		emissionModule.writeEmissionInformation(emissionEventOutputFile);
	}


	public ArrayList<ResponsibilityEvent> getResp() {
		return resp;
	}

	@Override
	public void notifyScoring(ScoringEvent event) {
		logger.info("before scoring. starting resp calc.");
		
		
		
		Double simulationEndTime = controler.getConfig().qsim().getEndTime();
		timeBinSize = simulationEndTime/noOfTimeBins;

		intervalHandler.addActivitiesToTimetables(links2xcells, links2ycells, simulationEndTime);

	//	System.out.println("+++++++++++++ " + intervalHandler.getActivities().size()); // sp-> 23
		
		
//		for(ColdEmissionEvent coldEvent: seh.getColdEmissionEvents()){
//			geh.handleEvent(coldEvent);
//		}
//		for(WarmEmissionEvent warmEvent: emissionModule.getWarmEmissionHandler().getWarmEvents()){
//			geh.handleEvent(warmEvent);
//		}
		
		System.out.println(intervalHandler.getActivities().size() + "activities");
		System.out.println(geh.getEmissionsPerCell().size());
		ResponsibilityUtils reut = new ResponsibilityUtils();
		resp = new ArrayList<ResponsibilityEvent>();
		reut.addExposureAndResponsibilityBinwise(intervalHandler.getActivities(), geh.getEmissionsPerCell(), resp, timeBinSize, controler.getConfig().qsim().getEndTime());
		System.out.println("---------------------------" + resp.size());
		
	}
	
}