/* *********************************************************************** *
 * project: org.matsim.*
 * ExtractTripModeShares30kmFromBellevue.java
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
package playground.thibautd.socnetsim.analysis.scripts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.facilities.MatsimFacilitiesReader;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.RouteUtils;
import org.matsim.population.algorithms.PersonAlgorithm;
import org.matsim.pt.PtConstants;

import playground.thibautd.socnetsim.population.JointActingTypes;
import playground.thibautd.socnetsim.utils.JointMainModeIdentifier;

/**
 * @author thibautd
 */
public class ExtractTripModeShares20kmFromBellevue {
	private static final Coord BELLEVUE_COORD = new CoordImpl( 683518 , 246836 );
	private static final double radius = 20000;
	private static final StageActivityTypes STAGES =
		new StageActivityTypesImpl(
				Arrays.asList(
					PtConstants.TRANSIT_ACTIVITY_TYPE,
					JointActingTypes.INTERACTION,
					JointActingTypes.INTERACTION ) );
	private static final MainModeIdentifier MODE_IDENTIFIER = 
		new MainModeIdentifier() {
			final MainModeIdentifier delegate = new JointMainModeIdentifier( new MainModeIdentifierImpl() );

			@Override
			public String identifyMainMode(final List<PlanElement> tripElements) {
				if ( tripElements.size() == 1 &&
						((Leg) tripElements.get( 0 )).getMode().equals( TransportMode.transit_walk ) ) {
					return TransportMode.walk;
				}
				return delegate.identifyMainMode( tripElements );
			}
		};

	// MZ: just use ht ecrow fly distance
	private static final double CROW_FLY_FACTOR = 1;
	private static final boolean USE_NET_DIST = false;

	// TODO pass this as argument
	private static final Filter FILTER = true ? new ODFilter() : new HomeCoordFilter();

	public static void main(final String[] args) throws IOException {
		final String plansFile = args[ 0 ];
		final String outputFile = args[ 1 ];
		// for V4 or distance computation
		final String networkFile = args.length > 2 ? args[ 2 ] : null;
		// useful for V4 only
		final String facilitiesFile = args.length > 3 ? args[ 3 ] : null;

		final Scenario scenario = ScenarioUtils.createScenario( ConfigUtils.createConfig() );
		if ( facilitiesFile != null ) new MatsimFacilitiesReader( scenario ).parse( facilitiesFile );
		if ( networkFile != null ) new MatsimNetworkReader( scenario ).parse( networkFile );

		final PopulationImpl pop = (PopulationImpl) scenario.getPopulation();

		final BufferedWriter writer = IOUtils.getBufferedWriter( outputFile );
		writer.write( "agentId\tmain_mode\ttotal_dist" );

		pop.setIsStreaming( true );
		pop.addAlgorithm( new PersonAlgorithm() {
			@Override
			public void run(Person person) {
				final Plan plan = person.getSelectedPlan();
				if ( !FILTER.acceptPlan( plan ) ) return;
				for ( Trip trip : TripStructureUtils.getTrips( plan , STAGES ) ) {
					if ( !FILTER.acceptTrip( trip ) ) continue;
					final String mode = MODE_IDENTIFIER.identifyMainMode( trip.getTripElements() );
					try {
						writer.newLine();
						writer.write( person.getId()+"\t"+mode+"\t"+calcDist( trip , scenario.getNetwork() ) );
					}
					catch (IOException e) {
						throw new RuntimeException( e );
					}
				}			
			}
		} );
		new MatsimPopulationReader( scenario ).parse( plansFile );

		writer.close();
	}

	private static double calcDist(final Trip trip, final Network network) {
		double dist = 0;

		for ( Leg l : trip.getLegsOnly() ) {
			final Route r = l.getRoute();
			if ( USE_NET_DIST && r instanceof NetworkRoute )  {
				dist += RouteUtils.calcDistance( (NetworkRoute) r , network );
			}
			else {
				// TODO: make configurable?
				dist += CROW_FLY_FACTOR *
					// TODO: use coord of activities
					CoordUtils.calcDistance(
							network.getLinks().get( r.getStartLinkId() ).getFromNode().getCoord(),
							network.getLinks().get( r.getEndLinkId() ).getToNode().getCoord() );
			}
		}

		return dist;
	}

	private static interface Filter {
		public boolean acceptPlan(final Plan plan);
		public boolean acceptTrip(final Trip trip);
	}

	private static class HomeCoordFilter implements Filter {
		@Override
		public boolean acceptPlan(final Plan plan) {
			final Activity act = getHomeActivity( plan );
			return CoordUtils.calcDistance( act.getCoord() , BELLEVUE_COORD ) <= radius;
		}

		@Override
		public boolean acceptTrip(final Trip trip) {
			return true;
		}

		private static Activity getHomeActivity(final Plan plan) {
			final Activity activity = (Activity) plan.getPlanElements().get( 0 );
			if ( !activity.getType().equals( "home" ) ) throw new IllegalArgumentException( ""+plan.getPlanElements() );
			return activity;
		}
	}

	private static class ODFilter implements Filter {
		@Override
		public boolean acceptPlan(final Plan plan) {
			return true;
		}

		@Override
		public boolean acceptTrip(final Trip trip) {
			return CoordUtils.calcDistance( trip.getOriginActivity().getCoord() , BELLEVUE_COORD ) <= radius &&
				 CoordUtils.calcDistance( trip.getDestinationActivity().getCoord() , BELLEVUE_COORD ) <= radius;
		}
	}
}
