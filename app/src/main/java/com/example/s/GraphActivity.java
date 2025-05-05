package com.example.s;

import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class GraphActivity extends ComponentActivity {

    private LineChart lineChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        lineChart = findViewById(R.id.line_chart);

        // Get health data from intent
        List<String[]> healthDataList = (List<String[]>) getIntent().getSerializableExtra("HEALTH_DATA");
        Log.d("GraphActivity", "Received health data: " + healthDataList);

        if (healthDataList != null) {
            setupGraph(healthDataList);
        }
    }

    private String loadHealthData() {
        SharedPreferences prefs = getSharedPreferences("HealthDataPrefs", MODE_PRIVATE);
        return prefs.getString("LastHealthData", "No health data available");
    }

    private void setupGraph(List<String[]> healthDataList) {
        List<Entry> heartRateEntries = new ArrayList<>();
        List<Entry> saturationEntries = new ArrayList<>();
        List<Entry> temperatureEntries = new ArrayList<>();

        for (int i = 0; i < healthDataList.size(); i++) {
            String[] data = healthDataList.get(i);
            float heartRate = Float.parseFloat(data[0]);
            float saturation = Float.parseFloat(data[1]);
            float temperature = Float.parseFloat(data[2]);

            heartRateEntries.add(new Entry(i, heartRate));
            saturationEntries.add(new Entry(i, saturation));
            temperatureEntries.add(new Entry(i, temperature));
        }

        LineData lineData = getLineData(heartRateEntries, saturationEntries, temperatureEntries);
        lineChart.setData(lineData);

        lineChart.setBackgroundColor(Color.WHITE);
        lineChart.getDescription().setText("Dati di salute");
        lineChart.getDescription().setTextSize(12f);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setGranularity(1f);

        lineChart.getAxisLeft().setTextColor(Color.BLACK);
        lineChart.getAxisLeft().setGranularity(1f);
        lineChart.getAxisLeft().setAxisMinimum(0f);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setTextColor(Color.BLACK);
        lineChart.invalidate();
    }

    @NonNull
    private static LineData getLineData(List<Entry> heartRateEntries, List<Entry> saturationEntries, List<Entry> temperatureEntries) {
        LineDataSet heartRateDataSet = new LineDataSet(heartRateEntries, "Heart Rate");
        heartRateDataSet.setColor(Color.RED);
        heartRateDataSet.setLineWidth(2f);
        heartRateDataSet.setCircleColor(Color.RED);
        heartRateDataSet.setCircleRadius(4f);

        LineDataSet saturationDataSet = new LineDataSet(saturationEntries, "Saturation");
        saturationDataSet.setColor(Color.BLUE);
        saturationDataSet.setLineWidth(2f);
        saturationDataSet.setCircleColor(Color.BLUE);
        saturationDataSet.setCircleRadius(4f);

        LineDataSet temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature");
        temperatureDataSet.setColor(Color.GREEN);
        temperatureDataSet.setLineWidth(2f);
        temperatureDataSet.setCircleColor(Color.GREEN);
        temperatureDataSet.setCircleRadius(4f);

        LineData lineData = new LineData(heartRateDataSet, saturationDataSet, temperatureDataSet);
        return lineData;
    }
}