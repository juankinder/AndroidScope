package com.devs.android.scope.ui;

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class CanvasMeasure extends View {
	private Paint paint_text;

	private double max_voltage;
	private String zoom;
	private String voltage;
	private String peek2peek;
	private int divisions = 0;

	private int canvasWidth = 0;
	private int canvasHeight = 0;

	public CanvasMeasure(Context context, int divisions) {
		super(context);
		this.divisions = divisions;

		setLayerType(View.LAYER_TYPE_HARDWARE, null);
		setWillNotDraw(false);
		
		paint_text = new Paint();
		paint_text.setColor(Color.BLACK);
		paint_text.setAntiAlias(true);
		paint_text.setTextSize(25.0f);
		paint_text.setStrokeWidth(1.0f);
		paint_text.setStyle(Paint.Style.FILL);
	}

	public void setSampleRate(int frecuency, int bufferSize) {
		zoom = new DecimalFormat("##.## us/div").format(1000 * ((float) 1 / frecuency)
				* (bufferSize / (float) divisions));
	}

	public void setVoltage(double max_voltage) {
		voltage = new DecimalFormat("##.##  V/div").format(max_voltage / divisions);
		this.max_voltage = max_voltage;
	}

	public void setPeek2Peek(double peek2peek) {
		this.peek2peek = new DecimalFormat("##.## Vpap").format(peek2peek
				* max_voltage / 255);
	}

	@Override
	protected void onDraw(Canvas canvas) {
//		super.onDraw(canvas);

		canvasWidth = canvas.getWidth();
		canvasHeight = canvas.getHeight();

		if (zoom != null) {
			canvas.drawText(zoom, canvasWidth - 140, canvasHeight - 10,
					paint_text);
		}

		if (voltage != null) {
			canvas.drawText(voltage, canvasWidth - 140, canvasHeight - 40,
					paint_text);
		}

		if (peek2peek != null) {
			canvas.drawText(peek2peek, canvasWidth - 140, 40, paint_text);
		}
	}

}
