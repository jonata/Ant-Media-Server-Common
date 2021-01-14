package io.antmedia.plugin;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;

import io.antmedia.muxer.Muxer;
import io.antmedia.plugin.api.IPacketListener;
import io.vertx.core.Vertx;

public class PacketFeeder extends Muxer{

	private ArrayList<IPacketListener> listeners = new ArrayList<IPacketListener>();

	public PacketFeeder(Vertx vertx) {
		super(vertx);
	}

	@Override
	public boolean addStream(AVCodec codec, AVCodecContext codecContext, int streamIndex) {
		System.out.println("PacketFeeder.addStream()  for "+streamId);
		return false;
	}

	@Override
	public boolean prepareIO() {
		System.out.println("PacketFeeder.prepareIO() for "+streamId);
		return false;
	}

	@Override
	public void writeTrailer() {
		System.out.println("PacketFeeder.writeTrailer() for "+streamId);
	}

	@Override
	public void writePacket(AVPacket avpacket, AVStream inStream) {
		System.out.println("PacketFeeder.writePacket() for "+streamId);
	}

	@Override
	public void writePacket(AVPacket avpacket, AVCodecContext codecContext) {
		System.out.println("PacketFeeder.writePacket() for "+streamId);
	}

	public void addListener(IPacketListener listener) {
		listeners .add(listener);
	}

}
