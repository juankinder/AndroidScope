package com.devs.android.scope;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class SignalBuffer {

	// ////////////////////////////////////////////////////////
	// Variables globales de la aplicacion
	// ////////////////////////////////////////////////////////
	private final String TAG = MainActivity.TAG;
	private static final int ZERO = 128;
	private final int stepScaler = 500;

	// Lista enlazada donde se almacenan todas las muestras
	private Buffer buffer;
	// Cantidad de datos almacenados por vez
	private int writeSize;
	// Nivel de continua
	private int dcValue = 0;

	// Nivel de disparo
	private int triggerValue;
	private boolean triggerRaising = true;
	private float delta = 0;

	// Buffer de lectura
	private List<Integer> readData;

	// Puntero que marca la posicion donde se debe almacenar el dato en el
	// buffer de lectura
	private int readDataPointer;
	// Incremento para realizar el downsizing del buffer de lectura
	private int readStep;
	
	private int maxVoltage;
	private int minVoltage;

	// Valores usados internamente
	private int temp_value;
	private int top;
	private int triggerPointer;

	/**
	 * Crea un buffer circular
	 * 
	 * @param size
	 *            : Numero de muestras en el buffer
	 */
	public SignalBuffer(Integer size) {
		// storedData = new LinkedList<Integer>();
		// storedData.clear();
		// storedDataSize = size;

		triggerValue = ZERO;
		triggerRaising = true;
		readData = new ArrayList<Integer>();

		buffer = new Buffer(size);
	}

	/**
	 * Almacena las muestras en el buffer circular
	 * 
	 * @param values
	 *            : Array de muestras a almacenar en el buffer circular
	 */
	public void store(int[] values) {
		int sumatoria = 0;
		writeSize = values.length;
		maxVoltage = 0;
		minVoltage = 255;
		
		for (int value : values) {
			sumatoria += value;

			buffer.add(value);
		}

		dcValue = (sumatoria / writeSize) - ZERO;
	}

	/**
	 * Establece el valor al cual se debe disparar el trigger
	 * 
	 * @param trigger_level
	 *            : Valor del trigger (flotante entre -1 y 1)
	 */
	public void setTriggerLevel(int trigger_level) throws Exception {
		if (trigger_level < 0 || trigger_level > 255)
			throw new Exception(
					"El nivel de trigger tiene que estar entre 0 y 255");

		triggerValue = trigger_level;
	}

	public void setTriggerEdge(boolean isRaisingEdge) {
		triggerRaising = isRaisingEdge;
	}

	/**
	 * Lee cierta cantidad de muestras desde el buffer circular
	 * 
	 * @param read_size
	 *            : Cantidad de muestras a leer
	 */
	public List<Integer> read(int read_size, boolean withDC) {
		int readInit;
		try {
			if (read_size >= buffer.size()) {
				read_size = buffer.size() - 1;
			}

			readInit = findTriggerPointer(read_size, withDC);
			delta = findDelta(readInit);
			// readInit = 200;
			// delta = 0;

			if (readInit > 0) {
				// Crea el buffer que se va a devolver
				// haciendo un downsizing del tama–o
				readStep = read_size / stepScaler;
				if (readStep == 0) {
					readStep = 1;
				}
				readData.clear();

				// Reinicia el puntero del buffer de salida
				readDataPointer = 0;
				static_read_pointer = readInit;

				// Mientras no se haya alcanzado el final del buffer
				// y no se haya llenado el buffer de lectura
				while ((readInit >= 0)
						&& (readDataPointer < (read_size / readStep))) {
					temp_value = buffer.get(readInit);
					temp_value -= withDC ? 0 : dcValue;
					readData.add(temp_value);
					
					if (maxVoltage < temp_value) {
						maxVoltage = temp_value;
					}
					if (minVoltage > temp_value) {
						minVoltage = temp_value;
					}

					readDataPointer++;
					readInit -= readStep;
					// storedDataPointer--;
				}
			}
		} catch (Exception e) {
			Log.e("Scope", e.getMessage());
		}

		return readData;
	}

	private int static_read_pointer;

	public List<Integer> readStatic(int readSize, int percentageMoved,
			boolean withDC) throws Exception {

		if (percentageMoved < -100 || percentageMoved > 100)
			throw new Exception(
					"percentageMoved debe ser un valor entre -100 y 100");

		int readPointer = 0;

		// Crea el buffer que se va a devolver
		// haciendo un downsizing del tama–o
		readStep = readSize / stepScaler;
		if (readStep == 0) {
			readStep = 1;
		}
		readData.clear();

		try {
			static_read_pointer = static_read_pointer
					- (percentageMoved * readSize / 100);

			// Limita el puntero para que no vaya por debajo de 0
			if (static_read_pointer < 0) {
				static_read_pointer = 0;
			}

			// Limita el puntero para que no vaya por encima del tama–o del
			// buffer
			if (static_read_pointer >= buffer.size()) {
				static_read_pointer = buffer.size() - 1;
			}

			int temp_pointer = static_read_pointer;
			while ((temp_pointer > 0) && (readPointer < readSize / readStep)) {

				int temp_value = buffer.get(temp_pointer);
				temp_value -= withDC ? 0 : dcValue;
				readData.add(temp_value);
				readPointer++;
				temp_pointer = static_read_pointer - (readPointer * readStep);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error " + e.getMessage());
		}
		return readData;
	}

	private int findTriggerPointer(int initialPosition, boolean withDC) {
		// Valor maximo hasta donde trata de encontrar el punto de disparo
		top = 2 * initialPosition;

		// Puntero que indica donde se produce el disparo
		triggerPointer = initialPosition;
		int dc = withDC ? 0 : dcValue;
		if (triggerRaising) {
			while (triggerPointer <= top
					&& triggerPointer < buffer.size() - 1
					&& !((buffer.get(triggerPointer) - dc >= triggerValue) && (buffer
							.get(triggerPointer + 1) - dc < triggerValue))) {
				triggerPointer++;
			}
		} else {
			while (triggerPointer <= top
					&& triggerPointer < buffer.size() - 1
					&& !((buffer.get(triggerPointer) - dc <= triggerValue) && (buffer
							.get(triggerPointer + 1) - dc > triggerValue))) {
				triggerPointer++;
			}
		}

		if (triggerPointer == top) {
			triggerPointer = initialPosition;
		}
		return triggerPointer;
	}

	private float findDelta(int initPointer) {
		float delta = 0;
		if (initPointer > 1) {
			float previousValue = (float) buffer.get(initPointer + 1);
			float nextValue = (float) buffer.get(initPointer);
			if (nextValue != previousValue) {
				delta = (triggerValue - previousValue)
						/ (nextValue - previousValue);
			}
		}
		return (delta);
	}

	public float getDelta() {
		return delta;
	}
	
	public int getPeek2Peek() {
		return maxVoltage - minVoltage;
	}

	/**
	 * Implementacion propia del buffer para ahorro de memoria.
	 */
	private class Buffer {
		private int[] internalBuffer;
		private int size;
		private int savePointer;

		public Buffer(int size) {
			this.size = size;
			internalBuffer = new int[size];
			savePointer = 0;
		}

		public void add(int value) {
			internalBuffer[savePointer++] = value;
			if (savePointer == size) {
				savePointer = 0;
			}
		}

		public int get(int position) {
			int temp = savePointer - position;
			if (temp < 0) {
				temp = size + temp;
			}

			return internalBuffer[temp];
		}

		public int size() {
			return size;
		}
	}
	
//	private final int N = 22;
//	private final double[] h = { 0.060877251993174335, -0.022916348133741646,
//			0.027690004407500798, -0.033609307764294785, 0.04116664744986654,
//			-0.05115999493087776, 0.06517459194640197, -0.08657692586028458,
//			0.12412296053698456, -0.21033449999687426, 0.6359874971894582,
//			0.6359874971894582, -0.21033449999687426, 0.12412296053698456,
//			-0.08657692586028458, 0.06517459194640197, -0.05115999493087776,
//			0.04116664744986654, -0.033609307764294785, 0.027690004407500798,
//			-0.022916348133741646, 0.060877251993174335, };
//
//	private int n = 0;
//	private double[] x = new double[N];
//
//	public int filter(int x_in) {
//		double y = 0.0;
//
//		// Store the current input, overwriting the oldest input
//		x[n] = x_in;
//
//		// Multiply the filter coefficients by the previous inputs and sum
//		for (int i = 0; i < N; i++) {
//			y += h[i] * x[((N - i) + n) % N];
//		}
//
//		// Increment the input buffer index to the next location
//		n = (n + 1) % N;
//
//		return (int) y;
//	}
}
