package io.antmedia.plugin;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avcodec.AVPacket;

import io.antmedia.plugin.api.IPacketListener;

public class PacketFeeder{

	private ArrayList<IPacketListener> listeners = new ArrayList<IPacketListener>();
	private String streamId;

	public PacketFeeder(String streamId) {
		this.streamId = streamId;
	}

	public void writeTrailer() {
		for (IPacketListener listener : listeners) {
			listener.writeTrailer();
		}
	}

	public void writePacket(AVPacket packet) {
		for (IPacketListener listener : listeners) {
			listener.onPacket(streamId, packet);
		}
	}

	public void addListener(IPacketListener listener) {
		listeners .add(listener);
	}
}
