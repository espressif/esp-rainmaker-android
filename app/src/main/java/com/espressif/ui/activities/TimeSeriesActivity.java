// Copyright 2022 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityTimeSeriesBinding;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.TsData;
import com.espressif.ui.widgets.EspMarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class TimeSeriesActivity extends AppCompatActivity {

    private static final String TAG = TimeSeriesActivity.class.getSimpleName();

    private static final String AGGREGATE_TYPE_RAW = "raw";
    private static final String AGGREGATE_TYPE_AVG = "avg";
    private static final String AGGREGATE_TYPE_MIN = "min";
    private static final String AGGREGATE_TYPE_MAX = "max";
    private static final String AGGREGATE_TYPE_COUNT = "count";
    private static final String AGGREGATE_TYPE_LATEST = "latest";

    private static final short INTERVAL_1D = 1;
    private static final short INTERVAL_7D = 2;
    private static final short INTERVAL_4W = 3;
    private static final short INTERVAL_1Y = 4;

    private static final String TIME_INTERVAL_HOUR = "hour";
    private static final String TIME_INTERVAL_DAY = "day";
    private static final String TIME_INTERVAL_WEEK = "week";
    private static final String TIME_INTERVAL_MONTH = "month";

    private static final short TYPE_BAR_CHART = 1;
    private static final short TYPE_LINE_CHART = 2;

    // Default values
    private String aggregateType = AGGREGATE_TYPE_RAW;
    private int chartInterval = INTERVAL_1D;
    private int chartType = TYPE_BAR_CHART;
    private String weekStart = null;
    private String timeZone;

    private ApiManager apiManager;
    private String nodeId, deviceName, paramName, tsType;
    private Param param;
    private long myStartTime, myEndTime;
    private SimpleDateFormat dateFormatter;
    private ArrayList<TsData> tsData;

    private float yMin, yMax;

    private ArrayList<BarEntry> barEntries = new ArrayList<>();
    private ArrayList<Entry> lineEntries = new ArrayList<>();
    private ArrayList<String> xAxisLabel = new ArrayList<>();

    private ActivityTimeSeriesBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTimeSeriesBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        apiManager = ApiManager.getInstance(getApplicationContext());
        Intent intent = getIntent();
        nodeId = intent.getStringExtra(AppConstants.KEY_NODE_ID);
        deviceName = intent.getStringExtra(AppConstants.KEY_DEVICE_NAME);
        param = intent.getParcelableExtra(AppConstants.KEY_PARAM);
        tsType = intent.getStringExtra(AppConstants.KEY_PROPERTY_TS_TYPE);
        dateFormatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        initViews();
        showLoading();

        paramName = deviceName + "." + param.getName();
        Log.d(TAG, "Node id : " + nodeId);
        Log.d(TAG, "Device Name : " + deviceName);
        Log.d(TAG, "Param Name : " + paramName);

        String timeInterval = TIME_INTERVAL_HOUR;
        timeZone = com.espressif.ui.Utils.getCurrentTimeZone();
        Log.d(TAG, "============= Time zone : " + timeZone);
        Calendar calendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        startTime = TimeUnit.SECONDS.convert(startTime, TimeUnit.MILLISECONDS);
        calendar.add(Calendar.HOUR, 23);
        calendar.add(Calendar.MINUTE, 59);
        calendar.add(Calendar.SECOND, 59);
        long endTime = calendar.getTimeInMillis();
        endTime = TimeUnit.SECONDS.convert(endTime, TimeUnit.MILLISECONDS);

        myStartTime = startTime;
        myEndTime = endTime;
        String today = new SimpleDateFormat("MMM dd, yyyy").format(new Date(myStartTime * 1000));
        binding.tvDate.setText(today);
        updateDate();

        if (isDataTypeSupported()) {
            getTsData(aggregateType, timeInterval, startTime, endTime, null, timeZone, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    hideLoading();
                    updateChart();
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    hideLoading();
                    updateChart();
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    displayNetworkError();
                }
            });
        } else {
            displayDataTypeNotSupported();
        }
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(param.getName());
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        binding.sgChartInterval.setOnCheckedChangeListener(sgChartIntervalChangeListener);
        binding.sgAggregateType.setOnCheckedChangeListener(sgAggregateTypeChangeListener);
        binding.sgChartType.setOnCheckedChangeListener(sgChartTypeChangeListener);
        binding.ivPrev.setOnClickListener(prevBtnClickListener);
        binding.ivNext.setOnClickListener(nextBtnClickListener);

        if (AppConstants.KEY_PROPERTY_TS_SIMPLE.equals(tsType)) {
            binding.sgAggregateType.setVisibility(View.INVISIBLE);
            binding.radioBtn4w.setLayoutParams(binding.radioBtn1y.getLayoutParams());
            binding.sgChartInterval.setWeightSum(3);
            binding.sgChartInterval.removeViewAt(3);
        } else {
            binding.sgAggregateType.setVisibility(View.VISIBLE);
        }

        binding.barChart.setVisibility(View.INVISIBLE);
        binding.lineChart.setVisibility(View.INVISIBLE);
        binding.ivNext.setVisibility(View.GONE);
    }

    private RadioGroup.OnCheckedChangeListener sgChartIntervalChangeListener = new RadioGroup.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {

            switch (checkedId) {

                case R.id.radio_btn_1d:
                    if (chartInterval == INTERVAL_1D) {
                        return;
                    }
                    changeChart(aggregateType, INTERVAL_1D);
                    break;

                case R.id.radio_btn_7d:
                    if (chartInterval == INTERVAL_7D) {
                        return;
                    }
                    changeChart(aggregateType, INTERVAL_7D);
                    break;

                case R.id.radio_btn_4w:
                    if (chartInterval == INTERVAL_4W) {
                        return;
                    }
                    changeChart(aggregateType, INTERVAL_4W);
                    break;

                case R.id.radio_btn_1y:
                    if (chartInterval == INTERVAL_1Y) {
                        return;
                    }
                    changeChart(aggregateType, INTERVAL_1Y);
                    break;
            }
        }
    };

    private RadioGroup.OnCheckedChangeListener sgAggregateTypeChangeListener = new RadioGroup.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {

            switch (checkedId) {

                case R.id.radio_btn_raw:
                    if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_RAW, chartInterval);
                    break;

                case R.id.radio_btn_min:
                    if (aggregateType.equals(AGGREGATE_TYPE_MIN)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_MIN, chartInterval);
                    break;

                case R.id.radio_btn_max:
                    if (aggregateType.equals(AGGREGATE_TYPE_MAX)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_MAX, chartInterval);
                    break;

                case R.id.radio_btn_latest:
                    if (aggregateType.equals(AGGREGATE_TYPE_LATEST)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_LATEST, chartInterval);
                    break;

                case R.id.radio_btn_avg:
                    if (aggregateType.equals(AGGREGATE_TYPE_AVG)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_AVG, chartInterval);
                    break;

                case R.id.radio_btn_count:
                    if (aggregateType.equals(AGGREGATE_TYPE_COUNT)) {
                        return;
                    }
                    changeChart(AGGREGATE_TYPE_COUNT, chartInterval);
                    break;
            }
        }
    };

    private RadioGroup.OnCheckedChangeListener sgChartTypeChangeListener = new RadioGroup.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {

            switch (checkedId) {

                case R.id.radio_btn_bar_chart:
                    changeChartType(TYPE_BAR_CHART);
                    break;

                case R.id.radio_btn_line_chart:
                    changeChartType(TYPE_LINE_CHART);
                    break;
            }
        }
    };

    private View.OnClickListener prevBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Log.e(TAG, "Previous button clicked");
            myEndTime = myStartTime - 1;
            Calendar calendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
            calendar.setTime(new Date(myEndTime * 1000));

            switch (chartInterval) {
                case INTERVAL_1D:
                    break;

                case INTERVAL_7D:
                    calendar.add(Calendar.DATE, -6);
                    break;

                case INTERVAL_4W:
                    calendar.add(Calendar.DATE, -27);
                    break;

                case INTERVAL_1Y:
                    calendar.set(Calendar.DATE, 1);
                    calendar.add(Calendar.MONTH, -11);
                    break;
            }
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            myStartTime = TimeUnit.SECONDS.convert(startTime, TimeUnit.MILLISECONDS);
            Log.e(TAG, "Start time : " + dateFormatter.format(new Date(myStartTime * 1000)));
            Log.e(TAG, "End time : " + dateFormatter.format(new Date(myEndTime * 1000)));
            setNextBtnVisibility();
            changeChart(aggregateType, chartInterval);
        }
    };

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            myStartTime = myEndTime + 1;
            Calendar calendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
            calendar.setTime(new Date(myStartTime * 1000));

            switch (chartInterval) {
                case INTERVAL_1D:
                    calendar.add(Calendar.HOUR, 23);
                    calendar.add(Calendar.MINUTE, 59);
                    calendar.add(Calendar.SECOND, 59);
                    break;

                case INTERVAL_7D:
                    calendar.add(Calendar.DATE, 6);
                    calendar.add(Calendar.HOUR, 23);
                    calendar.add(Calendar.MINUTE, 59);
                    calendar.add(Calendar.SECOND, 59);
                    calendar.set(Calendar.MILLISECOND, 0);
                    break;

                case INTERVAL_4W:
                    calendar.add(Calendar.DATE, 27);
                    calendar.add(Calendar.HOUR, 23);
                    calendar.add(Calendar.MINUTE, 59);
                    calendar.add(Calendar.SECOND, 59);
                    calendar.set(Calendar.MILLISECOND, 0);
                    break;

                case INTERVAL_1Y:
                    calendar.add(Calendar.MONTH, 12);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    calendar.add(Calendar.SECOND, -1);
                    break;
            }

            long endTime = calendar.getTimeInMillis();
            myEndTime = TimeUnit.SECONDS.convert(endTime, TimeUnit.MILLISECONDS);
            Log.e(TAG, "Start time : " + dateFormatter.format(new Date(myStartTime * 1000)));
            Log.e(TAG, "End time : " + dateFormatter.format(new Date(myEndTime * 1000)));
            setNextBtnVisibility();
            changeChart(aggregateType, chartInterval);
        }
    };

    private void changeChart(String newAgrType, int newChartType) {

        showLoading();
        Log.e(TAG, "Change chart");
        boolean isChartTypeChange = false;
        if (chartInterval != newChartType) {
            isChartTypeChange = true;
        }

        aggregateType = newAgrType;
        chartInterval = newChartType;
        if (isChartTypeChange) {
            setStartAndEndTime(newChartType);
            setNextBtnVisibility();
        }
        updateDate();

        String timeInterval = TIME_INTERVAL_HOUR;
        boolean isSupported = true;
        weekStart = null;

        switch (newChartType) {
            case INTERVAL_1D:
                timeInterval = TIME_INTERVAL_HOUR;
                break;
            case INTERVAL_7D:
                timeInterval = TIME_INTERVAL_DAY;
                break;
            case INTERVAL_4W:
                timeInterval = TIME_INTERVAL_WEEK;
                weekStart = new SimpleDateFormat("EEEE").format(new Date(myStartTime * 1000));
                break;
            case INTERVAL_1Y:
                timeInterval = TIME_INTERVAL_MONTH;
                if (aggregateType.equals(AGGREGATE_TYPE_RAW) || aggregateType.equals(AGGREGATE_TYPE_LATEST)) {
                    isSupported = false;
                }
                break;
        }

        if (!isDataTypeSupported()) {
            displayDataTypeNotSupported();
            return;
        }

        if (!isSupported) {
            binding.barChart.setVisibility(View.INVISIBLE);
            binding.lineChart.setVisibility(View.INVISIBLE);
            binding.tvNoData.setVisibility(View.VISIBLE);
            binding.tvNoData.setText(R.string.ts_not_supported);
            hideLoading();
            return;
        }

        getTsData(aggregateType, timeInterval, myStartTime, myEndTime, weekStart, timeZone, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                Log.e(TAG, "Get TS DATA SUCCESS");
                hideLoading();
                updateChart();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                hideLoading();
                updateChart();
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                displayNetworkError();
            }
        });
    }

    private void setStartAndEndTime(int type) {

        Calendar calendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
        long endTime = calendar.getTimeInMillis();
        myEndTime = TimeUnit.SECONDS.convert(endTime, TimeUnit.MILLISECONDS);

        switch (type) {
            case INTERVAL_1D:
                break;

            case INTERVAL_7D:
                calendar.add(Calendar.DATE, -6);
                break;

            case INTERVAL_4W:
                calendar.add(Calendar.DATE, -27);
                break;

            case INTERVAL_1Y:
                calendar.set(Calendar.DATE, 1);
                calendar.add(Calendar.MONTH, -11);
                break;
        }

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startTime = calendar.getTimeInMillis();
        myStartTime = TimeUnit.SECONDS.convert(startTime, TimeUnit.MILLISECONDS);
        String startDateInString = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss").format(new Date(myStartTime * 1000));
        String endDateInString = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss").format(new Date(myEndTime * 1000));
        Log.e(TAG, "============= Start date : " + startDateInString);
        Log.e(TAG, "============= End date : " + endDateInString);
    }

    private void getTsData(String aggregate, String timeInterval, long startTime, long endTime,
                           String weekStart, String timeZone, ApiResponseListener listener) {

        apiManager.getTimeSeriesData(nodeId, paramName, param.getDataType(), aggregate, timeInterval,
                startTime, endTime, weekStart, timeZone, tsType, new ApiResponseListener() {

                    @Override
                    public void onSuccess(Bundle data) {
                        if (data != null) {
                            tsData = data.getParcelableArrayList("ts_data");
                            Log.d(TAG, "Time Series data size : " + tsData.size());
                            processTsData(startTime, endTime, tsData);
                            listener.onSuccess(data);
                        }
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        listener.onResponseFailure(exception);
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        listener.onNetworkFailure(exception);
                    }
                });
    }

    private void processTsData(long startTime, long endTime, ArrayList<TsData> tsData) {

        barEntries.clear();
        lineEntries.clear();
        xAxisLabel.clear();

        final Calendar tempCalendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
        startTime = TimeUnit.MILLISECONDS.convert(startTime, TimeUnit.SECONDS);
        Date startDate = new Date(startTime);
        tempCalendar.setTime(startDate);
        Date endDate;
        HashMap<Long, TsData> tsDataMap = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy hh:mm aaa");
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        float min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        Log.e(TAG, "TS DATA size : " + tsData.size());

        if (tsData != null && tsData.size() > 0) {

            switch (chartInterval) {

                case INTERVAL_1D:

                    formatter = new SimpleDateFormat("hh:mm aaa");

                    if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {

                        if (tsData.size() > 0) {

                            for (int j = 0; j < tsData.size(); j++) {
                                TsData dataPoint = tsData.get(j);
                                long timeStamp = TimeUnit.MINUTES.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS);
                                tsDataMap.put(timeStamp, dataPoint);
                            }

                            int mins = 1440;
                            for (int i = 1; i < (mins + 1); i++) {

                                long timeStamp = tempCalendar.getTimeInMillis();
                                String timeStr = formatter.format(tempCalendar.getTime());
                                timeStamp = TimeUnit.MINUTES.convert(timeStamp, TimeUnit.MILLISECONDS);

                                if (tsDataMap.containsKey(timeStamp)) {
                                    TsData dataPoint = tsDataMap.get(timeStamp);
                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String markerText = sdf.format(new Date(TimeUnit.MILLISECONDS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS)));
                                    BarEntry barEntry1 = new BarEntry(i, y); // start always from x=1 for the first bar
                                    Entry lineEntry1 = new Entry(i, y);
                                    barEntry1.setData(markerText);
                                    lineEntry1.setData(markerText);
                                    barEntries.add(barEntry1);
                                    lineEntries.add(lineEntry1);
                                }
                                xAxisLabel.add(timeStr);
                                tempCalendar.add(Calendar.MINUTE, 1);
                            }
                        }
                    } else {

                        if (tsData.size() > 0) {

                            for (int j = 0; j < tsData.size(); j++) {
                                TsData dataPoint = tsData.get(j);
                                long timeStamp = TimeUnit.HOURS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS);
                                tsDataMap.put(timeStamp, dataPoint);
                            }
                            Log.e(TAG, "tsDataMap size : " + tsDataMap.size());

                            int hours = 24;
                            for (int i = 1; i < (hours + 1); i++) {

                                long timeStamp = tempCalendar.getTimeInMillis();
                                String timeStr = formatter.format(tempCalendar.getTime());
                                timeStamp = TimeUnit.HOURS.convert(timeStamp, TimeUnit.MILLISECONDS);

                                if (tsDataMap.containsKey(timeStamp)) {
                                    TsData dataPoint = tsDataMap.get(timeStamp);
                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String markerText = sdf.format(new Date(TimeUnit.MILLISECONDS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS)));
                                    BarEntry barEntry1 = new BarEntry(i, y); // start always from x=1 for the first bar
                                    Entry lineEntry1 = new Entry(i, y);
                                    Log.e(TAG, "Bar entry for time : " + timeStr + " and y : " + y);
                                    barEntry1.setData(markerText);
                                    lineEntry1.setData(markerText);
                                    barEntries.add(barEntry1);
                                    lineEntries.add(lineEntry1);
                                }
                                xAxisLabel.add(timeStr);
                                tempCalendar.add(Calendar.HOUR, 1);
                            }
                        }
                    }
                    break;

                case INTERVAL_7D:

                    formatter = new SimpleDateFormat("EEE");

                    if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {

                        if (tsData.size() > 0) {

                            for (int j = 0; j < tsData.size(); j++) {
                                TsData dataPoint = tsData.get(j);
                                long timeStamp = TimeUnit.MINUTES.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS);
                                tsDataMap.put(timeStamp, dataPoint);
                            }

                            int mins = 10080;
                            for (int i = 1; i < (mins + 1); i++) {

                                long timeStamp = tempCalendar.getTimeInMillis();
                                String timeStr = formatter.format(tempCalendar.getTime());
                                timeStamp = TimeUnit.MINUTES.convert(timeStamp, TimeUnit.MILLISECONDS);

                                if (tsDataMap.containsKey(timeStamp)) {
                                    TsData dataPoint = tsDataMap.get(timeStamp);
                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String markerText = sdf.format(new Date(TimeUnit.MILLISECONDS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS)));
                                    BarEntry barEntry = new BarEntry(i, y);
                                    Entry lineEntry = new Entry(i, y);
                                    barEntry.setData(markerText);
                                    lineEntry.setData(markerText);
                                    barEntries.add(barEntry);
                                    lineEntries.add(lineEntry);
                                }
                                xAxisLabel.add(timeStr);
                                tempCalendar.add(Calendar.MINUTE, 1);
                            }
                        }
                    } else {

                        ArrayList<TsData> mTsData = new ArrayList<>();
                        mTsData.addAll(tsData);

                        for (int i = 0; i < 7; i++) {

                            String weekStartDay = formatter.format(tempCalendar.getTime());
                            Log.d(TAG, "======= Adding xAxisLabel : " + weekStartDay);
                            xAxisLabel.add(weekStartDay);
                            tempCalendar.add(Calendar.DATE, 1);
                            endDate = tempCalendar.getTime();
                            Log.d(TAG, "======= Start date For calculation : " + startDate);
                            Log.d(TAG, "======= End date For calculation : " + endDate);

                            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MMM-yy");
                            Iterator<TsData> itr = mTsData.iterator();

                            while (itr.hasNext()) {
                                TsData dataPoint = itr.next();
                                Date tsDate = new Date(dataPoint.getTimeStamp() * 1000);

                                if ((tsDate.after(startDate) && tsDate.before(endDate)) || tsDate.equals(startDate)) {
                                    Log.e(TAG, "======= Entry added for date : " + tsDate);

                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String markerText = sdf1.format(tsDate);
                                    BarEntry barEntry = new BarEntry((i + 1), y);
                                    Entry lineEntry = new Entry((i + 1), y);
                                    barEntry.setData(markerText);
                                    lineEntry.setData(markerText);
                                    barEntries.add(barEntry);
                                    lineEntries.add(lineEntry);
                                    Log.e(TAG, "Adding entry for day ==== : " + weekStartDay);
                                    itr.remove();
                                    break;
                                }
                            }
                            startDate = tempCalendar.getTime();
                        }
                        xAxisLabel.add(""); //empty label for the last vertical grid line on Y-Right Axis
                    }
                    break;

                case INTERVAL_4W:

                    formatter = new SimpleDateFormat("dd/MM");

                    if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {

                        if (tsData.size() > 0) {

                            for (int j = 0; j < tsData.size(); j++) {
                                TsData dataPoint = tsData.get(j);
                                long timeStamp = TimeUnit.HOURS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS);
                                tsDataMap.put(timeStamp, dataPoint);
                            }

                            int hours = 672;
                            for (int i = 1; i < (hours + 1); i++) {

                                long timeStamp = tempCalendar.getTimeInMillis();
                                String timeStr = formatter.format(tempCalendar.getTime());
                                timeStamp = TimeUnit.HOURS.convert(timeStamp, TimeUnit.MILLISECONDS);

                                if (tsDataMap.containsKey(timeStamp)) {
                                    TsData dataPoint = tsDataMap.get(timeStamp);
                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String markerText = sdf.format(new Date(TimeUnit.MILLISECONDS.convert(dataPoint.getTimeStamp(), TimeUnit.SECONDS)));
                                    BarEntry barEntry = new BarEntry(i, y);
                                    Entry lineEntry = new Entry(i, y);
                                    barEntry.setData(markerText);
                                    lineEntry.setData(markerText);
                                    barEntries.add(barEntry);
                                    lineEntries.add(lineEntry);
                                }
                                xAxisLabel.add(timeStr);
                                tempCalendar.add(Calendar.HOUR, 1);
                            }
                            xAxisLabel.add("");
                        }

                    } else {

                        ArrayList<TsData> mTsData = new ArrayList<>();
                        mTsData.addAll(tsData);

                        for (int i = 0; i < 4; i++) {

                            String weekStartDay = formatter.format(tempCalendar.getTime());
                            Log.d(TAG, "======= Adding xAxisLabel : " + weekStartDay);
                            xAxisLabel.add(weekStartDay);
                            tempCalendar.add(Calendar.DATE, 7);
                            tempCalendar.add(Calendar.MILLISECOND, -1);
                            endDate = tempCalendar.getTime();
                            Log.d(TAG, "======= Start date For calculation : " + startDate);
                            Log.d(TAG, "======= End date For calculation : " + endDate);

                            SimpleDateFormat sdfMonth = new SimpleDateFormat("dd-MMM");
                            Iterator<TsData> itr = mTsData.iterator();

                            while (itr.hasNext()) {
                                TsData dataPoint = itr.next();
                                Date tsDate = new Date(dataPoint.getTimeStamp() * 1000);

                                if ((tsDate.after(startDate) && tsDate.before(endDate)) || tsDate.equals(startDate)) {

                                    Log.e(TAG, "Entry added for date : " + tsDate);

                                    float y = (float) dataPoint.getValue();
                                    if (y < min) {
                                        min = y;
                                    }
                                    if (y > max) {
                                        max = y;
                                    }

                                    String startDateText = sdfMonth.format(startDate);
                                    String endDateText = sdfMonth.format(endDate);
                                    String markerText = startDateText + " to " + endDateText;

                                    BarEntry barEntry = new BarEntry((i + 1), y);
                                    Entry lineEntry = new Entry((i + 1), y);
                                    barEntry.setData(markerText);
                                    lineEntry.setData(markerText);
                                    barEntries.add(barEntry);
                                    lineEntries.add(lineEntry);
                                    itr.remove();
                                    break;
                                }
                            }
                            tempCalendar.add(Calendar.MILLISECOND, 1);
                            startDate = tempCalendar.getTime();
                        }
                        xAxisLabel.add(""); //empty label for the last vertical grid line on Y-Right Axis
                    }
                    break;

                case INTERVAL_1Y:

                    ArrayList<TsData> mTsData = new ArrayList<>();
                    mTsData.addAll(tsData);

                    for (int i = 1; i <= 12; i++) {

                        formatter = new SimpleDateFormat("MMM");
                        String month = formatter.format(tempCalendar.getTime());
                        Log.d(TAG, "======= Adding xAxisLabel : " + month);
                        xAxisLabel.add(month);

                        tempCalendar.add(Calendar.MONTH, 1);
                        endDate = tempCalendar.getTime();
                        Log.d(TAG, "======= Start date For calculation : " + startDate);
                        Log.d(TAG, "======= End date For calculation : " + endDate);

                        Iterator<TsData> itr = mTsData.iterator();

                        while (itr.hasNext()) {
                            TsData dataPoint = itr.next();
                            Date tsDate = new Date(dataPoint.getTimeStamp() * 1000);
                            Log.d(TAG, "======= TS date For calculation : " + tsDate);

                            if ((tsDate.after(startDate) && tsDate.before(endDate)) || tsDate.equals(startDate)) {
                                float y = (float) dataPoint.getValue();
                                if (y < min) {
                                    min = y;
                                }
                                if (y > max) {
                                    max = y;
                                }

                                BarEntry barEntry = new BarEntry(i, y);
                                Entry lineEntry = new Entry(i, y);
                                barEntry.setData(month);
                                lineEntry.setData(month);
                                barEntries.add(barEntry);
                                lineEntries.add(lineEntry);
                                itr.remove();
                                break;
                            }
                        }
                        startDate = tempCalendar.getTime();
                    }
                    break;
            }

            yMax = (int) (10 * (Math.ceil(max / 10)));
            yMin = (int) (10 * (Math.floor(min / 10)));
            Log.d(TAG, "Min value in all readings : " + yMin);
            Log.d(TAG, "Max value in all readings : " + yMax);

        } else {
            Log.e(TAG, "No TS Data available.");
        }
    }

    private void updateChart() {

        Log.d(TAG, "Update chart");

        if ((aggregateType.equals(AGGREGATE_TYPE_RAW) || aggregateType.equals(AGGREGATE_TYPE_LATEST))
                && chartInterval == INTERVAL_1Y) {

            binding.barChart.setVisibility(View.INVISIBLE);
            binding.lineChart.setVisibility(View.INVISIBLE);
            binding.tvNoData.setVisibility(View.VISIBLE);
            binding.tvNoData.setText(R.string.ts_not_supported);
            hideLoading();
            return;
        }

        if (tsData != null && tsData.size() > 0) {

            if (chartType == TYPE_BAR_CHART) {
                binding.barChart.setVisibility(View.VISIBLE);
                binding.lineChart.setVisibility(View.INVISIBLE);
            } else {
                binding.barChart.setVisibility(View.INVISIBLE);
                binding.lineChart.setVisibility(View.VISIBLE);
            }
            binding.tvNoData.setVisibility(View.GONE);

            // clear old markers
            binding.barChart.highlightValue(null);
            binding.lineChart.highlightValue(null);

            EspMarkerView mv = new EspMarkerView(this, R.layout.layout_marker);
            // set the marker to the chart
            binding.barChart.setMarker(mv);
            binding.lineChart.setMarker(mv);

            if (chartType == TYPE_BAR_CHART) {
                displayBarChart();
            } else {
                displayLineChart();
            }

        } else {
            Log.e(TAG, "No TS Data to available to plot.");
            binding.barChart.setVisibility(View.INVISIBLE);
            binding.lineChart.setVisibility(View.INVISIBLE);
            binding.tvNoData.setVisibility(View.VISIBLE);
            binding.tvNoData.setText(R.string.no_chart_data);
            hideLoading();
        }
    }

    private void displayNetworkError() {
        binding.barChart.setVisibility(View.INVISIBLE);
        binding.lineChart.setVisibility(View.INVISIBLE);
        binding.tvNoData.setVisibility(View.VISIBLE);
        binding.tvNoData.setText(R.string.no_internet_connection);
        hideLoading();
    }

    private void displayBarChart() {

        Log.e(TAG, "Display BarChart, Total entries , : " + barEntries.size());

        XAxis xAxis = binding.barChart.getXAxis();
        configureXAxis(xAxis);

        YAxis rightAxis = binding.barChart.getAxisLeft();
        configureRightYAxis(rightAxis);

        YAxis leftAxis = binding.barChart.getAxisRight();
        configureLeftYAxis(leftAxis);

        if (barEntries.size() > 0) {
            setYAxisBounds(rightAxis, yMin, yMax);
        }

        //set the BarDataSet
        BarDataSet barDataSet = new BarDataSet(barEntries, "");
        barDataSet.setColor(getResources().getColor(R.color.colorPrimary));
        barDataSet.setFormSize(15f);
        barDataSet.setDrawValues(false);
        barDataSet.setValueTextSize(12f);
        barDataSet.setHighLightColor(getResources().getColor(R.color.colorPrimaryDark)); // color for highlight indicator

//      barDataSet.setHighlightEnabled(true); // allow highlighting for DataSet
//      set this to false to disable the drawing of highlight indicator (lines)
//      barDataSet.setDrawHighlightIndicators(true);

        setBarChart(barDataSet);
    }

    private void displayLineChart() {

        Log.d(TAG, "Display LineChart Total entries : " + lineEntries.size());

        XAxis xAxis = binding.lineChart.getXAxis();
        configureXAxis(xAxis);

        YAxis rightAxis = binding.lineChart.getAxisLeft();
        configureRightYAxis(rightAxis);

        YAxis leftAxis = binding.lineChart.getAxisRight();
        configureLeftYAxis(leftAxis);

        if (lineEntries.size() > 0) {
            setYAxisBounds(rightAxis, yMin, yMax);
        }

        //set the LineDataSet
        LineDataSet lineDataSet = new LineDataSet(lineEntries, "");
        lineDataSet.setColor(getResources().getColor(R.color.colorPrimary));
        lineDataSet.setDrawCircles(true);
        lineDataSet.setLineWidth(2f);

        lineDataSet.setCircleRadius(2.75f);
        lineDataSet.setCircleHoleRadius(1.5f);
        lineDataSet.setCircleColor(getResources().getColor(R.color.color_dark_gray));
        lineDataSet.setHighLightColor(getResources().getColor(R.color.colorPrimaryDark));

        lineDataSet.setFormSize(15f);
        lineDataSet.setDrawValues(false);
        lineDataSet.setValueTextSize(12f);

        setLineChart(lineDataSet);
    }

    private void configureXAxis(XAxis xAxis) {

        xAxis.setTextColor(Color.BLACK);
        xAxis.setTextSize(13);
        xAxis.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        xAxis.setAxisLineColor(Color.BLACK);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(1f);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setCenterAxisLabels(true);

        // Set label count & axis bounds
        if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {

            switch (chartInterval) {

                case INTERVAL_1D:
                case INTERVAL_4W:
                    xAxis.setLabelCount(5, true);
                    break;

                case INTERVAL_7D:
                    xAxis.setLabelCount(8, true);
                    break;
            }

            switch (chartInterval) {
                case INTERVAL_1D:
                    setXAxisBounds(xAxis, 0.5f, 1440.5f);
                    break;

                case INTERVAL_7D:
                    setXAxisBounds(xAxis, 0.5f, 10080.5f);
                    break;

                case INTERVAL_4W:
                    setXAxisBounds(xAxis, 0.5f, 672.5f);
                    break;

                case INTERVAL_1Y:
                    setXAxisBounds(xAxis, 0.5f, 12.5f);
                    break;
            }

        } else {

            if (chartInterval == INTERVAL_1D) {
                xAxis.setLabelCount(5, true);
            } else {
                xAxis.setLabelCount(xAxisLabel.size(), true);
            }

            switch (chartInterval) {
                case INTERVAL_1D:
                    setXAxisBounds(xAxis, 0.5f, 24.5f);
                    break;

                case INTERVAL_7D:
                    setXAxisBounds(xAxis, 0.5f, 7.5f);
                    break;

                case INTERVAL_4W:
                    setXAxisBounds(xAxis, 0.5f, 4.5f);
                    break;

                case INTERVAL_1Y:
                    setXAxisBounds(xAxis, 0.5f, 12.5f);
                    break;
            }
        }

        xAxis.setValueFormatter(new ValueFormatter() {

            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;

                if (chartInterval == INTERVAL_1Y) {
                    if (value != 0.5f) {
                        index = (int) (value + 0.5f);
                    }
                }

                if (xAxisLabel.size() > index) {
                    return xAxisLabel.get(index);
                } else {
                    return "";
                }
            }
        });
    }

    private void configureRightYAxis(YAxis rightAxis) {

        rightAxis.setTextColor(Color.BLACK);
        rightAxis.setTextSize(13);
        rightAxis.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        rightAxis.setDrawAxisLine(true);
        rightAxis.setAxisLineColor(Color.BLACK);
        rightAxis.setDrawGridLines(false);
        rightAxis.setGranularity(1f);
        rightAxis.setGranularityEnabled(true);
        rightAxis.setLabelCount(5, true); //labels (Y-Values) for 4 horizontal grid lines
        rightAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
    }

    private void configureLeftYAxis(YAxis leftAxis) {

        leftAxis.setDrawAxisLine(true);
        leftAxis.setLabelCount(0, true);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "";
            }
        });
    }

    private void setXAxisBounds(XAxis xAxis, float min, float max) {
        xAxis.setAxisMinimum(min);
        xAxis.setAxisMaximum(max);
    }

    private void setYAxisBounds(YAxis yAxis, float min, float max) {
        yAxis.setAxisMinimum(min);
        yAxis.setAxisMaximum(max);
    }

    private void setBarChart(BarDataSet barDataSet) {

        BarData data = new BarData(barDataSet);
        binding.barChart.setData(data);
        binding.barChart.getLegend().setEnabled(false);
        binding.barChart.setDrawBarShadow(false);
        binding.barChart.getDescription().setEnabled(false);

        if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {
            binding.barChart.setScaleXEnabled(true);
            binding.barChart.setScaleYEnabled(false);
            binding.barChart.setPinchZoom(true);
        } else {
            binding.barChart.setScaleEnabled(false);
            binding.barChart.setPinchZoom(false);
        }

        binding.barChart.setExtraBottomOffset(10f);
        binding.barChart.fitScreen();

        binding.barChart.getAxisRight().setDrawLabels(false);
        binding.barChart.setDoubleTapToZoomEnabled(false);
        binding.barChart.setDrawValueAboveBar(true);
        binding.barChart.setDrawGridBackground(false);

        binding.barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {

                Highlight highlight[] = new Highlight[binding.barChart.getData().getDataSets().size()];
                for (int j = 0; j < binding.barChart.getData().getDataSets().size(); j++) {

                    IDataSet iDataSet = binding.barChart.getData().getDataSets().get(j);

                    for (int i = 0; i < ((BarDataSet) iDataSet).getValues().size(); i++) {
                        if (((BarDataSet) iDataSet).getValues().get(i).getX() == e.getX()) {
                            highlight[j] = new Highlight(e.getX(), e.getY(), j);
                        }
                    }
                }
                binding.barChart.highlightValues(highlight);
            }

            @Override
            public void onNothingSelected() {

            }
        });

