package com.devs.android.scope;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.R.drawable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import com.devs.android.scope.ui.CanvasView;
import com.devs.android.scope.usb.USBCommands;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class MainActivity extends Activity {
	// ////////////////////////////////////////////////////////
	// Variables globales de la aplicacion
	// ////////////////////////////////////////////////////////
	public static final String TAG = "AndroidScope";

	// Variables para el graficado
	private CanvasView canvasView;
	private Thread graphThread;
	private Handler graphHandler = new Handler();
	private static boolean displayFFT = false;

	// Conexion con los servicios
	private Intent USBserviceIntent;
	private Messenger messengerSend;

	// Almacenamiento de las muestras
	private SignalBuffer read_buffer;
	private int samples2display;

	// Componentes de UI
	private Menu SettingsMenu;
	private playPauseToggle playToogle;
	private boolean displayDC = true;
	private boolean triggerRaising = true;
	private ZoomValues externalZoom = ZoomValues.TEN;
	private int frecuency = 0;
	private Class<?> selectedClass = null;

	private SharedPreferences sharedPrefs;

	// Zoom
	public enum ZoomValues {
		ONE(1, R.id.zoom_1, USBCommands.ZOOM_X1, 40), TWO(2, R.id.zoom_2,
				USBCommands.ZOOM_X2, 20), FOUR(4, R.id.zoom_4,
				USBCommands.ZOOM_X4, 10), FIVE(5, R.id.zoom_5,
				USBCommands.ZOOM_X5, 8), EIGHT(8, R.id.zoom_8,
				USBCommands.ZOOM_X8, 5), TEN(10, R.id.zoom_10,
				USBCommands.ZOOM_X10, 4), SIXTEEN(16, R.id.zoom_16,
				USBCommands.ZOOM_X16, 2.5), THIRTYTWO(32, R.id.zoom_32,
				USBCommands.ZOOM_X32, 1.25);

		private int zoomValue;
		private int menuId;
		private USBCommands code;
		private double maxVoltage;

		private static final SparseArray<ZoomValues> zoomValuesAndIds = new SparseArray<ZoomValues>();
		static {
			ZoomValues[] allZooms = ZoomValues.values();
			for (ZoomValues zoom : allZooms) {
				zoomValuesAndIds.put(zoom.menuId, zoom);
			}
		}

		ZoomValues(int zoom, int menu, USBCommands code2send, double maxVoltage) {
			zoomValue = zoom;
			menuId = menu;
			code = code2send;
			this.maxVoltage = maxVoltage;
		}

		public int getZoomValue() {
			return zoomValue;
		}

		public USBCommands getCode() {
			return code;
		}

		static public ZoomValues getZoom(int menuId) {
			return zoomValuesAndIds.get(menuId);
		}

		public double getMaxVolgate() {
			return maxVoltage;
		}
	}

	// ////////////////////////////////////////////////////////
	// Metodos propios del ciclo de vida de la Actividad
	// ////////////////////////////////////////////////////////

	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

			canvasView = new CanvasView(this, Integer.parseInt(sharedPrefs
					.getString("prefInitSamplesToDisplay", "200")),
					Integer.parseInt(sharedPrefs.getString("prefRound", "50")));
			setContentView(canvasView);

			selectedClass = SignalGenerator.class;
			frecuency = SignalGenerator.getSampleRateKhz();

			// Inicializaciones
			initUSB();

			initBuffer();

			canvasView.setMaxVoltage(externalZoom.getMaxVolgate());

			// Hilo para el graficado
			graphThread = new Thread() {
				public void run() {
					graphSignal();
					graphHandler.postDelayed(this, Integer.parseInt(sharedPrefs
							.getString("prefRefreshRate", "10")));
				}
			};

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		SettingsMenu = menu;

		MenuItem playPauseMenu = SettingsMenu.findItem(R.id.menu_pause);
		playPauseMenu.setIcon(drawable.ic_media_pause);
		playToogle = new playPauseToggle(playPauseMenu, true);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case R.id.menu_trigger:
			showTriggerPopup();
			break;
		case R.id.menu_zoom:
			showZoomPopup();
			break;
		case R.id.menu_pause:
			playToogle.toogle();
			break;
		case R.id.menu_source:
			showSourcePopup();
			break;
		case R.id.menu_acdc:
			displayDC = !displayDC;
			item.setTitle(displayDC ? "DC" : "AC");
			break;
		case R.id.menu_fft:
			displayFFT = !displayFFT;
			canvasView.fftMode(displayFFT, frecuency / 1000);
			break;
		case R.id.menu_settings:
			Intent i = new Intent(this, SettingsActivity.class);
			startActivity(i);
			break;
		}
		return true;
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		// Se setea la escucha de nuevos dispositivos
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);

	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		unregisterReceiver(mUsbReceiver);
	}

	@Override
	public void onResume() {
		try {
			super.onResume();
			Log.v(TAG, "onResume()");

			connectSource();
			
			// Restaura el graficador
			// canvasView.onResumeCanvasView();

			graphHandler.removeCallbacks(graphThread);
			graphHandler.postDelayed(graphThread, Integer.parseInt(sharedPrefs
					.getString("prefRefreshRate", "10")));

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void onPause() {
		try {
			super.onPause();
			Log.v(TAG, "onPause()");

			disconnectSource();

			// Detiene graficador
			// canvasView.onPauseCanvasView();

			graphHandler.removeCallbacks(graphThread);

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public void showTriggerPopup() {
		View v = (View) findViewById(R.id.menu_trigger);
		PopupMenu popup = new PopupMenu(this, v);
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.trigger_options, popup.getMenu());
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
				case R.id.trigger_rising:
					triggerRaising = true;
					break;
				case R.id.trigger_falling_edge:
					triggerRaising = false;
					break;
				}
				read_buffer.setTriggerEdge(triggerRaising);
				return true;
			}
		});
		int itemChecked = 0;
		if (!triggerRaising) {
			itemChecked = 1;
		}
		popup.getMenu().getItem(itemChecked).setChecked(true);
		popup.show();
	}

	/**
	 * Muestra el popup con las opciones de fuente
	 * 
	 * @param v
	 *            Vista donde se debe inflar el menu
	 */
	public void showSourcePopup() {
		View v = (View) findViewById(R.id.menu_source);
		PopupMenu popup = new PopupMenu(this, v);
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.signal_sources, popup.getMenu());
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
				case R.id.source_internal:
					selectedClass = SignalGenerator.class;
					frecuency = SignalGenerator.getSampleRateKhz();
					break;
				case R.id.source_audio:
					selectedClass = SoundService.class;
					frecuency = SoundService.getSampleRateKhz();
					break;
				case R.id.source_usb:
					selectedClass = USB_FTDIservice.class;
					frecuency = USB_FTDIservice.getSampleRateKhz();
					break;
				case R.id.source_arduino:
					selectedClass = ArduinoService.class;
					frecuency = ArduinoService.getSampleRateKhz();
					break;
				}
				disconnectSource();
				initBuffer();
				initUSB(selectedClass);
				connectSource();

				// Reiniciar zoom
				externalZoom = ZoomValues.TEN;
				setVerticalZoom(externalZoom.getCode());
				canvasView.setMaxVoltage(externalZoom.getMaxVolgate());

				return true;
			}
		});
		int itemChecked = 0;
		if (selectedClass == SoundService.class) {
			itemChecked = 1;
		} else if (selectedClass == USB_FTDIservice.class) {
			itemChecked = 2;
		} else if (selectedClass == ArduinoService.class) {
			itemChecked = 3;
		}
		popup.getMenu().getItem(itemChecked).setChecked(true);
		popup.show();
	}

	public void showZoomPopup() {
		View v = (View) findViewById(R.id.menu_zoom);
		PopupMenu popup = new PopupMenu(this, v);
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.zoom_options, popup.getMenu());
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				externalZoom = ZoomValues.getZoom(item.getItemId());
				setVerticalZoom(externalZoom.getCode());
				canvasView.setMaxVoltage(externalZoom.getMaxVolgate());
				return true;
			}
		});
		popup.getMenu().getItem(externalZoom.ordinal()).setChecked(true);
		popup.show();
	}

	// ////////////////////////////////////////////////////////
	// Inicializaciones
	// ////////////////////////////////////////////////////////

	/**
	 * Inicia el servicio de USB (no lo dispara)
	 */
	private void initUSB() {
		initUSB(selectedClass);
	}

	/**
	 * Inicia el servicio de USB (no lo dispara)
	 */
	private void initUSB(Class<?> cls) {
		// Crea el USB intent que disparara el servicio
		USBserviceIntent = new Intent(MainActivity.this, cls);

		// Crea un nuevo messenger para la comunicacion de vuelta
		Messenger messengerRead = new Messenger(new UsbReadHandler(this));
		USBserviceIntent.putExtra("MESSENGER", messengerRead);
	}

	/**
	 * Inicia el buffer circular
	 */
	private void initBuffer() {
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		read_buffer = new SignalBuffer(Integer.parseInt(sharedPrefs.getString(
				"prefBufferSize", "100000")));
		samples2display = Integer.parseInt(sharedPrefs.getString(
				"prefInitSamplesToDisplay", "200"));
		canvasView.setSampleRate(frecuency, samples2display);
	}

	/**
	 * Inicia el servicio y lo bindea
	 */
	private void connectSource() {
		startService(USBserviceIntent);
		bindService(USBserviceIntent, serviceConnection,
				Context.BIND_AUTO_CREATE);
	}

	/**
	 * Detiene el servicio y desbindea
	 */
	private void disconnectSource() {
		stopService(USBserviceIntent);
		unbindService(serviceConnection);
	}

	/**
	 * Define los callbacks para el bindeo del servicio
	 */
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder binder) {
			messengerSend = new Messenger(binder);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			messengerSend = null;
		}
	};

	// ////////////////////////////////////////////////////////
	// Funcionalidad de I/O del Servicio
	// ////////////////////////////////////////////////////////

	/**
	 * Handler para la lectura de valores
	 */
	private static class UsbReadHandler extends Handler {
		private WeakReference<MainActivity> reference;

		UsbReadHandler(MainActivity activity) {
			reference = new WeakReference<MainActivity>(activity);
		}

		@Override
		public void handleMessage(Message message) {
			int[] buffer_in = (int[]) message.obj;

			if (message.arg1 == RESULT_OK && buffer_in != null) {
				MainActivity activity = reference.get();
				if (displayFFT && activity != null) {
					activity.getFFT(buffer_in);
				} else {
					activity.read_buffer.store(buffer_in);
				}
			}
		};
	};

	/**
	 * Envio de datos por USB
	 */
	public void sendBytes(USBCommands[] value2send) {
		Message msg = Message.obtain();

		Bundle bundle = new Bundle();
		bundle.putSerializable("rbuf", value2send);
		msg.setData(bundle);

		try {
			messengerSend.send(msg);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	public void setHorizontalZoom(int zoom) {
		samples2display = zoom;
		canvasView.setSampleRate(frecuency, samples2display);
	}

	public void setVerticalZoom(USBCommands zoom) {
		USBCommands[] value2send = new USBCommands[2];
		value2send[0] = USBCommands.CMD_VERTICAL_ZOOM;
		value2send[1] = zoom;

		sendBytes(value2send);
	}

	public void setTriggerLevel(int level) {
		try {
			canvasView.setTriggerLevel(level);
			read_buffer.setTriggerLevel(level);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	// ////////////////////////////////////////////////////////
	// Graficado de la se–al
	// ////////////////////////////////////////////////////////
	/**
	 * Grafica la se–al
	 * 
	 * @param num_samples
	 *            : Numero de muestras en el array
	 * @param signal
	 *            : Array de flotantes conteniendo la se–al (flotantes entre -1
	 *            y 1)
	 */
	private void graphSignal() {
		try {
			// Envia el array al graficador
			if (!displayFFT) {
				canvasView.setSignal(
						read_buffer.read(samples2display, displayDC),
						read_buffer.getDelta());
				canvasView.setPeek2Peek(read_buffer.getPeek2Peek());
				canvasView.draw();
			}
		} catch (Exception e) {
			Log.e(TAG, e.getStackTrace().toString());
		}
	}

	/**
	 * Grafica la se–al
	 * 
	 * @param num_samples
	 *            : Numero de muestras en el array
	 * @param signal
	 *            : Array de flotantes conteniendo la se–al (flotantes entre -1
	 *            y 1)
	 */
	public void graphSignalStatic(int percentageMoved) {
		try {
			// Envia el array al graficador
			if (!playToogle.isPlaying()) {
				canvasView.setSignal(read_buffer.readStatic(samples2display,
						percentageMoved, displayDC), 0);
				canvasView.draw();
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/**
	 * Clase para el manejo de play/pause
	 */
	private class playPauseToggle {
		private boolean isPlay;
		private MenuItem menuItem;

		public playPauseToggle(MenuItem menu, boolean play) {
			menuItem = menu;

			isPlay = play;
			// setStatus(isPlay);
		}

		public void toogle() {
			isPlay = !isPlay;

			setStatus(isPlay);
		}

		private void setStatus(boolean statusPlay) {
			if (statusPlay) {
				setPlay();
			} else {
				setPause();
			}
		}

		public boolean isPlaying() {
			return isPlay;
		}

		private void setPlay() {
			menuItem.setIcon(drawable.ic_media_pause);

			USBCommands[] value2send = { USBCommands.CMD_PLAY };

			sendBytes(value2send);
			canvasView.setPlay(true);

			graphHandler.removeCallbacks(graphThread);
			graphHandler.postDelayed(graphThread, Integer.parseInt(sharedPrefs
					.getString("prefRefreshRate", "10")));
		}

		private void setPause() {
			menuItem.setIcon(drawable.ic_media_play);

			USBCommands[] value2send = { USBCommands.CMD_PAUSE };
			sendBytes(value2send);
			canvasView.setPlay(false);

			graphHandler.removeCallbacks(graphThread);
		}
	}

	private void getFFT(int[] buffer_in) {
		double[] real_input = new double[buffer_in.length];
		for (int i = 0; i < buffer_in.length; i++) {
			real_input[i] = (double) buffer_in[i] - 128;
		}

		DoubleFFT_1D ft = new DoubleFFT_1D(buffer_in.length);
		ft.realForward(real_input);

		List<Integer> salida = new ArrayList<Integer>();
		for (int i = 0; i < real_input.length; i++) {
			salida.add((int) real_input[i] / 50);
		}
		canvasView.setSignal(salida, 0);
		canvasView.draw();
	}

	private void connectArduino(Context context) {
		Toast.makeText(context, "PicScope basado en Arduino conectado",
				Toast.LENGTH_SHORT).show();
		selectedClass = ArduinoService.class;
		frecuency = ArduinoService.getSampleRateKhz();

		initBuffer();
		initUSB(selectedClass);
		// connectSource();

		// Reiniciar zoom
		externalZoom = ZoomValues.TEN;
		setVerticalZoom(externalZoom.getCode());
		canvasView.setMaxVoltage(externalZoom.getMaxVolgate());
	}

	// ////////////////////////////////////////////////////////
	// Control de la conexion de dispositivos USB
	// ////////////////////////////////////////////////////////
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				// Inicia el driver de USB
				// startSampling();
				connectArduino(context);

			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				Toast.makeText(context, "Dispositivo desconectado",
						Toast.LENGTH_SHORT).show();
			}
		}
	};
}
