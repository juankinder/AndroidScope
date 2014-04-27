package com.devs.android.scope;

import com.devs.android.scope.usb.USBCommands;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class SoundService extends SignalSource {

	private static final int RECORDER_SAMPLERATE = 8000;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	private AudioRecord recorder = null;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private int scaler = 10;

	int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we
									// use only 1024
	int BytesPerElement = 2; // 2 bytes in 16bit format

	@Override
	protected void startSampling() {
		
		int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
		recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
				RECORDER_SAMPLERATE, RECORDER_CHANNELS,
				RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

		if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
			recorder.startRecording();
			isRecording = true;
			recordingThread = new Thread(new Runnable() {
				public void run() {
					readAudioFromMic();
				}
			}, "AudioRecorder Thread");
			recordingThread.start();
		}

	}

	@Override
	protected void stopSampling() {
		if (null != recorder) {
			isRecording = false;
			recorder.stop();
			recorder.release();
			recorder = null;
			recordingThread = null;
		}
	}

	private void readAudioFromMic() {
		short sData[] = new short[BufferElements2Rec];

		while (isRecording) {
			recorder.read(sData, 0, BufferElements2Rec);
			returnResult(short2int(sData));
		}
	}

	/**
	 * Convierte un array de short a int.
	 * 
	 * @param sData
	 * @return
	 */
	private int[] short2int(short[] sData) {
		int shortArrsize = sData.length;
		int[] bytes = new int[shortArrsize];
		for (int i = 0; i < shortArrsize; i++) {
			bytes[i] = scaler * (int) sData[i] / 500 + 128;
		}
		return bytes;
	}

	public static int getSampleRateKhz() {
		return RECORDER_SAMPLERATE;
	}

	@Override
	protected void playSignal() {
		startSampling();
	}

	@Override
	protected void pauseSignal() {
		stopSampling();
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
		} else if (zoom == USBCommands.ZOOM_X32) {
			scaler = 32;
		} else {
			scaler = 1;
		}
	}
}
