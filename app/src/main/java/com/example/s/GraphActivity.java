package com.example.s;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.CheckBox;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class GraphActivity extends AppCompatActivity {

    private LineChart lineChart;
    private LineDataSet heartRateDataSet, saturationDataSet, temperatureDataSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        lineChart = findViewById(R.id.line_chart);

        CheckBox heartRateCheckBox = findViewById(R.id.checkbox_heart_rate);
        CheckBox saturationCheckBox = findViewById(R.id.checkbox_saturation);
        CheckBox temperatureCheckBox = findViewById(R.id.checkbox_temperature);

        // Get health data from intent
        List<String[]> healthDataList = (List<String[]>) getIntent().getSerializableExtra("HEALTH_DATA");

        if (healthDataList != null) {
            setupGraph(healthDataList);
        }

        // Set listeners for checkboxes
        heartRateCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> toggleLineVisibility(heartRateDataSet, isChecked));
        saturationCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> toggleLineVisibility(saturationDataSet, isChecked));
        temperatureCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> toggleLineVisibility(temperatureDataSet, isChecked));
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

        heartRateDataSet = new LineDataSet(heartRateEntries, "Heart Rate");
        heartRateDataSet.setColor(Color.RED);
        heartRateDataSet.setLineWidth(2f);
        heartRateDataSet.setCircleColor(Color.RED);
        heartRateDataSet.setCircleRadius(4f);

        saturationDataSet = new LineDataSet(saturationEntries, "Saturation");
        saturationDataSet.setColor(Color.BLUE);
        saturationDataSet.setLineWidth(2f);
        saturationDataSet.setCircleColor(Color.BLUE);
        saturationDataSet.setCircleRadius(4f);

        temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature");
        temperatureDataSet.setColor(Color.GREEN);
        temperatureDataSet.setLineWidth(2f);
        temperatureDataSet.setCircleColor(Color.GREEN);
        temperatureDataSet.setCircleRadius(4f);

        LineData lineData = new LineData(heartRateDataSet, saturationDataSet, temperatureDataSet);
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

    private void toggleLineVisibility(LineDataSet dataSet, boolean isVisible) {
        dataSet.setVisible(isVisible);
        lineChart.invalidate();
    }
}
