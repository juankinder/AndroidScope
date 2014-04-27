package com.devs.android.scope.usb;

import com.devs.android.scope.MainActivity;

import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

class UsbId {
	int mVid;
	int mPid;
	int mBcdDevice;

	public static final int VENDOR_FTDI = 0x0403;
    public static final int FTDI_FT232R = 0x6001;
    
    
	UsbId(int vid, int pid, int bcdDevice) {
		mVid = vid;
		mPid = pid;
		mBcdDevice = bcdDevice;
	}
}

public class USBdriver {

	private static final UsbId[] IDS = { new UsbId(0x0000, 0x0000, 0) };

	private UsbId mSelectedDeviceInfo;

	public static final int BAUD300 = 300;
	public static final int BAUD600 = 600;
	public static final int BAUD1200 = 1200;
	public static final int BAUD2400 = 2400;
	public static final int BAUD4800 = 4800;
	public static final int BAUD9600 = 9600;
	public static final int BAUD14400 = 14400;
	public static final int BAUD19200 = 19200;
	public static final int BAUD38400 = 38400;
	public static final int BAUD57600 = 57600;
	public static final int BAUD115200 = 115200;
	public static final int BAUD230400 = 230400;
	public static final int BAUD_ULTRA_FAST = 1000000;
	
	public static final int READ_TIMEOUT = 100;

	public static final int FTDI_MAX_INTERFACE_NUM = 4;

	private static final String TAG = MainActivity.TAG;

	private UsbManager mManager;
	private UsbDevice mDevice;
	private UsbDeviceConnection mDeviceConnection;
	private UsbInterface[] mInterface = new UsbInterface[FTDI_MAX_INTERFACE_NUM];

	private UsbEndpoint mFTDIEndpointIN;
	private UsbEndpoint mFTDIEndpointOUT;

	public static final int WRITEBUF_SIZE = 10;
	
	public USBdriver(UsbManager manager) {
		mManager = manager;
	}

	/** Trata de iniciar el dispositivo CDC **/
	public boolean begin(int baudrate) {
		boolean result = false;

		for (UsbDevice device : mManager.getDeviceList().values()) {
			getPermission(device);
			if (!mManager.hasPermission(device)) {
				return false;
			}

			if (getUsbInterfaces(device)) {
				break;
			}
		}
		mFTDIEndpointIN = null;
		mFTDIEndpointOUT = null;
		
		if ((mSelectedDeviceInfo != null) && (mDevice != null) && getCdcEndpoint()) {
			result = initCdcAcm(mDeviceConnection, baudrate);
		}
		return result;
	}

	/** Cierra el dispositivo **/
	public void end() {
		if (mSelectedDeviceInfo != null) {
			if (mDeviceConnection != null) {
				if (mInterface[0] != null) {
					mDeviceConnection.releaseInterface(mInterface[0]);
					mInterface[0] = null;
				}
				if (mInterface[1] != null) {
					mDeviceConnection.releaseInterface(mInterface[1]);
					mInterface[1] = null;
				}
				mDeviceConnection.close();
			}
			mDevice = null;
			mDeviceConnection = null;
		}
	}

	/**
	 * Lee datos desde el puerto utilizando transferencias bulk.
	 * 
	 * @param buf
	 *            Buffer donde se almacenaran los datos leidos.
	 * @return Cantidad de datos transferidos. Si retorna un numero negativo es porque hubo un error en la lectura.
	 */
	public int read(byte[] buf) {
		int len = 0;

		if (mDeviceConnection != null) {
			try {
				len = mDeviceConnection.bulkTransfer(mFTDIEndpointIN, buf, buf.length, READ_TIMEOUT); // RX
			} catch (Exception e) {
				Log.e(getClass().getName(), "USBDriver: Error reading:", e);
			}
		}
		return len;
	}

	/**
	 * Escribe en el puerto 1 byte de datos binarios.
	 * 
	 * @param buf
	 *            : write buffer
	 * @return written length
	 */
	public int write(byte[] buf) {
		return write(buf, buf.length);
	}

	/**
	 * Escribe en el puerto n bytes de datos binarios.
	 * 
	 * @param buf
	 *            : Buffer con datos a escribir
	 * @param length
	 *            : Longitud del buffer.
	 * @return Longitud de datos escritos.
	 */
	public int write(byte[] buf, int length) {

		int offset = 0;
		int actual_length;
		byte[] write_buf = new byte[WRITEBUF_SIZE];

		while (offset < length) {
			int write_size = WRITEBUF_SIZE;

			if (offset + write_size > length) {
				write_size = length - offset;
			}
			System.arraycopy(buf, offset, write_buf, 0, write_size);

			actual_length = mDeviceConnection.bulkTransfer(mFTDIEndpointOUT, write_buf, write_size, 0);

			if (actual_length < 0) {
				return -1;
			}
			offset += actual_length;
		}

		return offset;
	}

