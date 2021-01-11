package io.antmedia.plugin;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;

import io.antmedia.adaptive.base.Encoder;
import io.antmedia.adaptive.base.VideoEncoder;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.webrtc.VideoCodec;

public class VideoFrameFeeder extends VideoEncoder {
	
	private ArrayList<IFrameListener> listeners = new ArrayList<>();

	public VideoFrameFeeder(int resolutionHeight, int bitrate, String streamId) {
		super(resolutionHeight, bitrate, streamId);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void prepareCodecLocal(int sourceWidth, int sourceHeight, AVRational videoCodecTimebase,
			AVRational sampleAspectRatio, int gopSize, int streamIndex, boolean isAVC, AVCodecParameters codecpar)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean writeFrameInternal(AVFrame frame, int streamIndex, long captureTimestampMS) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public VideoCodec getCodec() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCodecName() {
		// TODO Auto-generated method stub
		return null;
	}

	public void addListener(IFrameListener listener) {
		listeners.add(listener);
	}


}
