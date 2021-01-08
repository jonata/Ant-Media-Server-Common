package io.antmedia.plugin;

import org.bytedeco.ffmpeg.avutil.AVFrame;

import io.antmedia.Encoder;

public class FrameFeeder extends Encoder {

	public FrameFeeder(int bitrate, String streamId) {
		super(bitrate, streamId);
	}

	@Override
	public void writeTrailer() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean writeFrame(AVFrame frame, int streamIndex, long captureTimeMS) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getCodecName() {
		// TODO Auto-generated method stub
		return null;
	}

}
