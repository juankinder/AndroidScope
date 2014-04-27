package com.devs.android.scope.ui;

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Clase para la generacion del indicador del trigger
 */
public class CanvasTrigger extends View {

	// Color de la linea
	static final int LINE_COLOR = Color.GREEN;

	private Paint paint;
	private Paint paint_text;

	// Nivel y texto del trigger
	private float trigger_level;
	private String trigger_text;
	
	private double maxVoltage;

	/**
	 * Constructor de la clase
	 * 
	 * @param context
	 *            Contexto
	 */
	public CanvasTrigger(Context context) {
		super(context);

		paint = new Paint();
		paint.setColor(Color.GREEN);
		
		setLayerType(View.LAYER_TYPE_HARDWARE, null);
		setWillNotDraw(false);

		paint_text = new Paint();
		paint_text.setColor(Color.GREEN);
		paint_text.setAntiAlias(true);
		paint_text.setTextSize(25.0f);
		paint_text.setStrokeWidth(1.0f);
		paint_text.setStyle(Paint.Style.FILL);

		// Setea el nivel de trigger en 0
		setTriggerLevel(128);
		maxVoltage = 0;
	}

	/**
	 * Setea el nivel de trigger
	 * 
	 * @param level
	 *            Valor al que se setea el trigger (entre 0 y 255)
	 */
	public void setTriggerLevel(int level) {
		trigger_level = level;
		trigger_text = "Trigger: " + new DecimalFormat("##.## V").format(((float) (level - 128) * maxVoltage) / 255);
	}
	
	public void setMaxVoltage(double max_voltage) {
		maxVoltage = max_voltage;
		trigger_text = "Trigger: " + new DecimalFormat("##.## V").format(((float) (trigger_level - 128) * maxVoltage) / 255);
	}

	@Override
	protected void onDraw(Canvas canvas) {
//		super.onDraw(canvas);

		int ypos = (int) (canvas.getHeight() * (1 - ((float) trigger_level / 255)));
		canvas.drawLine(0, ypos, canvas.getWidth(), ypos, paint);
		canvas.drawText(trigger_text, 10, ypos, paint_text);
	}
}