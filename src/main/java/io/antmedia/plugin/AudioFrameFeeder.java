package io.antmedia.plugin;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avutil.AVFrame;

import io.antmedia.adaptive.base.AudioEncoder;
import io.antmedia.plugin.api.IFrameListener;

public class AudioFrameFeeder extends AudioEncoder {

	private ArrayList<IFrameListener> listeners = new ArrayList<>();

	public AudioFrameFeeder(int bitrate, String streamId) {
		super(bitrate, streamId);
	}

	@Override
	protected void prepareCodecLocal(int sampleRate, int channelLayout, int streamIndex) throws Exception {
		System.out.println("AudioFrameFeeder.prepareCodecLocal() for "+streamId);
		running.set(true);
	}

	@Override
	public boolean encode(AVFrame audioFrame, int streamIndex, long timestampMS) throws Exception {
		System.out.println("AudioFrameFeeder.encode() for "+streamId);
		return false;
	}
	
	@Override
	public boolean writeFrame(AVFrame frame, int streamIndex, long timestampMS) throws Exception {
		for (IFrameListener iFrameListener : listeners) {
			iFrameListener.onAudioFrame(streamId, frame);
		}
		return false;
	}

	public void addListener(IFrameListener listener) {
		listeners .add(listener);
	}

	


}
