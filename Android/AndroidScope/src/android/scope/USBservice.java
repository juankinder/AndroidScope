package android.scope;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.scope.usb.USBCommands;
import android.scope.usb.USBdriver;
import android.util.Log;

public class USBservice extends SignalSource {

	// Configuracion de la velocidad de conexion
	private static final int mBaudrate = USBdriver.BAUD115200;
	
	// Tiempo en milisegundos entre lecturas del buffer
	private static int threadSleep = 10;
	
	// Cantidad de bytes a leer
	private static int bytes2read = 64;
	
	// Variables globales del servicio
	private static final String ACTION_USB_PERMISSION = "android.scope.USB_PERMISSION";

	// Driver de USB
	private USBdriver mSerial;

	// Bandera para determinar si esta corriendo en el bucle principal
	private boolean mRunningMainLoop = false;

	// Bandera para determinar si se debe detener
	private boolean mStop = false;

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
			mSerial = new USBdriver((UsbManager) getSystemService(Context.USB_SERVICE));

			// Se setea la escucha de nuevos dispositivos
			IntentFilter filter = new IntentFilter();
			filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			registerReceiver(mUsbReceiver, filter);

			// Se solicitan los permisos antes de iniciar
			PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
			mSerial.setPermissionIntent(permissionIntent);

			// Se dispara el driver para que envie y reciba datos
			if (mSerial.begin(mBaudrate)) {
				mainloop();
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
		unregisterReceiver(mUsbReceiver);
		mSerial.end();
	}

	// ////////////////////////////////////////////////////////
	// Funcionalidad de LECTURA de datos al puerto USB
	// ////////////////////////////////////////////////////////
	/**
	 * Loop principal para la lectura de datos del USB (polling)
	 */
	private void mainloop() {
		try {
			mStop = false;
			mRunningMainLoop = true;

			new Thread(mLoop).start();

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/**
	 * Hilo principal para la lectura de datos del USB (polling)
	 */
	private Runnable mLoop = new Runnable() {
		@Override
		public void run() {
			int len;
			byte[] rbuf = new byte[bytes2read];
			byte[] tempbuffer;

			for (;;) {
				len = mSerial.read(rbuf) - 1; // Lectura de los datos desde el puerto USB

				if (len > 0) {
					tempbuffer = new byte[len];
					for (int i = 0; i < len; i++) {
						tempbuffer[i] = rbuf[i];
					}

					// Devolver los valores, convirtiendolos a una se–al valida
					returnResult(convert2signal(tempbuffer));
				}
				try {
					Thread.sleep(threadSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (mStop) {
					mRunningMainLoop = false;
					return;
				}
			}
		}
	};

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
		if ((mSerial != null) && mSerial.isConnected()) {
			mSerial.write(rbuf);
		}
	}

	// ////////////////////////////////////////////////////////
	// Control de la conexion de dispositivos USB
	// ////////////////////////////////////////////////////////
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) { // Conexion de un dispositivo
				if (!mSerial.isConnected()) {
					mSerial.begin(mBaudrate);
				}
				if (!mRunningMainLoop) {
					mainloop();
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) { // Desconexion de un dispositivo
				mStop = true;
				mSerial.usbDetached(intent);
				mSerial.end();

			} else if (ACTION_USB_PERMISSION.equals(action)) { // Solicitud de permisos
				synchronized (this) {
					if (!mSerial.isConnected()) {
						mSerial.begin(mBaudrate);
					}
				}
				if (!mRunningMainLoop) {
					mainloop();
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
