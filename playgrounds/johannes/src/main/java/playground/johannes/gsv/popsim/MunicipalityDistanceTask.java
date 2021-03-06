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

package playground.johannes.gsv.popsim;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TDoubleDoubleHashMap;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import playground.johannes.gsv.synPop.CommonKeys;
import playground.johannes.gsv.synPop.analysis.AnalyzerTask;
import playground.johannes.gsv.synPop.mid.MIDKeys;
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
public class MunicipalityDistanceTask extends AnalyzerTask {
    @Override
    public void analyze(Collection<PlainPerson> persons, Map<String, DescriptiveStatistics> results) {
        TDoubleArrayList xVals = new TDoubleArrayList();
        TDoubleArrayList yVals = new TDoubleArrayList();

        for(PlainPerson person : persons) {
            String xStr = person.getAttribute(MIDKeys.PERSON_MUNICIPALITY_CLASS);
            for(Episode plan : person.getEpisodes()) {
                for(Element leg : plan.getLegs()) {

                    String yStr = leg.getAttribute(CommonKeys.LEG_ROUTE_DISTANCE);

                    if(xStr != null && yStr != null) {
                        xVals.add(Double.parseDouble(xStr));
                        yVals.add(Double.parseDouble(yStr));
                    }
                }
            }
        }

        if(outputDirectoryNotNull()) {
            try {
                String filename = String.format("%s/munic.dist.mean.txt", getOutputDirectory());
                double[] x = xVals.toNativeArray();
                double[] y = yVals.toNativeArray();
//                Discretizer disc = FixedSampleSizeDiscretizer.create(x, 50, 100);
                TDoubleDoubleHashMap corr = Correlations.mean(x, y);
                TXTWriter.writeMap(corr, "munic", "distance", filename);

                filename = String.format("%s/munic.dist.scatter.txt", getOutputDirectory());
                TXTWriter.writeScatterPlot(xVals, yVals, "munic", "distance", filename);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
