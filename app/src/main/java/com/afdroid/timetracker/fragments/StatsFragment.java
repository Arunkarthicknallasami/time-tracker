package com.afdroid.timetracker.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStats;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afdroid.timetracker.R;
import com.afdroid.timetracker.Utils.AppHelper;
import com.afdroid.timetracker.chartformatter.DayAxisValueFormatter;
import com.afdroid.timetracker.preferences.TimeTrackerPrefHandler;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class StatsFragment extends Fragment {
    private BarChart barChart;

    private TextView startTime;
    private TextView endTime;

    private View rootView;

    private final int DAILY = AppHelper.DAILY_STATS;
    private final int YESTERDAY = AppHelper.YESTERDAY_STATS;
    private final int WEEKLY = AppHelper.WEEKLY_STATS;
    private final int MONTHLY = AppHelper.MONTHLY_STATS;

    private int selectedPeriod = 0;
    private int mode = 0;
    private List<String> appList = null;
    private List<String> appNameList = null;

    private Context context;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Log.e("NETWORK_USAGE", "createview");
        context = getActivity().getApplicationContext();
        rootView = inflater.inflate(R.layout.fragment_stats, container, false);
        Bundle args = getArguments();
        selectedPeriod = args.getInt("period", 0);
        barChart = (BarChart) rootView.findViewById(R.id.chart1);
        barChart.setNoDataText(getResources().getString(R.string.no_data));
        startTime = (TextView) rootView.findViewById(R.id.tvStartTime);
        endTime = (TextView) rootView.findViewById(R.id.tvEndTime);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e("NETWORK_USAGE", "resume");
        mode = TimeTrackerPrefHandler.INSTANCE.getMode(context);
        String serialized = TimeTrackerPrefHandler.INSTANCE.getPkgList(context);
        if (serialized != null) {
            appList = null;
            appNameList = null;
            appList = new LinkedList<String>(Arrays.asList(TextUtils.
                    split(serialized, ",")));
            appNameList = new LinkedList<String>();
        }
        if (mode == 1 && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)) {
           showAlert();
        }
        getStatsInfo();
    }

    private void showAlert() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.network_stats)
                .setMessage(R.string.networks_stats_na)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void getStatsInfo() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long millis = 0;

        Date endresultdate = new Date(System.currentTimeMillis());
        switch (selectedPeriod) {
            case DAILY:
                millis = calendar.getTimeInMillis();
                break;
            case YESTERDAY:
                calendar.set(Calendar.HOUR_OF_DAY, -24);
                millis = calendar.getTimeInMillis();
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 99);
                Date date = calendar.getTime();
                endresultdate = date;
                break;
            case WEEKLY:
                calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
                millis = calendar.getTimeInMillis();
                break;
            case MONTHLY:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                millis = calendar.getTimeInMillis();
                break;
            default:
                break;
        }

        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
        Date startresultdate = new Date(millis);

        Map<String, UsageStats> lUsageStatsMap = AppHelper.getUsageStatsManager().
                queryAndAggregateUsageStats(
                        millis,
                        System.currentTimeMillis());

        startTime.setText("From " + sdf.format(startresultdate));
        endTime.setText("To " + sdf.format(endresultdate));

        if (appList != null) {
            PackageManager packageManager = context.getPackageManager();
            ArrayList<Float> values = new ArrayList<Float>();
            ApplicationInfo info = null;

            for (int i = 0; i < appList.size(); i++) {
                String appPkg = appList.get(i);
                String appname = null;
                int uid = 0;
                try {
                    appname = (String) packageManager.getApplicationLabel(packageManager.
                            getApplicationInfo(appPkg, PackageManager.GET_META_DATA));
                    info = packageManager.getApplicationInfo(appPkg, 0);

                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }


                if (appname != null) {
                    appNameList.add(appname);


                    if (mode == 1) {
                        // network test
                        uid = info.uid;
                        NetworkStatsManager networkStatsManager;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
                            NetworkStats nwStats = networkStatsManager.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, millis, System.currentTimeMillis(), uid);
                            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                            nwStats.getNextBucket(bucket);
                            double received = (double) bucket.getRxBytes() / (1024 * 1024);
                            double send = (double) bucket.getTxBytes() / (1024 * 1024);
                            double total = received + send;

                            NetworkStats nwStats1 = networkStatsManager.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, millis, System.currentTimeMillis(), uid);
                            NetworkStats.Bucket bucket1 = new NetworkStats.Bucket();
                            nwStats1.getNextBucket(bucket1);
                            double received1 = (double) bucket1.getRxBytes() / (1024 * 1024);
                            double send1 = (double) bucket1.getTxBytes() / (1024 * 1024);
                            double total1 = received1 + send1;
                            double totalAll = total + total1;

                            values.add(((float) totalAll));

//                        Log.e("NETWORK_USAGE", "appname : "+appname+ " : "+String.format("%.2f", totalAll) + " MB");
                        } else {
                            values.add(0.0f);
                        }
                    } else {
                        if (lUsageStatsMap.containsKey(appPkg)) {
                            if (selectedPeriod == DAILY) {
                                values.add(AppHelper.getMinutes(lUsageStatsMap.get(appPkg).
                                        getTotalTimeInForeground()));
                            } else {
                                values.add(AppHelper.getHours(lUsageStatsMap.get(appPkg).
                                        getTotalTimeInForeground()));
                            }

                        } else {
                            //if device does not contain the app usage data
                            values.add(0.0f);
                        }
                    }


                } else {
                    appList.remove(appPkg);
                }
            }
            saveAppPreference();
            Log.d(AppHelper.TAG, "applist size - " + appList.size());
            Log.d(AppHelper.TAG, "appnamelist size - " + appNameList.size());
            Log.d(AppHelper.TAG, "values size - " + values.size());
            if (values.size() != 0) {
                setChart(values);
            }
        }
    }

    private void saveAppPreference() {
        TimeTrackerPrefHandler.INSTANCE.savePkgList
                (TextUtils.join(",", appList), context);
    }

    private void setChart(ArrayList<Float> values) {
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);

        // if more than 60 entries are displayed in the chart, no values will be
        // drawn
