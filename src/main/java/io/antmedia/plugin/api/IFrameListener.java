package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVFrame;

public interface IFrameListener {

	void onAudioFrame(String streamId, AVFrame audioFrame);
	void onVideoFrame(String streamId, AVFrame frame);
	void writeTrailer();
	void setVideoCodecParameter(String streamId, AVCodecParameters videoCodecParameters);
	void setAudioCodecParameter(String streamId, AVCodecParameters audioCodecParameters);

}
