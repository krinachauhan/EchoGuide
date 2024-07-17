package com.example.echoguide;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.List;

public class CustomValueFormatter extends ValueFormatter {
    private final List<String> activityNames;

    public CustomValueFormatter(List<String> activityNames) {
        this.activityNames = activityNames;
    }

    @Override
    public String getBarLabel(BarEntry barEntry) {
        int index = (int) barEntry.getX();
        return activityNames.get(index);
    }
}
