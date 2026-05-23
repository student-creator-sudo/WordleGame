package com.example.wordle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class BarChartView extends View {
    private int[] values = new int[6];
    private int max = 1;
    private Paint barPaint = new Paint();
    private Paint textPaint = new Paint();

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(Color.parseColor("#3F51B5"));
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setValues(int[] values) {
        this.values = values;
        this.max = 1;
        for (int v : values) if (v > max) max = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int numBars = 7;
        int margin = 24;
        int availableWidth = width - 2 * margin;
        int barSpacing = 16;
        int barWidth = (availableWidth + barSpacing) / numBars - barSpacing;
        int baseY = height - 60;
        int chartHeight = height - 120;
        for (int i = 0; i < numBars; i++) {
            int dataValue;
            String label;

            if (i == 0) {
                // Bar 0 is the "Fail" bar.
                dataValue = values[6];
                label = "F";
            } else {
                // Bars 1-6 are for Attempts 1-6.
                // Subtract 1 from 'i' to get the correct data from the array.
                dataValue = values[i - 1];
                label = String.valueOf(i);
            }

            int barHeight = max > 0 ? (int) (chartHeight * (dataValue / (float) max)) : 0;
            int left = margin + i * (barWidth + barSpacing);
            int top = baseY - barHeight;
            int right = left + barWidth;
            int bottom = baseY;

            canvas.drawRect(left, top, right, bottom, barPaint);

            // Draw the label (F, 1, 2, 3, 4, 5, 6)
            canvas.drawText(label, left + barWidth / 2f, baseY + 40, textPaint);

            // Draw the actual number value above the bar
            canvas.drawText(String.valueOf(dataValue), left + barWidth / 2f, top - 10, textPaint);
        }
    }
}
