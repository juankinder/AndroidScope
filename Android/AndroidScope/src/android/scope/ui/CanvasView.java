package android.scope.ui;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.scope.MainActivity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CanvasView extends SurfaceView implements Runnable {

	private Thread canvasThread;
	private SurfaceHolder surfaceHolder;
	private CanvasGrid canvasGrid;
	private CanvasSignal canvasSignal;
	private CanvasTrigger canvasTrigger;
	private CanvasMeasure canvasMeasure;

	private ScaleGestureDetector detector;

	private Puntero puntero;

	// Parametros de Zoom
	private static float MIN_ZOOM = 2f;
	private static float MAX_ZOOM = 10000f;
	private float scaleFactor = 0;

	private final int DIVISIONS = 10;

	// Parametros de trigger
	private final static int TRIGGER_ZONE = 5;

	private MainActivity activity;
	volatile boolean running = false;
	
	
	private boolean triggerEnabled = true;
	private boolean meassureEnabled = true;

	public CanvasView(Context context) {
		super(context);
	}
	
	public CanvasView(MainActivity activity, int initialSamples, int softAmount) {
		super(activity);
		this.activity = activity;
		scaleFactor = initialSamples;

		// lo registro para se entere si hay cambios
		surfaceHolder = getHolder();

		// setFocusable(true);
		detector = new ScaleGestureDetector(activity, new ScaleListener());

		canvasGrid = new CanvasGrid(activity, DIVISIONS);
		canvasSignal = new CanvasSignal(activity, softAmount);
		canvasTrigger = new CanvasTrigger(activity);
		canvasMeasure = new CanvasMeasure(activity, DIVISIONS);
	}

	public void onResumeCanvasView() {
		running = true;
		canvasThread = new Thread(this);
		canvasThread.start();
	}

	public void onPauseCanvasView() {
		boolean retry = true;
		running = false;
		while (retry) {
			try {
				canvasThread.join();
				retry = false;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Setea la se–al a graficar
	 * 
	 * @param signal
	 *            : Array de floats (-1 a 1) con la se–al a graficar
	 */
	public void setSignal(List<Integer> signal) {
		setSignal(signal, 0);
	}
	
	/**
	 * Setea la se–al a graficar
	 * 
	 * @param signal
	 *            : Array de floats (-1 a 1) con la se–al a graficar
	 */
	public void setSignal(List<Integer> signal, float delta) {
		if (canvasSignal != null) {
			canvasSignal.setSignal(signal, delta);
		}
	}

	public void setTriggerLevel(int level) {
		if (canvasTrigger != null)
			canvasTrigger.setTriggerLevel(level);
	}
	
	public void setSampleRate(int frecuency, int bufferSize) {
		canvasMeasure.setSampleRate(frecuency, bufferSize);
	}

	public void setPlay(boolean playing) {
		triggerEnabled = playing;
	}
	
	public void setMaxVoltage(double maxVoltage) {
		canvasMeasure.setVoltage(maxVoltage);
		canvasTrigger.setMaxVoltage(maxVoltage);
	}
	
	public void setPeek2Peek(double peek2peek) {
		canvasMeasure.setPeek2Peek(peek2peek);
	}

	@Override
	public void run() {
		while (running) {
			if (surfaceHolder.getSurface().isValid()) {
				Canvas canvas = surfaceHolder.lockCanvas();
				try {

					canvas.drawColor(Color.WHITE);

					if (canvasGrid != null) {
						canvasGrid.draw(canvas);
					}

					if (canvasTrigger != null && triggerEnabled) {
						canvasTrigger.draw(canvas);
					}

					if (canvasSignal != null) {
						canvasSignal.draw(canvas);
					}
					
					if (canvasMeasure != null && meassureEnabled) {
						canvasMeasure.draw(canvas);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
				surfaceHolder.unlockCanvasAndPost(canvas);

			}
		}
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			scaleFactor /= detector.getScaleFactor();
			scaleFactor = Math.max(MIN_ZOOM, Math.min(scaleFactor, MAX_ZOOM));

			activity.setHorizontalZoom((int) scaleFactor);

			return true;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		try {
			detector.onTouchEvent(event);
			handleSingleTouch(event);
		} catch (Exception e) {
			// Log.e(TAG, "Error " + e.getMessage());
		}
		return true;
	}

	private void handleSingleTouch(MotionEvent event) {
		int actionMasked = event.getActionMasked();
		int xPosition;
		int yPosition;

		switch (actionMasked) {

		case MotionEvent.ACTION_DOWN: // Presion
			xPosition = (int) event.getX() * 100 / this.getRight();
			yPosition = (int) event.getY() * 100 / this.getBottom();
			puntero = (xPosition < TRIGGER_ZONE) ? new PunteroTrigger(yPosition) : new PunteroDesplazamiento(xPosition);
			break;

		case MotionEvent.ACTION_MOVE: // Movimiento
			xPosition = (int) event.getX() * 100 / this.getRight();
			yPosition = (int) event.getY() * 100 / this.getBottom();
			puntero.move(xPosition, yPosition);
			break;
		}
	}

	private abstract class Puntero {
		public abstract void move(int newXposition, int newYposition);
	}

	private class PunteroTrigger extends Puntero {

		public PunteroTrigger(int newYposition) {
			activity.setTriggerLevel(convertPercentage(newYposition));
		}

		@Override
		public void move(int newXposition, int newYposition) {
			activity.setTriggerLevel(convertPercentage(newYposition));
		}

		private int convertPercentage(int yPosition) {
			return (int) (255 * (1 - ((float) yPosition / 100)));
		}
	}

	private class PunteroDesplazamiento extends Puntero {
		private int lastXlocation = 0;

		public PunteroDesplazamiento(int newXposition) {
			lastXlocation = newXposition;
		}

		@Override
		public void move(int newXposition, int newYposition) {
			activity.graphSignalStatic(lastXlocation - newXposition);
			lastXlocation = newXposition;
		}
	}
	
	public void fftMode(boolean enabled, int divisions) {
		triggerEnabled = !enabled;
		meassureEnabled = !enabled;
		if(!enabled) {
			divisions = DIVISIONS;
		}
		canvasGrid = new CanvasGrid(activity, divisions);

	}
}
