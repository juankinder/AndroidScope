package com.devs.android.scope;

import java.lang.ref.WeakReference;

import com.devs.android.scope.usb.USBCommands;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

public abstract class SignalSource extends IntentService{
	
	// Variables globales del servicio
	protected final String TAG = MainActivity.TAG;
		
	protected int result = Activity.RESULT_OK;
	
	// Messengers de entrada / salida
	protected Messenger messengerOut;
	protected Messenger messengerIn = new Messenger(new IncomingHandler(this));
	
	public SignalSource(String name) {
		super(name);
	}
	
	public SignalSource() {
		super("SignalSource");
	}
	
	@Override
	public void onHandleIntent(Intent arg0) {
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras != null) {
			messengerOut = (Messenger) extras.get("MESSENGER");
		}
		startSampling();
		return messengerIn.getBinder();
	}
	
	protected abstract void startSampling();
	
	protected abstract void stopSampling();
	
	public static int getSampleRateKhz() {
		return 0;
	}
	
	
	@Override
	public void onDestroy() {
		try {
			super.onDestroy();
			stopSampling();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onLowMemory() {
		try {
			super.onLowMemory();
			Toast.makeText(this, "Memoria baja", Toast.LENGTH_SHORT).show();
			stopSampling();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}
	
	
	
	// ////////////////////////////////////////////////////////
	// Funcionalidad de ENVIO de datos al generador
	// ////////////////////////////////////////////////////////
	/**
	 * Handler para el envio de datos al generador
	 */
	protected static class IncomingHandler extends Handler {
		private WeakReference<SignalSource> reference;

		public IncomingHandler(SignalSource signalSource) {
			reference = new WeakReference<SignalSource>(signalSource);
		}

		@Override
		public void handleMessage(Message msg) {
			try {
				Bundle data = msg.getData();
				USBCommands[] commands = (USBCommands[]) data.get("rbuf");
				
				if (commands[0] == USBCommands.CMD_PAUSE) {
					reference.get().pauseSignal();
				} else if (commands[0] == USBCommands.CMD_PLAY) {
					reference.get().playSignal();
				} else if (commands[0] == USBCommands.CMD_VERTICAL_ZOOM) {
					reference.get().setZoom(commands[1]);
//				} else if (commands[0] == USBCommands.CMD_SAMPLES) {
//					numberSamples = (int) commands[1].getCode();
				}
			} catch (Exception e1) {
				Log.w(getClass().getName(), "Excepci—n enviando mensaje", e1);
			}
		}
	}
	
	protected abstract void playSignal();
	
	protected abstract void pauseSignal();
	
	protected abstract void setZoom(USBCommands zoom);
	
	
	// ////////////////////////////////////////////////////////
	// Retorno de datos generados
	// ////////////////////////////////////////////////////////
	/**
	 * Devuelve el valor leido utilizando el messengerOut
	 * 
	 * @param buffer_in
	 *            : Buffer de floats con la informacion recibida
	 */
	protected void returnResult(int[] buffer_in) {
		Message msg = Message.obtain();

		msg.arg1 = result;
		msg.arg2 = buffer_in.length;
		msg.obj = buffer_in;

		try {
			messengerOut.send(msg);
		} catch (android.os.RemoteException e1) {
			Log.w(getClass().getName(), "Exception sending message", e1);
		}
	}
}
