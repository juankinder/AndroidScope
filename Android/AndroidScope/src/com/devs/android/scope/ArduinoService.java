package com.devs.android.scope;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import com.devs.android.scope.usb.USBCommands;
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

public class ArduinoService extends SignalSource {
	// private static final String ACTION_USB_PERMISSION =
	// "android.scope.USB_PERMISSION";

	private static int SAMPLE_RATE_KHZ = 200;
	private int BAUDRATE = 2000000;

	// Tiempo en milisegundos entre lecturas del buffer
	private static int threadSleep = 10;

	private static D2xxManager ftD2xx = null;
	private FT_Device ftDev;

	static final int READBUF_SIZE = 10240;
	byte[] rbuf = new byte[READBUF_SIZE];
	char[] rchar = new char[READBUF_SIZE];
	int mReadSize = 0;

	USBCommands zoom = USBCommands.ZOOM_X1;
	byte prescaler;

	boolean mThreadIsStopped = true;
	Handler mHandler = new Handler();
	Thread mThread;


	// ////////////////////////////////////////////////////////
	// Inicializaciones
	// ////////////////////////////////////////////////////////

	/**
	 * Inicia el driver y la conexion USB
	 */
	@Override
	protected void startSampling() {
		try {
			ftD2xx = D2xxManager.getInstance(this);

			IntentFilter filter = new IntentFilter();
			filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			registerReceiver(mUsbReceiver, filter);

			openDevice();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void stopSampling() {
		closeDevice();
		unregisterReceiver(mUsbReceiver);
	}
	
	
	private void openDevice() {
		if (ftDev != null) {
			if (ftDev.isOpen()) {
				if (mThreadIsStopped) {
					SetConfig(BAUDRATE, (byte) 8, (byte) 1, (byte) 0, (byte) 0);
					ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
					ftDev.restartInTask();
					ftDev.setLatencyTimer((byte) 0);
					// sendData();
					new Thread(mLoop).start();
				}
				return;
			}
		}

		int devCount = 0;
		devCount = ftD2xx.createDeviceInfoList(this);

		Log.d(TAG, "Device number : " + Integer.toString(devCount));

		D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
		ftD2xx.getDeviceInfoList(devCount, deviceList);

		if (devCount <= 0) {
			return;
		}

		if (ftDev == null) {
			ftDev = ftD2xx.openByIndex(this, 0);
		} else {
			synchronized (ftDev) {
				ftDev = ftD2xx.openByIndex(this, 0);
			}
		}

		if (ftDev.isOpen()) {
			if (mThreadIsStopped) {
				SetConfig(BAUDRATE, (byte) 8, (byte) 1, (byte) 0, (byte) 0);
				ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
				ftDev.restartInTask();
				ftDev.setLatencyTimer((byte) 0);
				// sendData();
				new Thread(mLoop).start();
			}
		}
	}

	private void closeDevice() {
		mThreadIsStopped = true;
		if (ftDev != null) {
			ftDev.close();
		}
	}

	/**
	 * Hilo principal para la lectura de datos del USB (polling)
	 */
	private Runnable mLoop = new Runnable() {
		@Override
		public void run() {
			int readSize;
			mThreadIsStopped = false;
			while (true) {
				if (mThreadIsStopped) {
					break;
				}

				synchronized (ftDev) {
					readSize = ftDev.getQueueStatus();
					if (readSize > 0) {
						mReadSize = readSize;
						if (mReadSize > READBUF_SIZE) {
							mReadSize = READBUF_SIZE;
						}
						ftDev.read(rbuf, mReadSize);

						ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
						ftDev.restartInTask();
						ftDev.setLatencyTimer((byte) 0);

						mHandler.post(new Runnable() {
							@Override
							public void run() {
								returnResult(convert2signal(rbuf));
							}
						});

					} // end of if(readSize>0)
				} // end of synchronized

				try {
					Thread.sleep(threadSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
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

	private void SetConfig(int baud, byte dataBits, byte stopBits, byte parity,
			byte flowControl) {
		if (ftDev.isOpen() == false) {
			Log.e(TAG, "SetConfig: device not open");
			return;
		}

		// configure our port
		// reset to UART mode for 232 devices
		// ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
		ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_FAST_SERIAL);

		ftDev.setBaudRate(baud);

		switch (dataBits) {
		case 7:
			dataBits = D2xxManager.FT_DATA_BITS_7;
			break;
		case 8:
			dataBits = D2xxManager.FT_DATA_BITS_8;
			break;
		default:
			dataBits = D2xxManager.FT_DATA_BITS_8;
			break;
		}

		switch (stopBits) {
		case 1:
			stopBits = D2xxManager.FT_STOP_BITS_1;
			break;
		case 2:
			stopBits = D2xxManager.FT_STOP_BITS_2;
			break;
		default:
			stopBits = D2xxManager.FT_STOP_BITS_1;
			break;
		}

		switch (parity) {
		case 0:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		case 1:
			parity = D2xxManager.FT_PARITY_ODD;
			break;
		case 2:
			parity = D2xxManager.FT_PARITY_EVEN;
			break;
		case 3:
			parity = D2xxManager.FT_PARITY_MARK;
			break;
		case 4:
			parity = D2xxManager.FT_PARITY_SPACE;
			break;
		default:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		}

		ftDev.setDataCharacteristics(dataBits, stopBits, parity);

		short flowCtrlSetting;
		switch (flowControl) {
		case 0:
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		case 1:
			flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
			break;
		case 2:
			flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
			break;
		case 3:
			flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
			break;
		default:
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		}

		// TODO : flow ctrl: XOFF/XOM
		// TODO : flow ctrl: XOFF/XOM
		ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);
	}

	/**
	 * Envio de datos al puerto USB
	 * 
	 * @param rbuf
	 *            : Buffer de bytes con la informacion a enviar
	 */
	private void sendData() {
		while (ftDev == null) {
			// return;
		}

		synchronized (ftDev) {
			if (ftDev.isOpen() == false) {
				Log.e(TAG, "onClickWrite : Device is not open");
				return;
			}
			byte[] sendBuffer = new byte[1];
			sendBuffer[0] = zoom.getCode();

			ftDev.setLatencyTimer((byte) 16);
			ftDev.write(sendBuffer, sendBuffer.length);
			closeDevice();
			openDevice();
		}
	}

	// ////////////////////////////////////////////////////////
	// Control de la conexion de dispositivos USB
	// ////////////////////////////////////////////////////////

	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				// never come here(when attached, go to onNewIntent)
				openDevice();
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				closeDevice();
			}
		}
	};

	public static int getSampleRateKhz() {
		return SAMPLE_RATE_KHZ;
	}

	@Override
	protected void playSignal() {
		openDevice();
		sendData();
	}

	@Override
	protected void pauseSignal() {
		closeDevice();
		sendData();
	}

	@Override
	protected void setZoom(USBCommands zoom) {
		this.zoom = zoom;
		sendData();
	}
}
