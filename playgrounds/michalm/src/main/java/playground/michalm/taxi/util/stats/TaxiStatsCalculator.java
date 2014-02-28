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

package playground.michalm.taxi.util.stats;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.contrib.dvrp.data.*;
import org.matsim.contrib.dvrp.schedule.*;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;

import playground.michalm.taxi.schedule.*;


public class TaxiStatsCalculator
{
    public TaxiStats calculateStats(VrpData data)
    {
        TaxiStats evaluation = new TaxiStats();

        for (Vehicle v : data.getVehicles()) {
            evaluateSchedule(data, TaxiSchedules.getSchedule(v), evaluation);
        }

        return evaluation;
    }


    private void evaluateSchedule(VrpData data, Schedule<TaxiTask> schedule, TaxiStats eval)
    {
        if (schedule.getStatus() == ScheduleStatus.UNPLANNED) {
            return;// do not evaluate - the vehicle is unused
        }

        if (schedule.getTaskCount() < 1) {
            throw new RuntimeException("count=0 ==> must be unplanned!");
        }

        for (TaxiTask t : schedule.getTasks()) {
            double time = t.getEndTime() - t.getBeginTime();

            switch (t.getTaxiTaskType()) {
                case PICKUP_DRIVE:
                    eval.taxiPickupDriveTime += time;

                    if (eval.maxTaxiPickupDriveTime < time) {
                        eval.maxTaxiPickupDriveTime = time;
                    }

                    eval.taxiPickupDriveTimeStats.addValue(time);

                    break;

                case DROPOFF_DRIVE:
                    eval.taxiDropoffDriveTime += time;
                    break;

                case CRUISE_DRIVE:
                    eval.taxiCruiseTime += time;
                    break;

                case PICKUP_STAY:
                    eval.taxiPickupTime += time;

                    Request req = ((TaxiPickupStayTask)t).getRequest();
                    double waitTime = t.getBeginTime() - req.getT0();
                    eval.passengerWaitTime += waitTime;

                    if (eval.maxPassengerWaitTime < waitTime) {
                        eval.maxPassengerWaitTime = waitTime;
                    }

                    eval.passengerWaitTimeStats.addValue(waitTime);

                    break;

                case DROPOFF_STAY:
                    eval.taxiDropoffTime += time;
                    break;

                case WAIT_STAY:
                    eval.taxiWaitTime += time;
            }
        }

        double latestValidEndTime = schedule.getVehicle().getT1();
        double actualEndTime = schedule.getEndTime();

        eval.taxiOverTime += Math.max(actualEndTime - latestValidEndTime, 0);
    }


    public static class TaxiStats
    {
        public static final String HEADER = "PickupDriveT\t" //
                + "MaxPickupDriveT" //
                + "DeliveryDriveT\t"//
                + "PickupT\t" //
                + "DropoffT\t" //
                + "CruiseT\t" //
                + "WaitT\t" //
                + "OverT\t" //
                + "PassengerWaitT\t" //
                + "MaxPassengerWaitT";

        private double taxiPickupDriveTime;
        private double taxiDropoffDriveTime;
        private double taxiPickupTime;
        private double taxiDropoffTime;
        private double taxiCruiseTime;
        private double taxiWaitTime;
        private double taxiOverTime;
        private double passengerWaitTime;

        private double maxTaxiPickupDriveTime;
        private double maxPassengerWaitTime;

        private final DescriptiveStatistics taxiPickupDriveTimeStats = new DescriptiveStatistics();
        private final DescriptiveStatistics passengerWaitTimeStats = new DescriptiveStatistics();


        public double getTaxiPickupDriveTime()
        {
            return taxiPickupDriveTime;
        }


        public double getTaxiDropoffDriveTime()
        {
            return taxiDropoffDriveTime;
        }


        public double getTaxiPickupTime()
        {
            return taxiPickupTime;
        }


        public double getTaxiDropoffTime()
        {
            return taxiDropoffTime;
        }


        public double getTaxiCruiseTime()
        {
            return taxiCruiseTime;
        }


        public double getTaxiWaitTime()
        {
            return taxiWaitTime;
        }


        public double getTaxiOverTime()
        {
            return taxiOverTime;
        }


        public double getPassengerWaitTime()
        {
            return passengerWaitTime;
        }


        public double getMaxTaxiPickupDriveTime()
        {
            return maxTaxiPickupDriveTime;
        }


        public double getMaxPassengerWaitTime()
        {
            return maxPassengerWaitTime;
        }


        public DescriptiveStatistics getTaxiPickupDriveTimeStats()
        {
            return taxiPickupDriveTimeStats;
        }


        public DescriptiveStatistics getPassengerWaitTimeStats()
        {
            return passengerWaitTimeStats;
        }


        @Override
        public String toString()
        {
            return new StringBuilder().append(taxiPickupDriveTime).append('\t') //
                    .append(maxTaxiPickupDriveTime).append('\t') //
                    .append(taxiDropoffDriveTime).append('\t') //
                    .append(taxiPickupTime).append('\t') //
                    .append(taxiDropoffTime).append('\t') //
                    .append(taxiCruiseTime).append('\t') //
                    .append(taxiWaitTime).append('\t') //
                    .append(taxiOverTime).append('\t') //
                    .append(passengerWaitTime).append('\t') //
                    .append(maxPassengerWaitTime).toString();
        }
    }
}