package io.antmedia.adaptive.base;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.muxer.Muxer;

public abstract class Encoder {

	protected List<Muxer> muxerList = new ArrayList<>();
	
	protected long totalProcessingTime = 0;
	protected long encodedPacketCount = 0;
	
	protected AtomicBoolean isStopped = new AtomicBoolean(false);
	
	protected AtomicBoolean running = new AtomicBoolean(false);

	private int bitrate;
	protected Logger logger;
	
	protected String streamId;
	
	protected Object lock = new Object();
	
	public Encoder(int bitrate, String streamId) {
		this.bitrate= bitrate;
		logger = LoggerFactory.getLogger(this.getClass());
		this.streamId = streamId;
	}
	
	/*
	 * Lock addMuxer because sometimes concurrent modificaiton exception occurs.
	 * Please remind that Collection.SynchrnoizedList needs also synch while iterating
	 */
	public void addMuxer(Muxer m) {
		synchronized (lock) {
			muxerList.add(m);
		}
		
	}
	
	public List<Muxer> getMuxerList() {
		return muxerList;
	}

	/*
	 * Lock the muxer list to not encounter concurrent modification
	 */
	public void prepareIO() {
		synchronized (lock) {
			for (Muxer muxer : muxerList) {
				muxer.prepareIO();
			}
		}
	}

	/*
	 * Lock removeMuxer because sometimes concurrent modificaiton exception occurs.
	 * Please remind that Collection.SynchrnoizedList needs also synch while iterating
	 */
	public boolean removeMuxer(Muxer m){
		synchronized (lock) {
			return muxerList.remove(m);
		}
		
	}
	
	public abstract void writeTrailer();
	
	public abstract boolean writeFrame(AVFrame frame, int streamIndex, long captureTimeMS) throws Exception;
	
	public abstract String getCodecName();

	public int getBitrate() {
		return bitrate;
	}

	public void setBitrate(int bitrate) {
		this.bitrate = bitrate;
	}

	public long getEncodedPacketCount() {
		return encodedPacketCount;
	}
	
	public long getTotalProcessingTime() {
		return totalProcessingTime;
	}
	
	public boolean isRunning() {
		return running.get();
	}

}
