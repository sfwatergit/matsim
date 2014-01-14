/* *********************************************************************** *
 * project: org.matsim.*
 * SpatialAveragingForLinkEmissions.java
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
package playground.julia.distributionV1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

import playground.julia.exposure.EmActivity;
import playground.julia.exposure.EmPerCell;
import playground.julia.exposure.ResponsibilityEvent;
import playground.vsp.emissions.events.EmissionEventsReader;
import playground.vsp.emissions.types.ColdPollutant;
import playground.vsp.emissions.types.WarmPollutant;

/**
 * @author benjamin, julia
 *
 */
public class SpatialAveragingDistribution {
	private static final Logger logger = Logger.getLogger(SpatialAveragingDistribution.class);

	Map<Id, Integer> link2xbin;
	Map<Id, Integer> link2ybin;
	
	Map<Double, ArrayList<EmPerCell>> emissionPerBin ;
	Map<Double, ArrayList<EmPerLink>> emissionPerLink;
	ArrayList<ResponsibilityEvent> responsibilityAndExposure;

	private void run() throws IOException{
		
		/*
		 * set up
		 * configure parameters, set paths
		 * 
		 */
		
		DistributionConfiguration distConfig = new DistributionConfig();
//		DistributionConfiguration distConfig = new DistributionConfigTest(logger);
		Double simulationEndTime = distConfig.getSimulationEndTime();
		int noOfTimeBins = distConfig.getNoOfTimeBins();
		Double timeBinSize = distConfig.getSimulationEndTime()/noOfTimeBins;
		
		String outPathStub = distConfig.getOutPathStub();
			
		/*
		 * generate two lists to store location of links 
		 * needed frequently
		 */
		
		link2xbin = distConfig.getLink2xBin();
		link2ybin = distConfig.getLink2yBin();
		
		/*
		 * four lists to store information on activities, car trips and emissions
		 * activities: time interval, person id, location - from events
		 * car trips: time interval, person id, link id - from events
		 * emission per bin: time bin, area bin, responsible person id - from emission events
		 * emission per link: time bin, link id, responsible person id - from emission events 
		 * 
		 * TODO later: write emission per bin/cell into xml?
		 * TODO later: write emission per link into xml?
		 * 
		 */
		
		ArrayList<EmActivity> activities = new ArrayList<EmActivity>();
		ArrayList<EmCarTrip> carTrips= new ArrayList<EmCarTrip>();
		
		emissionPerBin = new HashMap<Double, ArrayList<EmPerCell>>();
		emissionPerLink = new HashMap<Double, ArrayList<EmPerLink>>();
		
		generateActivitiesAndTrips(distConfig.getEventsFile(), activities, carTrips, simulationEndTime);
		generateEmissions(distConfig.getEmissionFile(), distConfig);
		
		logger.info("Done calculating emissions per bin and link.");
		
		/*
		 * two lists to store information on exposure and responsibility 
		 * responsibility: responsible person id, emission value x time - from  (car trips and emission per link), (activities and emission per bin)
		 * exposure: person id, emission value x time - from  (car trips and emission per link), (activities and emission per bin)
		 * 
		 * TODO later: write emission per bin/link into xml... handle as responsibility events
		 * TODO later: write emission per bin/link into xml... handle as exposure events
		 */
		
		responsibilityAndExposure = new ArrayList<ResponsibilityEvent>();
		
		ResponsibilityUtils reut = new ResponsibilityUtils();
		reut.addExposureAndResponsibilityBinwise(activities, emissionPerBin, responsibilityAndExposure, timeBinSize, simulationEndTime);
		//reut.addExposureAndResponsibilityLinkwise(carTrips, emissionPerLink, responsibilityAndExposure, timeBinSize, simulationEndTime);
		
		logger.info("Done calculating responsibility events.");
		
		if(distConfig.storeResponsibilityEvents()){
			String outPathForResponsibilityEvents = distConfig.getOutPathStub() + "responsibilityEvents.xml";
			writeResponsibilityEventsToXml(responsibilityAndExposure, outPathForResponsibilityEvents );
		}
		
		/*
		 * analysis
		 * exposure analysis: sum, average, welfare, personal time table
		 * responsibility analysis: sum, average, welfare
		 *  TODO: welfare!
		 */
		
		ExposureUtils exut = new ExposureUtils();
		exut.printExposureInformation(responsibilityAndExposure, outPathStub+"exposure.txt");
		exut.printResponsibilityInformation(responsibilityAndExposure, outPathStub+"responsibility.txt");
		exut.printPersonalExposureInformation(responsibilityAndExposure, outPathStub+"personalExposure.txt");
		exut.printPersonalResponsibilityInformation(responsibilityAndExposure, outPathStub+"personalResponsibility.txt");
		
		logger.info("Finished writing output to "+ outPathStub +".");
		}

