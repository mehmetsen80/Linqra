package org.lite.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrendAnalysis {
    private final double percentageChange;
    private final TrendDirection direction;

    // Helper method to create trend analysis from two values
    public static TrendAnalysis fromValues(double recent, double previous) {
        if (previous == 0) return new TrendAnalysis(0.0, TrendDirection.STABLE);
        
        double percentageChange = ((recent - previous) / previous) * 100;
        TrendDirection direction = 
            percentageChange > 5 ? TrendDirection.INCREASING :
            percentageChange < -5 ? TrendDirection.DECREASING :
            TrendDirection.STABLE;
            
        return new TrendAnalysis(percentageChange, direction);
    }

    @Override
    public String toString() {
        return "TrendAnalysis{" +
                "percentageChange=" + percentageChange +
                ", direction=" + direction +
                '}';
    }
}