/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package playground.johannes.gsv.synPop.analysis;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TDoubleDoubleHashMap;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import playground.johannes.gsv.synPop.CommonKeys;
import playground.johannes.sna.util.TXTWriter;
import playground.johannes.socialnetworks.statistics.Correlations;
import playground.johannes.synpop.data.Element;
import playground.johannes.synpop.data.Episode;
import playground.johannes.synpop.data.PlainPerson;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author johannes
 */
public class DistanceStartTimeTask extends AnalyzerTask {
    @Override
    public void analyze(Collection<PlainPerson> persons, Map<String, DescriptiveStatistics> results) {
        TDoubleArrayList distVals = new TDoubleArrayList();
        TDoubleArrayList startVals = new TDoubleArrayList();

        for(PlainPerson person : persons) {
            for(Episode plan : person.getEpisodes()) {
                for(Element leg : plan.getLegs()) {
                    String xStr = leg.getAttribute(CommonKeys.LEG_ROUTE_DISTANCE);
                    String startVal = leg.getAttribute(CommonKeys.LEG_START_TIME);

                    if(xStr != null && startVal != null) {
                        distVals.add(Double.parseDouble(xStr));
                        startVals.add(Double.parseDouble(startVal));
                    }
                }
            }
        }

        if(outputDirectoryNotNull()) {
            try {
                TDoubleDoubleHashMap corr = Correlations.mean(startVals.toNativeArray(), distVals.toNativeArray(), 3600);
                TXTWriter.writeMap(corr, "startTime", "distance", getOutputDirectory() + "/distStartTime.txt");

                TXTWriter.writeScatterPlot(startVals, distVals, "startTime", "distance", getOutputDirectory() + "/distStartTime.scatter.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
