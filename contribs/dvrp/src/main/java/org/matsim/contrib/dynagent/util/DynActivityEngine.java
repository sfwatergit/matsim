/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package org.matsim.contrib.dynagent.util;

import java.util.*;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.dynagent.DynAgent;
import org.matsim.core.mobsim.framework.*;
import org.matsim.core.mobsim.framework.MobsimAgent.State;
import org.matsim.core.mobsim.qsim.*;
import org.matsim.core.mobsim.qsim.interfaces.*;
import org.matsim.core.utils.misc.Time;


public class DynActivityEngine
    implements MobsimEngine, ActivityHandler
{
    private static class EndTimeEntry
    {
        public final DynAgent agent;
        public double scheduledEndTime = Time.UNDEFINED_TIME;


        public EndTimeEntry(DynAgent agent)
        {
            this.agent = agent;
        }
    }


    private final ActivityEngine activityEngine;
    private final Map<Id, EndTimeEntry> activityEndTimes;

    private InternalInterface internalInterface;


    public DynActivityEngine(ActivityEngine activityEngine)
    {
        this.activityEngine = activityEngine;
        activityEndTimes = new HashMap<Id, EndTimeEntry>();
    }


    @Override
    public void doSimStep(double time)
    {
        for (EndTimeEntry e : activityEndTimes.values()) {
            if (e.agent.getState() == State.ACTIVITY) {
                e.agent.doSimStep(time);
                
                //ask agents who are performing an activity about its end time;
                double currentEndTime = e.agent.getActivityEndTime();

                if (e.scheduledEndTime != currentEndTime) {
                    //we may reschedule the agent right now, but rescheduling is quite costly
                    //i.e. it requires iteration through all agents (and there may be millions of them)
                    //and if the agent is very indecisive we may repeat this operation each sim step
                    //that is why it is better to defer the rescheduling as much as possible

                    if (currentEndTime <= time //the agent wants to end the activity NOW, or
                            || e.scheduledEndTime <= time) { //the simulation thinks the agent wants to finish the activity NOW
                        internalInterface.rescheduleActivityEnd(e.agent);
                        e.scheduledEndTime = currentEndTime;
                    }
                }
            }
        }

        activityEngine.doSimStep(time);
    }


    @Override
    public boolean handleActivity(MobsimAgent agent)
    {
        if (activityEngine.handleActivity(agent)) {
            if (agent instanceof DynAgent) {
                EndTimeEntry entry = activityEndTimes.get(agent.getId());

                if (entry == null) {
                    entry = new EndTimeEntry((DynAgent)agent);
                    activityEndTimes.put(agent.getId(), entry);
                }

                entry.scheduledEndTime = agent.getActivityEndTime();
            }

            return true;
        }

        return false;
    }


    @Override
    public void onPrepareSim()
    {
        activityEngine.onPrepareSim();
    }


    @Override
    public void afterSim()
    {
        activityEngine.afterSim();
        activityEndTimes.clear();
    }


    @Override
    public void setInternalInterface(InternalInterface internalInterface)
    {
        this.internalInterface = internalInterface;
        activityEngine.setInternalInterface(internalInterface);
    }

}