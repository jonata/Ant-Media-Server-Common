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
	}

	@Override
	protected void prepareCodecLocal(int sourceWidth, int sourceHeight, AVRational videoCodecTimebase,
			AVRational sampleAspectRatio, int gopSize, int streamIndex, boolean isAVC, AVCodecParameters codecpar)
					throws Exception {
		System.out.println("VideoFrameFeeder.prepareCodecLocal()   for "+streamId);
	}

	@Override
	public boolean writeFrameInternal(AVFrame frame, int streamIndex, long captureTimestampMS) throws Exception {
		//System.out.println("VideoFrameFeeder.writeFrameInternal()  for "+streamId);
		
		for (IFrameListener iFrameListener : listeners) {
			iFrameListener.onVideoFrame(streamId, frame);
		}
		return false;
	}

	@Override
	public VideoCodec getCodec() {
		//System.out.println("VideoFrameFeeder.getCodec()  for "+streamId);
		return null;
	}

	@Override
	public String getCodecName() {
		//System.out.println("VideoFrameFeeder.getCodecName()  for "+streamId);
		return null;
	}

	public void addListener(IFrameListener listener) {
		listeners.add(listener);
	}
	
	@Override
	public void writeTrailer() {
		super.writeTrailer();
		for (IFrameListener iFrameListener : listeners) {
			iFrameListener.writeTrailer();
		}
	}


}
