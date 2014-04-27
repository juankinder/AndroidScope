package com.devs.android.scope.usb;

public enum USBCommands {
	CMD_SAMPLES((byte) (0xA0)), 		// Cantidad de muestras		// No se usa
	CMD_TIMER((byte) (0xC0)), 			// Valor del Timer0			// No se usa
	CMD_PAUSE((byte) (0xD0)), 			// Pausa de muestreo
	CMD_PLAY((byte) (0xD1)), 			// Play de muestreo
	CMD_VERTICAL_ZOOM((byte) (0xD2)), 	// Valor de amplificacion OA
	
	// Valores de Zoom
	ZOOM_X1((byte) (0x01)),
	ZOOM_X2((byte) (0x02)),
	ZOOM_X4((byte) (0x04)),
	ZOOM_X5((byte) (0x05)),
	ZOOM_X8((byte) (0x08)),
	ZOOM_X10((byte) (0x10)),
	ZOOM_X16((byte) (0x16)),
	ZOOM_X32((byte) (0x32));

//	private final static Byte CMD_TIMER_DEFAULT_HIGH = (byte) (0xFF);
//	private final static Byte CMD_TIMER_DEFAULT_LOW = (byte) (0xFB);

	private byte code;

	USBCommands(byte codeHex) {
		code = codeHex;
	}

	public byte getCode() {
		return code;
	}

}
