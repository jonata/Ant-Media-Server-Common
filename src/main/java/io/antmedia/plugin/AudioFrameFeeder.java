package io.antmedia.plugin;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avutil.AVFrame;

import io.antmedia.adaptive.base.AudioEncoder;
import io.antmedia.plugin.api.IFrameListener;

public class AudioFrameFeeder extends AudioEncoder {

	private ArrayList<IFrameListener> listeners = new ArrayList<>();

	public AudioFrameFeeder(int bitrate, String streamId) {
		super(bitrate, streamId);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void prepareCodecLocal(int sampleRate, int channelLayout, int streamIndex) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean encode(AVFrame audioFrame, int streamIndex, long timestampMS) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	public void addListener(IFrameListener listener) {
		listeners .add(listener);
	}

	


}