	private void writeResponsibilityEventsToXml(Collection<ResponsibilityEvent> responsibilityAndExposure, String responsibilityEventOutputFile) {
		
		ResponsibilityEventWriter rew = new ResponsibilityEventWriter(responsibilityEventOutputFile);
		
		for(ResponsibilityEvent ree: responsibilityAndExposure){
			rew.handleResponsibilityEvent(ree);
		}
		
		rew.closeFile();		
	}

	private void generateEmissions(String emissionFile, DistributionConfiguration distConfig) {
				
		EventsManager eventsManager = EventsUtils.createEventsManager();
		EmissionEventsReader emissionReader = new EmissionEventsReader(eventsManager);
		
		WarmPollutant warmPollutant2analyze = distConfig.getWarmPollutant2analyze();
		ColdPollutant coldPollutant2analyze = distConfig.getColdPollutant2analyze();
		GeneratedEmissionsHandler generatedEmissionsHandler = new GeneratedEmissionsHandler(0.0, distConfig.getTimeBinSize(), link2xbin, link2ybin, warmPollutant2analyze, coldPollutant2analyze);
		eventsManager.addHandler(generatedEmissionsHandler);
		
		emissionReader.parse(emissionFile);

		//TODO something wrong here, em per cell deleted???
		emissionPerBin = generatedEmissionsHandler.getEmissionsPerCell();
		emissionPerLink = generatedEmissionsHandler.getEmissionsPerLink();
		
	}

	private void generateActivitiesAndTrips(String eventsFile,
			ArrayList<EmActivity> activities, ArrayList<EmCarTrip> carTrips, Double simulationEndTime) {
		// from eventsfile -> car trips, activities: store
		EventsManager eventsManager = EventsUtils.createEventsManager();
		
		IntervalHandler intervalHandler = new IntervalHandler();
		eventsManager.addHandler(intervalHandler);
		
		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
				
		intervalHandler.addActivitiesToTimetables(activities, link2xbin, link2ybin, simulationEndTime);
		intervalHandler.addCarTripsToTimetables(carTrips, simulationEndTime);
		
		eventsManager.removeHandler(intervalHandler);
		
	}

	private Map<Id, Integer> calculateYbins(DistributionConfiguration distConfig) {
		Map<Id, Integer> link2ybin = new HashMap<Id, Integer>();
		for(Id linkId: distConfig.getLinks().keySet()){
			link2ybin.put(linkId, mapYCoordToBin(distConfig.getLinks().get(linkId).getCoord().getY(), distConfig));
		}
		return link2ybin;
	}

	private Map<Id, Integer> calculateXbins(DistributionConfiguration distConfig) {
		Map<Id, Integer> link2xbin = new HashMap<Id, Integer>();
		for(Id linkId: distConfig.getLinks().keySet()){
			link2xbin.put(linkId, mapXCoordToBin(distConfig.getLinks().get(linkId).getCoord().getX(), distConfig));
		}
		return link2xbin;
	}

	private Integer mapYCoordToBin(double yCoord, DistributionConfiguration distConfig) {
		double yMin = distConfig.getYmin();
		double yMax = distConfig.getYmax();
		if (yCoord <= yMin || yCoord >= yMax) return null; // yCoord is not in area of interest
		double relativePositionY = ((yCoord - yMin) / (yMax - yMin) * distConfig.getNumberOfYBins()); // gives the relative position along the y-range
		return (int) relativePositionY; // returns the number of the bin [0..n-1]
	}

	private Integer mapXCoordToBin(double xCoord, DistributionConfiguration distConfig) {
		double xMin = distConfig.getXmin();
		double xMax = distConfig.getXmax();
		if (xCoord <= xMin  || xCoord >= xMax) return null; // xCorrd is not in area of interest
		double relativePositionX = ((xCoord - xMin) / (xMax - xMin) * distConfig.getNumberOfXBins()); // gives the relative position along the x-range
		return (int) relativePositionX; // returns the number of the bin [0..n-1]
	}

	public static void main(String[] args) throws IOException{
		new SpatialAveragingDistribution().run();
	}
}