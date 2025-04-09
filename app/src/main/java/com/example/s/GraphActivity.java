package com.example.s;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class GraphActivity extends ComponentActivity {

    private LineChart lineChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        lineChart = findViewById(R.id.lineChart);

        lineChart.setBackgroundColor(getResources().getColor(android.R.color.white));

        loadGraphData();
    }

    private void loadGraphData() {
        ArrayList<Entry> entries = new ArrayList<>();
        int index = 0;

        try {
            FileInputStream fis = openFileInput("heartbeat_data.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    float value = Float.parseFloat(line.trim());
                    entries.add(new Entry(index++, value));
                } catch (NumberFormatException e) {
                    // Log the invalid data for debugging purposes
                    e.printStackTrace();
                    continue; // Skip this invalid line
                }
            }
            reader.close();

            if (entries.isEmpty()) {
                Toast.makeText(this, "No heartbeat data found.", Toast.LENGTH_SHORT).show();
                return; // Early exit if no valid data was found
            }

            // Customize the dataset and chart
            LineDataSet dataSet = new LineDataSet(entries, "Frequenza Cardiaca");
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawValues(false);
            dataSet.setColor(getResources().getColor(R.color.colorPrimary));  // Custom line color
            dataSet.setCircleColor(getResources().getColor(R.color.colorPrimary));

            LineData lineData = new LineData(dataSet);
            lineChart.setData(lineData);
            lineChart.invalidate(); // Refresh the chart
            lineChart.setTouchEnabled(true);  // Enable touch gestures for zoom and pan
            lineChart.setDragEnabled(true);  // Enable drag gestures
            lineChart.setScaleEnabled(true); // Enable zooming

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading data", Toast.LENGTH_SHORT).show();
        }
    }
}