	public boolean isConnected() {
		if (mDevice != null && mFTDIEndpointIN != null && mFTDIEndpointOUT != null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Setea la velocidad de comunicacion
	 * 
	 * @param baudrate
	 * @return Verdadero si la velocidad pudo setearse correctamente.
	 */
	public boolean setBaudrate(int baudrate) {
		boolean result = false;
		if (mDeviceConnection != null) {
			byte[] baudByte = new byte[4];
			baudByte[0] = (byte) (baudrate & 0x000000FF);
			baudByte[1] = (byte) ((baudrate & 0x0000FF00) >> 8);
			baudByte[2] = (byte) ((baudrate & 0x00FF0000) >> 16);
			baudByte[3] = (byte) ((baudrate & 0xFF000000) >> 24);
			result = mDeviceConnection.controlTransfer(0x21, 0x20, 0, 0, new byte[] { baudByte[0], baudByte[1], baudByte[2], baudByte[3], 0x00, 0x00, 0x08 }, 7, 0) >= 0;
		}
		return result;
	}

	// FIXME ISSUE: Cuando se reconecta el USB no se puede reactivar CDC.
	private boolean initCdcAcm(UsbDeviceConnection conn, int baudrate) {
		boolean result = false;
		if (conn.claimInterface(mInterface[0], true)) {
			// Si result es mayor o igual a cero no hubo problemas
			result = (conn.controlTransfer(0x21, 0x22, 0x00, 0, null, 0, 0) >= 0);
			//result &= setBaudrate(baudrate);
		}
		return result;
	}

	private boolean getCdcEndpoint() {
		UsbEndpoint ep;

		if (mInterface[0] == null) {
			return false;
		}
		for (int i = 0; i < 2; ++i) {
			ep = mInterface[0].getEndpoint(i);
			if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
				mFTDIEndpointIN = ep;
			} else {
				mFTDIEndpointOUT = ep;
			}
		}
		if (mFTDIEndpointIN == null || mFTDIEndpointOUT == null) {
			return false;
		}
		return true;

	}

	/** 
	 * Setea la interfaz USB
	 * @param device
	 * @param intf
	 * @param intfNum
	 * @return
	 */
	private boolean setUSBInterface(UsbDevice device, UsbInterface intf, int intfNum) {
		if (mDeviceConnection != null) {
			if (mInterface[intfNum] != null) {
				mDeviceConnection.releaseInterface(mInterface[intfNum]);
				mInterface[intfNum] = null;
			}
			mDeviceConnection.close();
			mDevice = null;
			mDeviceConnection = null;
		}

		if (device != null && intf != null) {
			UsbDeviceConnection connection = mManager.openDevice(device);
			if (connection != null) {
				Log.d(TAG, "open succeeded");
				for (UsbId usbids : IDS) {
					if ((usbids.mVid == 0 && usbids.mPid == 0 && device.getDeviceClass() == UsbConstants.USB_CLASS_COMM) // CDC
							|| (device.getVendorId() == usbids.mVid && device.getProductId() == usbids.mPid)) {
						Log.d(TAG, "Vendor ID : " + device.getVendorId());
						Log.d(TAG, "Product ID : " + device.getProductId());
						mDevice = device;
						mDeviceConnection = connection;
						mInterface[intfNum] = intf;
						return true;
					}
				}
			} else {
				Log.d(TAG, "open failed");
			}
		}

		return false;
	}

	private boolean getUsbInterfaces(UsbDevice device) {
		UsbInterface[] intf = new UsbInterface[FTDI_MAX_INTERFACE_NUM];
		for (UsbId usbids : IDS) {

			if (usbids.mVid == 0 && usbids.mPid == 0 && device.getDeviceClass() == UsbConstants.USB_CLASS_COMM) {
				for (int i = 0; i < device.getInterfaceCount(); ++i) {
					if (device.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
						intf[0] = device.getInterface(i);
					}
				}
				if (intf[0] == null) {
					return false;
				}
			} else {
				intf = findUSBInterfaceByVIDPID(device, usbids.mVid, usbids.mPid);
			}
			if (intf[0] != null) {
				for (int i = 0; i < 1; ++i) {
					setUSBInterface(device, intf[i], i);
					mSelectedDeviceInfo = usbids;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Busca por una interfaz en el dispositivo USB por VID y PID
	 */
	private UsbInterface[] findUSBInterfaceByVIDPID(UsbDevice device, int vid, int pid) {
		UsbInterface[] retIntf = new UsbInterface[FTDI_MAX_INTERFACE_NUM];
		int j = 0;
		int count = device.getInterfaceCount();
		for (int i = 0; i < count; i++) {
			UsbInterface intf = device.getInterface(i);
			if (device.getVendorId() == vid && device.getProductId() == pid) {
				retIntf[j] = intf;
				++j;
			}
		}
		return retIntf;
	}

	// get a device descriptor : bcdDevice
	// need Android API Level 13
	/*
	 * private int getDescriptorBcdDevice() { byte[] rowDesc =
	 * mDeviceConnection.getRawDescriptors(); return rowDesc[13] << 8 +
	 * rowDesc[12]; }
	 */
	private PendingIntent mPermissionIntent;

	/**
	 * Sets PendingIntent for requestPermission
	 * 
	 * @param pi
	 * @see getPermission
	 */
	public void setPermissionIntent(PendingIntent pi) {
		mPermissionIntent = pi;
	}

	/**
	 * Trata de obtener los permisos en caso de no tenerlos
	 * 
	 * @param device
	 * @see setPermissionIntent
	 */
	public void getPermission(UsbDevice device) {
		if (device != null && mPermissionIntent != null) {
			if (!mManager.hasPermission(device)) {
				mManager.requestPermission(device, mPermissionIntent);
			}
		}
	}

	/** Cuando se conecta un dispositivo USB **/
	public boolean usbAttached(Intent intent) {
		UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		return getUsbInterfaces(device);
	}

	/** Cuando se desconecta un dispositivo USB **/
	public void usbDetached(Intent intent) {
		UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		String deviceName = device.getDeviceName();
		if (mDevice != null && mDevice.equals(deviceName)) {
			setUSBInterface(null, null, 0);
			end();
		}
	}
}
