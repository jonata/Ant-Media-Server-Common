package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avutil.AVFrame;

public interface IFrameListener {

	void onAudioFrame(String streamId, AVFrame audioFrame);
	void onVideoFrame(String streamId, AVFrame frame);
	void writeTrailer();

}