//        barChart.setXAxisRenderer(new XAxisRenderer(barChart.getViewPortHandler(), barChart.getXAxis(), barChart.getTransformer(YAxis.AxisDependency.LEFT)) {
//            @Override
//            protected void drawLabel(Canvas c, String formattedLabel, float x, float y, MPPointF anchor, float angleDegrees) {
//                //for 6AM and 6PM set the correct label x position based on your needs
//                if (!TextUtils.isEmpty(formattedLabel) && formattedLabel.equals("6"))
//                    Utils.drawXAxisValue(c, formattedLabel, x + Utils.convertDpToPixel(5f), y + Utils.convertDpToPixel(1f), mAxisLabelPaint, anchor, angleDegrees);
//                    //for 12AM and 12PM set the correct label x position based on your needs
//                else
//                    Utils.drawXAxisValue(c, formattedLabel, x + Utils.convertDpToPixel(20f), y + Utils.convertDpToPixel(1f), mAxisLabelPaint, anchor, angleDegrees);
//            }
//        });

        binding.barChart.invalidate();
    }

    private void setLineChart(LineDataSet lineDataSet) {

        LineData data = new LineData(lineDataSet);
        binding.lineChart.setData(data);
        binding.lineChart.setScaleEnabled(true);
        binding.lineChart.getLegend().setEnabled(false);
        binding.lineChart.getDescription().setEnabled(false);
//        barChart.setDrawBarShadow(false);

        if (aggregateType.equals(AGGREGATE_TYPE_RAW)) {
            binding.lineChart.setScaleXEnabled(true);
            binding.lineChart.setScaleYEnabled(false);
            binding.lineChart.setPinchZoom(true);
        } else {
            binding.lineChart.setScaleEnabled(false);
            binding.lineChart.setPinchZoom(false);
        }

        binding.lineChart.getAxisRight().setDrawLabels(false);
        binding.lineChart.setDoubleTapToZoomEnabled(false);
        binding.barChart.setDrawValueAboveBar(true);
        binding.lineChart.setDrawGridBackground(false);

        binding.lineChart.setExtraBottomOffset(10f);
        binding.lineChart.fitScreen();

        binding.lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {

                Highlight highlight[] = new Highlight[binding.lineChart.getData().getDataSets().size()];
                for (int j = 0; j < binding.lineChart.getData().getDataSets().size(); j++) {

                    IDataSet iDataSet = binding.lineChart.getData().getDataSets().get(j);

                    for (int i = 0; i < ((LineDataSet) iDataSet).getValues().size(); i++) {
                        if (((LineDataSet) iDataSet).getValues().get(i).getX() == e.getX()) {
                            highlight[j] = new Highlight(e.getX(), e.getY(), j);
                        }
                    }
                }

                binding.lineChart.highlightValues(highlight);
            }

            @Override
            public void onNothingSelected() {

            }
        });
        binding.lineChart.invalidate();
    }

    private void updateDate() {

        String startDateStr, endDateStr;
        String dateStr = "";

        switch (chartInterval) {
            case INTERVAL_1D:
                dateStr = new SimpleDateFormat("MMM dd, yyyy").format(new Date(myStartTime * 1000));
                break;

            case INTERVAL_7D:
            case INTERVAL_4W:
                startDateStr = new SimpleDateFormat("dd MMM yy").format(new Date(myStartTime * 1000));
                endDateStr = new SimpleDateFormat("dd MMM yy").format(new Date(myEndTime * 1000));
                dateStr = startDateStr + " - " + endDateStr;
                break;

            case INTERVAL_1Y:
                startDateStr = new SimpleDateFormat("MMM yyyy").format(new Date(myStartTime * 1000));
                endDateStr = new SimpleDateFormat("MMM yyyy").format(new Date(myEndTime * 1000));
                dateStr = startDateStr + " - " + endDateStr;
                break;
        }
        binding.tvDate.setText(dateStr);
    }

    private void changeChartType(int newType) {

        switch (newType) {

            case TYPE_BAR_CHART:
                binding.barChart.setVisibility(View.VISIBLE);
                binding.lineChart.setVisibility(View.GONE);
                break;

            case TYPE_LINE_CHART:
                binding.barChart.setVisibility(View.GONE);
                binding.lineChart.setVisibility(View.VISIBLE);
                break;
        }
        chartType = newType;
        updateChart();
    }

    private void setNextBtnVisibility() {
        Calendar calendar = com.espressif.ui.Utils.getCalendarForTimeZone(timeZone);
        long time = calendar.getTimeInMillis();
        Date currentDate = new Date(time);
        Date endDate = new Date(myEndTime * 1000);

        if (isSameDay(endDate, currentDate)) {
            binding.ivNext.setVisibility(View.GONE);
        } else {
            binding.ivNext.setVisibility(View.VISIBLE);
        }
    }

    private boolean isSameDay(Date date1, Date date2) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
        return fmt.format(date1).equals(fmt.format(date2));
    }

    /**
     * This method is used to check param data type.
     * Only int & float data types are supported currently.
     *
     * @return Returns true of param data type is supported, false otherwise.
     */
    private boolean isDataTypeSupported() {
        String dataType = param.getDataType();
        return !TextUtils.isEmpty(dataType) && (dataType.equals("int") || dataType.equals("float"));
    }

    private void displayDataTypeNotSupported() {
        binding.barChart.setVisibility(View.INVISIBLE);
        binding.lineChart.setVisibility(View.INVISIBLE);
        binding.tvNoData.setVisibility(View.VISIBLE);
        binding.tvNoData.setText(R.string.ts_data_type_not_supported);
        hideLoading();
    }

    private void showLoading() {
        binding.progressIndicator.setVisibility(View.VISIBLE);
        for (int i = 0; i < binding.sgChartInterval.getChildCount(); i++) {
            binding.sgChartInterval.getChildAt(i).setEnabled(false);
        }
        for (int i = 0; i < binding.sgAggregateType.getChildCount(); i++) {
            binding.sgAggregateType.getChildAt(i).setEnabled(false);
        }
        for (int i = 0; i < binding.sgChartType.getChildCount(); i++) {
            binding.sgChartType.getChildAt(i).setEnabled(false);
        }
        binding.ivPrev.setEnabled(false);
        binding.ivNext.setEnabled(false);
    }

    private void hideLoading() {
        binding.progressIndicator.setVisibility(View.GONE);
        for (int i = 0; i < binding.sgChartInterval.getChildCount(); i++) {
            binding.sgChartInterval.getChildAt(i).setEnabled(true);
        }
        for (int i = 0; i < binding.sgAggregateType.getChildCount(); i++) {
            binding.sgAggregateType.getChildAt(i).setEnabled(true);
        }
        for (int i = 0; i < binding.sgChartType.getChildCount(); i++) {
            binding.sgChartType.getChildAt(i).setEnabled(true);
        }
        binding.ivPrev.setEnabled(true);
        binding.ivNext.setEnabled(true);
    }
}
