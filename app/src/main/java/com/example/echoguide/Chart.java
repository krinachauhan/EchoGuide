package com.example.echoguide;

import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chart extends AppCompatActivity {
    String Android_id;
    DatabaseReference databaseReference;
    FirebaseDatabase firebaseDatabase;
    List<String> activityNames;
    BarChart barChart;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart);
         barChart = findViewById(R.id.idBarChart);

        Android_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        databaseReference = firebaseDatabase.getInstance().getReference("EchoGuide");
        String date = "26-04-2024";
        databaseReference.child(Android_id).child(date).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (task.isSuccessful()) {
                    if (task.getResult().exists()) {
                        DataSnapshot dataSnapshot = task.getResult();
                        activityNames = new ArrayList<>();
                        for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
                            String activityName = childSnapshot.child("nameActivity").getValue(String.class);
                            activityNames.add(activityName);
                            //  Toast.makeText(Chart.this,"User Doesn't Exist"+dataSnapshot,Toast.LENGTH_SHORT).show();
                        }
                       createBarChart();
                //  Toast.makeText(Chart.this, "User Doesn" + activityNames, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Chart.this, "User Doesn't Exist", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(Chart.this, "Failed to read", Toast.LENGTH_SHORT).show();
                }
            }
        });
   }
    private void createBarChart() {

        Map<String, Integer> countMap = new HashMap<>();
        for (String item : activityNames) {
            countMap.put(item, countMap.getOrDefault(item, 0) + 1);
        }

// Create entries from the count map
        List<BarEntry> entries = new ArrayList<>();
        int index = 0;
        List<String> labels = new ArrayList<>(countMap.keySet()); // Store labels for x-axis
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue()));
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Activity Name");
        dataSet.setColors(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.MAGENTA); // Setting colors for bars

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.9f); // Adjust bar width if needed

// Set x-axis labels
        String[] labelsArray = labels.toArray(new String[0]);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labelsArray));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setGranularity(1f);

// Rotate the x-axis labels

        barChart.setData(barData);
        barChart.getDescription().setEnabled(false);
        barChart.animateY(2000); // Animate the chart
        barChart.invalidate(); // refresh
    }




    }
