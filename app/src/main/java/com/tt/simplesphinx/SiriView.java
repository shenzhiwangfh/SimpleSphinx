/* Copyright (C) 2018 Tcl Corporation Limited */
package com.tt.simplesphinx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;

public class SiriView extends View {
    private Thread thread;
    private float targetHeight;
    private Paint paint;

    private float waveHeight;
    private int waveColor;
    private int waveAmount;
    private float waveSpeed;

    private ArrayList<Path> paths;
    private float amplitude = 1;
    private boolean isSet = false;

    private static final long SLEEP_TIME = 16;
    private static final float SILENT_WAVE_HEIGHT = 30.0f;
    private static final float WAVE_RISE_SPEED = 5.0f;
    private static final float WAVE_DROP_SPEED = 3.0f;
    private static final int SAMPLE_RATE_STEP = 15;

//    long startAt = 0;

    public SiriView(Context context) {
        super(context);
        init(context);
    }

    public SiriView(Context context, AttributeSet attr) {
        super(context, attr);
        init(context);
    }

    public SiriView(Context context, AttributeSet attr, int style) {
        super(context, attr, style);
        init(context);
    }

    private void init(Context context) {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        waveSpeed = 500.0f;
        waveAmount = 20;
        waveColor = Color.rgb(39, 188, 136);
        waveHeight = SILENT_WAVE_HEIGHT;
        targetHeight = 100.0f;
        thread = new Thread(runnable);
        thread.start();

        paths = new ArrayList<>(waveAmount);
        for (int i = 0; i < waveAmount; i++) {
            paths.add(new Path());
        }
//        startAt = System.currentTimeMillis();
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                invalidate();
            }
        }
    };

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    handler.sendEmptyMessage(1);
                    Thread.sleep(SLEEP_TIME);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private void lineChange() {
        if (waveHeight < targetHeight && isSet) {
            waveHeight += WAVE_RISE_SPEED;
        } else {
            isSet = false;
            if (waveHeight <= SILENT_WAVE_HEIGHT) {
                waveHeight = SILENT_WAVE_HEIGHT;
            } else {
                waveHeight -= WAVE_DROP_SPEED;
            }
        }
    }

    private long startAt;
    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }

    private void drawVoiceLine(Canvas canvas) {
        lineChange();
        paint.setColor(waveColor);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.save();
        int moveY = getHeight() / 2;
        for (int i = 0; i < paths.size(); i++) {
            paths.get(i).reset();
            paths.get(i).moveTo(getWidth(), getHeight() / 2);
        }

        //long startAt = System.currentTimeMillis(); //AIUIService.startAt;
        long millisPassed = System.currentTimeMillis() - startAt;

        float offset = millisPassed / waveSpeed;


        for (float j = getWidth(); j >= 0; j -= SAMPLE_RATE_STEP) {
            float i = j;

            amplitude = 4 * waveHeight * i / getWidth()
                    - 4 * waveHeight * i / getWidth() * i / getWidth();
            for (int n = 1; n <= paths.size(); n++) {
                float sin = amplitude * (float) Math.sin((i - Math.pow(1.22, n)) * Math.PI / 180 - offset);
                paths.get(n - 1).lineTo(j, (2 * n * sin / paths.size() - 15 * sin / paths.size() + moveY));
            }
        }
        for (int n = 0; n < paths.size(); n++) {
            if (n == paths.size() - 1) {
                paint.setAlpha(255);
            } else {
                paint.setAlpha(n * 130 / paths.size());
            }
            if (paint.getAlpha() > 0) {
                canvas.drawPath(paths.get(n), paint);
            }
        }
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawVoiceLine(canvas);
    }

    public void stop() {
        // To be fixed, once the animation is finished, the thread should be suspended.
    }

    public void setWaveHeight(float height) {
        if (height < 0.4f) {
            height = 0.4f;
        } else {
            isSet = true;
        }

        this.targetHeight = getHeight() * height / 3;
    }

    public void setWaveWidth(float width) {
        //this.waveWidth = width;
    }

    public void setWaveColor(int waveColor) {
        this.waveColor = waveColor;
    }

    public void setWaveOffsetX(float waveOffsetX) {
        //this.waveOffsetX = waveOffsetX;
    }

    public void setWaveAmount(int waveAmount) {
        //this.waveAmount = waveAmount;
    }

    public void setWaveSpeed(float waveSpeed) {
        //this.waveSpeed = waveSpeed;
    }
}