//        barChart.setMaxVisibleValueCount(60);
        barChart.setVisibleXRangeMaximum(6);
        barChart.moveViewToX(10);

        // scaling can now only be done on x- and y-axis separately
        barChart.setPinchZoom(false);

        barChart.setDrawGridBackground(false);
        // barChart.setDrawYLabels(false);
        IAxisValueFormatter xAxisFormatter = new DayAxisValueFormatter(barChart, appNameList);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f); // only intervals of 1 day
        xAxis.setLabelCount(appNameList.size());
        xAxis.setValueFormatter(xAxisFormatter);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setLabelCount(10, false);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setSpaceTop(15f);
        leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false);

        Legend l = barChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        l.setForm(Legend.LegendForm.SQUARE);
        l.setFormSize(9f);
        l.setTextSize(11f);
//        l.setXEntrySpace(4f);

        setData(values);
    }

    private void setData(ArrayList<Float> values) {
        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        for (int i = 0; i < values.size(); i++) {
            float val = values.get(i);
            yVals1.add(new BarEntry(i, val));
        }

        BarDataSet set1;

        if (barChart.getData() != null &&
                barChart.getData().getDataSetCount() > 0) {
            set1 = (BarDataSet) barChart.getData().getDataSetByIndex(0);
            set1.setValues(yVals1);
            barChart.getData().notifyDataChanged();
            barChart.notifyDataSetChanged();
        } else {
            if (selectedPeriod == DAILY) {
                set1 = new BarDataSet(yVals1, ((mode == 0) ? "App" : "Network") + " usage in " + ((mode == 0) ? "Minutes" : "MB"));
            } else {
                set1 = new BarDataSet(yVals1, ((mode == 0) ? "App" : "Network") + " usage in " + ((mode == 0) ? "Hours" : "MB"));
            }
            set1.setDrawIcons(false);
            set1.setColors(ColorTemplate.LIBERTY_COLORS);
            ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
            dataSets.add(set1);
            BarData data = new BarData(dataSets);
            data.setValueTextSize(10f);
            data.setBarWidth(0.2f);
            barChart.setData(data);
            barChart.setVisibleXRangeMaximum(4.0f);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }
}
