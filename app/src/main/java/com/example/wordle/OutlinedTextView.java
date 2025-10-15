package com.example.wordle;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class OutlinedTextView extends AppCompatTextView {

    private boolean hasStroke = false;
    private float strokeWidth = 0;
    private int strokeColor = 0;

    public OutlinedTextView(Context context) {
        super(context);
        init(null);
    }

    public OutlinedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.OutlinedTextView);
            hasStroke = a.hasValue(R.styleable.OutlinedTextView_outlineColor); // Check if outlineColor is set
            if (hasStroke) {
                strokeWidth = a.getDimensionPixelSize(R.styleable.OutlinedTextView_outlineWidth, 1);
                strokeColor = a.getColor(R.styleable.OutlinedTextView_outlineColor, 0xff000000); // Default to black
            }
            a.recycle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // If there's no stroke defined, or width is zero, just do normal drawing.
        if (!hasStroke || strokeWidth <= 0) {
            super.onDraw(canvas);
            return;
        }

        // It's important to save the original text color
        int originalTextColor = getCurrentTextColor(); // More reliable way to get original fill color

        TextPaint paint = getPaint();

        // --- 1. Draw the STROKE ---
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(this.strokeWidth);
        // Temporarily set the TextView's color to the strokeColor for the stroke draw pass
        // This is because super.onDraw() uses the TextView's current color.
        setTextColor(this.strokeColor);
        super.onDraw(canvas); // This draws the text with stroke settings

        // --- 2. Restore and Draw the FILL ---
        paint.setStyle(Paint.Style.FILL); // Set style back to fill
        // Restore the original text color for the fill pass
        setTextColor(originalTextColor);
        super.onDraw(canvas); // This draws the text again, this time filled, on top of the stroke
    }

    // Optional: Add programmatic setters if you want to change these at runtime
    public void setOutlineColor(int color) {
        this.strokeColor = color;
        this.hasStroke = true;
        invalidate(); // Redraw
    }

    public void setOutlineWidth(float width) {
        this.strokeWidth = width;
        this.hasStroke = true;
        invalidate(); // Redraw
    }

    public void removeOutline() {
        this.hasStroke = false;
        invalidate(); // Redraw
    }
}