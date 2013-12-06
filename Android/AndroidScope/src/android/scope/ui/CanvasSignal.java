package android.scope.ui;

import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.preference.PreferenceManager;
import android.scope.MainActivity;
import android.util.Log;
import android.view.View;

public class CanvasSignal extends View {

	private final String TAG = MainActivity.class.getSimpleName();

	private final int SIGNAL_COLOR = Color.BLACK;
	private final Style SIGNAL_STYLE = Style.STROKE;

	private Paint paint;

	private Path path;
	int i, x, y;

	private int canvasWidth = 0;
	private int canvasHeight = 0;

	public CanvasSignal(Context context, int softAmount) {
		super(context);

		paint = new Paint();
		paint.setColor(SIGNAL_COLOR);
		paint.setStyle(SIGNAL_STYLE);

		final CornerPathEffect cornerPathEffect = new CornerPathEffect(
				softAmount);
		paint.setPathEffect(cornerPathEffect);

		setSignal(null, 0);
	}

	public void setSignal(List<Integer> signal, float delta) {
		if (signal == null) {
			return;
		}

		path = new Path();

		try {
			for (i = 0; i < signal.size(); i++) {
				x = (int) ((i * (float) canvasWidth / (float) (signal.size() - 1)) + (float) (1 - delta));
				y = (int) (canvasHeight * (1 - ((float) signal.get(i) / 255)));
				path.lineTo(x, y);
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		canvasWidth = canvas.getWidth();
		canvasHeight = canvas.getHeight();

		if (path != null) {
			canvas.drawPath(path, paint);
		}
	}
}
