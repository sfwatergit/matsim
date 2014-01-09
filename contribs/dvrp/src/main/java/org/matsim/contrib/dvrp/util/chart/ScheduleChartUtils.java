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

package org.matsim.contrib.dvrp.util.chart;

import java.awt.*;
import java.util.*;
import java.util.List;

import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.gantt.*;
import org.jfree.data.time.*;
import org.jfree.data.xy.XYDataset;
import org.matsim.contrib.dvrp.data.VrpData;
import org.matsim.contrib.dvrp.data.model.Vehicle;
import org.matsim.contrib.dvrp.data.schedule.*;
import org.matsim.contrib.dvrp.data.schedule.Task;


public class ScheduleChartUtils
{
    public static JFreeChart chartSchedule(VrpData data)
    {
        return chartSchedule(data, BASIC_DESCRIPTION_CREATOR, BASIC_PAINT_SELECTOR);
    }


    public static <T extends Task> JFreeChart chartSchedule(VrpData data,
            DescriptionCreator<T> descriptionCreator, PaintSelector<T> paintSelector)
    {
        // data
        TaskSeriesCollection dataset = createScheduleDataset(data, descriptionCreator);
        XYTaskDataset xyTaskDataset = new XYTaskDataset(dataset);

        // chart
        String title = "Time " + data.getTime();
        JFreeChart chart = ChartFactory.createXYBarChart(title, "Time", false, "Vehicles",
                xyTaskDataset, PlotOrientation.HORIZONTAL, false, true, false);
        XYPlot plot = (XYPlot)chart.getPlot();

        // Y axis
        List<Vehicle> vehicles = data.getVehicles();
        String[] series = new String[vehicles.size()];
        for (int i = 0; i < series.length; i++) {
            series[i] = vehicles.get(i).getName();
        }

        SymbolAxis symbolAxis = new SymbolAxis("Vehicles", series);
        symbolAxis.setGridBandsVisible(false);
        plot.setDomainAxis(symbolAxis);

        // X axis
        plot.setRangeAxis(new DateAxis("Time", TimeZone.getTimeZone("GMT"), Locale.getDefault()));

        // Renderer
        XYBarRenderer xyBarRenderer = new ChartTaskRenderer<T>(dataset, paintSelector);
        xyBarRenderer.setUseYInterval(true);
        plot.setRenderer(xyBarRenderer);

        return chart;
    }


    @SuppressWarnings("serial")
    private static class ChartTask<T extends Task>
        extends org.jfree.data.gantt.Task
    {
        private T vrpTask;


        private ChartTask(String description, TimePeriod duration, T vrpTask)
        {
            super(description, duration);
            this.vrpTask = vrpTask;
        }
    }


    @SuppressWarnings("serial")
    private static class ChartTaskRenderer<T extends Task>
        extends XYBarRenderer
    {
        private final TaskSeriesCollection tsc;
        private final PaintSelector<T> paintSelector;


        public ChartTaskRenderer(final TaskSeriesCollection tsc, PaintSelector<T> paintSelector)
        {
            this.tsc = tsc;
            this.paintSelector = paintSelector;

            setBarPainter(new StandardXYBarPainter());
            setShadowVisible(false);
            setDrawBarOutline(true);

            setBaseToolTipGenerator(new XYToolTipGenerator() {
                public String generateToolTip(XYDataset dataset, int series, int item)
                {
                    return getTask(series, item).getDescription();
                }
            });
        }


        @Override
        public Paint getItemPaint(int row, int column)
        {
            return paintSelector.select(getTask(row, column).vrpTask);
        }


        private ChartTask<T> getTask(int series, int item)
        {
            @SuppressWarnings("unchecked")
            ChartTask<T> chartTask = (ChartTask<T>)tsc.getSeries(series).get(item);
            return chartTask;
        }

    }


    public static interface PaintSelector<T extends Task>
    {
        Paint select(T task);
    }


    private static final Color DARK_BLUE = new Color(0, 0, 200);
    private static final Color DARK_RED = new Color(200, 0, 0);

    public static final PaintSelector<Task> BASIC_PAINT_SELECTOR = new PaintSelector<Task>() {
        public Paint select(Task task)
        {
            switch (task.getType()) {
                case DRIVE:
                    return DARK_RED;

                case STAY:
                    return DARK_BLUE;

                default:
                    throw new IllegalStateException();
            }
        }
    };


    public static interface DescriptionCreator<T extends Task>
    {
        String create(T task);
    }


    public static final DescriptionCreator<Task> BASIC_DESCRIPTION_CREATOR = new DescriptionCreator<Task>() {
        public String create(Task task)
        {
            return task.getType().name();
        }
    };


    private static <T extends Task> TaskSeriesCollection createScheduleDataset(VrpData data,
            DescriptionCreator<T> descriptionCreator)
    {
        TaskSeriesCollection collection = new TaskSeriesCollection();

        for (Vehicle v : data.getVehicles()) {
            @SuppressWarnings("unchecked")
            Schedule<T> schedule = (Schedule<T>)v.getSchedule();

            final TaskSeries scheduleTaskSeries = new TaskSeries(v.getName());

            if (schedule.getStatus().isUnplanned()) {
                collection.add(scheduleTaskSeries);
                continue;
            }

            List<T> tasks = schedule.getTasks();

            for (T t : tasks) {
                String description = descriptionCreator.create(t);

                TimePeriod duration = new SimpleTimePeriod(new Date(t.getBeginTime() * 1000),
                        new Date(t.getEndTime() * 1000));

                scheduleTaskSeries.add(new ChartTask<T>(description, duration, t));
            }

            collection.add(scheduleTaskSeries);
        }

        return collection;
    }
}