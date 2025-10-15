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
        int numBars = 7; // Changed from 6 to 7
        int margin = 24;
        int availableWidth = width - 2 * margin;
        int barSpacing = 16;
        int barWidth = (availableWidth + barSpacing) / numBars - barSpacing;
        int baseY = height - 60;
        int chartHeight = height - 120;
        for (int i = 0; i < numBars; i++) {
            int barHeight = max > 0 ? (int) (chartHeight * (values[i] / (float) max)) : 0;
            int left = margin + i * (barWidth + barSpacing);
            int top = baseY - barHeight;
            int right = left + barWidth;
            int bottom = baseY;
            canvas.drawRect(left, top, right, bottom, barPaint);
            // Draw attempt number (1-6) below each bar, first bar is 'F' for fails
            String label = (i == 0) ? "F" : String.valueOf(i);
            canvas.drawText(label, left + barWidth / 2f, baseY + 40, textPaint);
            // Draw value above each bar
            canvas.drawText(String.valueOf(values[i]), left + barWidth / 2f, top - 10, textPaint);
        }
    }
}
