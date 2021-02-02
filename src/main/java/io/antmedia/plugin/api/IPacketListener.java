package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;

public interface IPacketListener {
	AVPacket onPacket(String streamId, AVPacket packet);
	void writeTrailer();
	void setVideoCodecParameter(String streamId, AVCodecParameters videoCodecParameters);
	void setAudioCodecParameter(String streamId, AVCodecParameters audioCodecParameters);
}
