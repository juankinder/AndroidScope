package com.devs.android.scope;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.devs.android.scope.ftdi.SerialInputOutputManager;
import com.devs.android.scope.ftdi.UsbSerialDriver;
import com.devs.android.scope.ftdi.UsbSerialProber;
import com.devs.android.scope.usb.USBCommands;
import com.devs.android.scope.usb.USBdriver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class USB_FTDIservice extends SignalSource {

	// Variables globales del servicio
	private static final String ACTION_USB_PERMISSION = "android.scope.USB_PERMISSION";

	// Driver de USB
	private UsbSerialDriver mSerial;

	// Configuracion de la velocidad de conexion
	private static final int mBaudrate = USBdriver.BAUD115200;
	private static final int timeOut = 1000;

	private final ExecutorService mExecutor = Executors
			.newSingleThreadExecutor();
	private SerialInputOutputManager mSerialIoManager;

	// Listener para cuando se leen nuevos datos
	private final SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {

		@Override
		public void onRunError(Exception e) {
			Log.d(TAG, "Runner stopped.");
		}

		@Override
		public void onNewData(final byte[] data) {
			returnResult(convert2signal(data));
		}
	};

	// ////////////////////////////////////////////////////////
	// Inicializaciones
	// ////////////////////////////////////////////////////////
	/**
	 * Inicia el driver y la conexion USB
	 */
	@Override
	protected void startSampling() {
		try {
			// Obtiene el driver
			UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

			// Find the first available driver.
			mSerial = UsbSerialProber.findFirstDevice(manager);

			if (mSerial != null) {
				Log.i(TAG, "Starting io manager ..");
				mSerialIoManager = new SerialInputOutputManager(mSerial,
						mListener);
				mExecutor.submit(mSerialIoManager);
			}

			// Se setea la escucha de nuevos dispositivos
			IntentFilter filter = new IntentFilter();
			filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			registerReceiver(mUsbReceiver, filter);

			// Se dispara el driver para que envie y reciba datos
			if (mSerial != null) {
				try {
					mSerial.open();
					mSerial.setParameters(mBaudrate, 8,
							UsbSerialDriver.STOPBITS_1,
							UsbSerialDriver.PARITY_NONE);
				} catch (IOException e) {
					Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
					try {
						mSerial.close();
					} catch (IOException e2) {
						// Ignore.
					}
					mSerial = null;
					return;
				}
			}

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/**
	 * Detiene el driver y la conexion USB
	 */
	@Override
	protected void stopSampling() {
		try {
			unregisterReceiver(mUsbReceiver);
			if (mSerialIoManager != null) {
				Log.i(TAG, "Stopping io manager ..");
				mSerialIoManager.stop();
				mSerialIoManager = null;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Convierte un buffer de bytes a uno de floats entre -1.0 y 1.0
	 * 
	 * @param buffer_in
	 *            : Buffer de bytes con la se–al a convertir
	 * @return senal : Buffer de floats con la se–al convertida
	 */
	private int[] convert2signal(byte[] buffer_in) {

		int i;
		int[] senal = new int[buffer_in.length];

		try {
			for (i = 0; i < buffer_in.length; i++) {
				senal[i] = byteToUnsignedInt(buffer_in[i]);
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

		return senal;
	}

	/**
	 * Convierte un byte a un entero sin signo
	 * 
	 * @param b
	 *            : Byte a convertir
	 * @return c : Entero equivalente
	 */
	private int byteToUnsignedInt(byte b) {
		int c;
		c = 0x00 << 24 | b & 0xff;
		return c;
	}

	/**
	 * Envio de datos al puerto USB
	 * 
	 * @param rbuf
	 *            : Buffer de bytes con la informacion a enviar
	 */
	public void sendData(byte[] rbuf) {
		if (mSerial != null) {
			try {
				// mSerial.setDTR(true);
				// mSerial.setDTR(false);
				mSerial.write(rbuf, timeOut);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// ////////////////////////////////////////////////////////
	// Control de la conexion de dispositivos USB
	// ////////////////////////////////////////////////////////
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				// Inicia el driver de USB
				startSampling();
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				try {
					mSerial.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			} else if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					startSampling();
				}

			}
		}
	};

	@Override
	protected void playSignal() {
		byte[] array2send = { USBCommands.CMD_PLAY.getCode() };
		sendData(array2send);
	}

	@Override
	protected void pauseSignal() {
		byte[] array2send = { USBCommands.CMD_PAUSE.getCode() };
		sendData(array2send);
	}

	@Override
	protected void setZoom(USBCommands zoom) {
		byte[] array2send = { USBCommands.CMD_VERTICAL_ZOOM.getCode(),
				zoom.getCode() };
		sendData(array2send);
	}
}
