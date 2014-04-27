package com.devs.android.scope.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Clase para la generacion de las grillas en el canvas
 */
public class CanvasGrid extends View {

	// Cantidad de divisiones
	private int divisions = 10;

	// Colores de las lineas
	static final int DIVISIONES_COLOR = Color.LTGRAY;
	static final int DIVISIONES_COLOR_MAIN = Color.GRAY;

	Paint paint = null;
	Paint paint_main = null;
	
	/**
	 * Constructor de la clase
	 * 
	 * @param context
	 *            Contexto
	 * @param div_amount
	 *            Cantidad de divisiones que la grilla debe tener
	 */
	public CanvasGrid(Context context, int div_amount) {
		super(context);
		divisions = div_amount;
		
		setWillNotDraw(false);
		setLayerType(View.LAYER_TYPE_HARDWARE, null);

		paint = new Paint();
		paint.setColor(DIVISIONES_COLOR);

		paint_main = new Paint();
		paint_main.setColor(DIVISIONES_COLOR_MAIN);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		float width = canvas.getWidth();
		float height = canvas.getHeight();
		float xpos = width / divisions;
		float ypos = height / divisions;

		for (int i = 1; i <= divisions; i++) {
			Paint temp_paint = paint;

			if (i == divisions / 2) {
				temp_paint = paint_main;
			}
			canvas.drawLine((xpos * i), 0, (xpos * i), height, temp_paint);
			canvas.drawLine(0, (ypos * i), width, (ypos * i), temp_paint);
		}
	}
}