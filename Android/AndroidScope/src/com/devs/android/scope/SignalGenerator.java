package com.devs.android.scope;

import com.devs.android.scope.usb.USBCommands;

import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

public class SignalGenerator extends SignalSource {

	private boolean active = false;

	// Caracteristicas de la se–al
	private int amplitud = 5;
	private static int scaler = 10;
	private int t;
	int[] senal;

	// Cantidad de muestras a enviar por vez
	private static int numberSamples = 1024;

	private Handler mHandler = new Handler();
	private int refreshRate = 50;

	// ////////////////////////////////////////////////////////
	// Metodos propios del ciclo de vida del Servicio
	// ////////////////////////////////////////////////////////

	/**
	 * Crea el IntentService
	 */
	public SignalGenerator() {
		super("SignalGenerator");
	}

	// ////////////////////////////////////////////////////////
	// Inicializaciones
	// ////////////////////////////////////////////////////////

	/**
	 * Inicia el handler para la generacion de la se–al
	 */
	@Override
	protected void startSampling() {
		final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		amplitud = Integer.parseInt(sharedPrefs.getString("prefGeneratorAmplitude", "5"));
		numberSamples = Integer.parseInt(sharedPrefs.getString("prefGeneratorSamples", "128"));
		refreshRate = Integer.parseInt(sharedPrefs.getString("prefGeneratorRate", "100"));
		
		try {
			active = true;
			t = 0;
			senal = new int[numberSamples];

			mHandler.removeCallbacks(createSignalHandler);
			mHandler.postDelayed(createSignalHandler, refreshRate);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

	}
	
	@Override
	protected void stopSampling() {
		active = false;
		mHandler.removeCallbacks(createSignalHandler);
	}

	/**
	 * Handler para generar la se–al cada cierto intervalo de tiempo
	 */
	private Runnable createSignalHandler = new Runnable() {
		public void run() {
			returnResult(createSignal());

			if (active) {
				mHandler.removeCallbacks(createSignalHandler);
				mHandler.postDelayed(this, refreshRate);
			}
		}
	};

	// sin(2 * pi * f) = sin(w * t)
	// f = 1 / ( 2 * pi * 10) = 1 / 62,83 = 15,91KHz
	// 63 muestras por periodo
	// Frecuencia muestreo Fs = 1MHz (1,002MHz)
	/**
	 * Crea la se–al
	 * 
	 * @return Array de enteros con la se–al generada
	 */
	private int[] createSignal() {
		int i;
		if (active) {
			try {
				for (i = 0; i < numberSamples; i++) {
					t = t > 62 ? 0 : t;
					senal[i] = (int) ((scaler * amplitud * Math.sin(((float) t / 10))) + 128) + 30;
					t++;
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
		return senal;
	}

	public static int getSampleRateKhz() {
		return 1000;
	}

	@Override
	protected void playSignal() {
		active = true;
		mHandler.removeCallbacks(createSignalHandler);
		mHandler.postDelayed(createSignalHandler, refreshRate);
		
	}

	@Override
	protected void pauseSignal() {
		active = false;
		mHandler.removeCallbacks(createSignalHandler);
	}

	@Override
	protected void setZoom(USBCommands zoom) {
		if (zoom == USBCommands.ZOOM_X2) {
			scaler = 2;
		} else if (zoom == USBCommands.ZOOM_X4) {
			scaler = 4;
		} else if (zoom == USBCommands.ZOOM_X5) {
			scaler = 5;
		} else if (zoom == USBCommands.ZOOM_X8) {
			scaler = 8;
		} else if (zoom == USBCommands.ZOOM_X10) {
			scaler = 10;
		} else if (zoom == USBCommands.ZOOM_X16) {
			scaler = 16;
		}else if (zoom == USBCommands.ZOOM_X32) {
			scaler = 32;
		} else {
			scaler = 1;
		}
	}
}
