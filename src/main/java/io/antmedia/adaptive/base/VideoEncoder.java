package io.antmedia.adaptive.base;

import java.nio.ByteBuffer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.webrtc.VideoDecoder;

import io.antmedia.AppSettings;
import io.antmedia.muxer.Muxer;
import io.antmedia.webrtc.VideoCodec;
import org.bytedeco.ffmpeg.global.*;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swresample.*;
import org.bytedeco.ffmpeg.swscale.*;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avdevice.*;
import static org.bytedeco.ffmpeg.global.swresample.*;
import static org.bytedeco.ffmpeg.global.swscale.*;
public abstract class VideoEncoder extends Encoder {

	protected int resolutionHeight;
	protected int resolutionWidth;
	protected AVCodecContext videoCodecContext = null;
	protected SwsContext sws_ctx = null; 
	protected AVCodec codec = null;
	protected BytePointer picture_bufptr;
	protected AVFrame rawVideoFrame;
	protected AVFrame picture;
	private BytePointer rawBuffer;
	private int rawVideoWidth;
	private int rawVideoHeight;
	private String error;

	protected AVPacket avpacket;

	protected int sourceWidth;
	protected int sourceHeight;
	protected AVRational videoCodecTimebase;
	protected AVRational sampleAspectRatio;
	protected int gopSize;
	protected int streamIndex;
	protected boolean isAVC;
	protected AVCodecParameters codecpar;
	protected long totalVideoEncoderTime;
	private long totalVideoEncodeQueueTime;
	
	 protected AppSettings appSettings;
	 protected String codecName;



	public VideoEncoder(int resolutionHeight, int bitrate, String streamId) {
		super(bitrate, streamId);
		this.resolutionHeight = resolutionHeight;
	}
	
	public boolean getVideoCodecContext(String name, int width, int height, AVRational timeBase,
			AVRational sampleAspectRatio, int gopSize) {
		codec = avcodec_find_encoder_by_name(name);

		if (codec == null) {
			logger.error("codec({}) cannot be find for stream:{} ", name, streamId);
			return false;
		}
		codecName = codec.name().getString();

		videoCodecContext = avcodec_alloc_context3(codec);
		if (videoCodecContext == null) {
			logger.info("Video codec context ({}) cannot be allocated for stream:{}", name, streamId);
			return false;
		}
		videoCodecContext.width(width);
		videoCodecContext.height(height);

        videoCodecContext.time_base(timeBase);
        videoCodecContext.pix_fmt(AV_PIX_FMT_YUV420P);

        videoCodecContext.sample_aspect_ratio(sampleAspectRatio);


       
        if (appSettings.getGopSize() != 0) 
        {
        	videoCodecContext.gop_size(appSettings.getGopSize());
        }
        else {
        	 if (gopSize > 100) {
                 //do not increase more than 100 for gop size. It is approximately 4sec for 25fps
                 gopSize = 100;
        	 }
        	 else if (gopSize <= 0) {
        		 gopSize = 60;
        	 }
        	 videoCodecContext.gop_size(gopSize);
        }
		return true;
	}


	public void prepareCodec(int sourceWidth, int sourceHeight, AVRational videoCodecTimebase, AVRational sampleAspectRatio, int gopSize, int streamIndex, boolean isAVC, AVCodecParameters codecpar) 
			throws Exception {
		
		synchronized(lock) 
		{
			if (isStopped.get()) {
				//prepare codec and write trailer can be called in different threads
				//in other words, prepareCodec can be called after writingTrailer and it creates a leakage
				//so that if isStopped is true return immediately
				logger.info("Codec is stopped before it's prepared for stream:{}", streamId);
				return;
			}
			//because this is synch it's likely no need to have additional synch in methods prepaceCodecLocal
			//BE CAREFUL about synch 
			prepareCodecLocal(sourceWidth, sourceHeight, videoCodecTimebase, sampleAspectRatio, gopSize, streamIndex, isAVC, codecpar);
		}
	}

	protected abstract void prepareCodecLocal(int sourceWidth, int sourceHeight, AVRational videoCodecTimebase, AVRational sampleAspectRatio, int gopSize, int streamIndex, boolean isAVC, AVCodecParameters codecpar) 
			throws Exception;

	private void initializeRawBuffer(int size, int[] yuvStrides, int width, int height) {
		rawBuffer = new BytePointer(av_malloc(size));

		rawVideoFrame.linesize(0, yuvStrides[0]);
		rawVideoFrame.linesize(1, yuvStrides[1]);
		rawVideoFrame.linesize(2, yuvStrides[2]);

		av_image_fill_pointers(new PointerPointer(rawVideoFrame), AV_PIX_FMT_YUV420P, height, rawBuffer, rawVideoFrame.linesize());
		rawVideoWidth = width;
		rawVideoHeight = height;
	}

	public synchronized boolean writeRawVideo(byte[] frameData, int size, int[] yuvStrides, int width, int height, long frameNumber, int streamIndex, long captureTimestampMS) throws Exception
	{
		if (rawVideoFrame == null) {
			rawVideoFrame = av_frame_alloc();
			initializeRawBuffer(size, yuvStrides, width, height);
		}

		if (rawVideoWidth != width || rawVideoHeight != height) {
			av_free(rawBuffer);
			rawBuffer = null;
			av_frame_free(rawVideoFrame);
			rawVideoFrame = null;

			rawVideoFrame = av_frame_alloc();
			initializeRawBuffer(size, yuvStrides, width, height);
		}


		rawVideoFrame.width(width);
		rawVideoFrame.height(height);
		rawVideoFrame.format(AV_PIX_FMT_YUV420P);

		rawVideoFrame.pts(frameNumber);

		rawBuffer.position(0);

		rawBuffer.put(frameData, 0, size);

		return writeFrame(rawVideoFrame, streamIndex, captureTimestampMS);

	}
	
	
	/**
	 * No need to have double synch in methods so that synch for method is removed which caused a dead-lock for another thread
	 */
	public boolean writeFrame(AVFrame frame, int streamIndex,  long captureTimestampMS) throws Exception{
		synchronized (lock) {
			totalVideoEncodeQueueTime += (System.currentTimeMillis() - captureTimestampMS);
			return writeFrameInternal(frame, streamIndex, captureTimestampMS);
		}
	}
	
	public abstract boolean writeFrameInternal(AVFrame frame, int streamIndex,  long captureTimestampMS) throws Exception;


	@Override
	public void writeTrailer() {
		isStopped.set(true);
		
		synchronized(lock) {
			running.set(false);
			for (Muxer muxer : muxerList) {
				muxer.writeTrailer();
			}

			// Close the video codec
			if (videoCodecContext != null) {
				avcodec_free_context(videoCodecContext);
				videoCodecContext.close();
				videoCodecContext = null;
			}

			if (rawVideoFrame != null) {
				av_frame_free(rawVideoFrame);
				rawVideoFrame.close();
				rawVideoFrame = null;
			}

			if (rawBuffer != null) {
				av_free(rawBuffer);
				rawBuffer.close();
				rawBuffer = null;
			}

			if (sws_ctx != null) {
				sws_freeContext(sws_ctx);
				sws_ctx.close();
				sws_ctx = null;
			}

			if (picture_bufptr != null) {
				picture_bufptr.close();
				picture_bufptr = null;
			}
			if (picture != null) {
				av_frame_free(picture);
				picture.close();
				picture = null;
			}
			logger.info("Video codec writing trailer name: {} height:{} for stream:{}", this.getCodecName(), this.getResolutionHeight(), streamId);
			//this is the average time between capturing frame and writing to muxers
			if (encodedPacketCount > 0) {
				long avarageProcessing = totalProcessingTime / encodedPacketCount;
				logger.info("Total processing time {} ms Average processing time {} ms per frame for stream:{}", (int)(totalProcessingTime / 1e6), (int)(avarageProcessing / 1e6), streamId);
			}

			if (avpacket != null) {
				avpacket.close();
				avpacket = null;
			}		
		}
	}

	/**
	 * Write encodedVideoFrame to muxers directly
	 *
	 * @param buffer
	 * @param timestamp
	 * @param streamIndex
	 * @param isKeyFrame
	 */
	public void writeVideoBuffer(ByteBuffer encodedVideoFrame, long dts, int frameRotation, int streamIndex,
								 boolean isKeyFrame,long firstFrameTimeStamp, long pts)
	{
		synchronized (lock) 
		{	
			for (Muxer muxer : muxerList) {
				muxer.writeVideoBuffer(encodedVideoFrame, dts, frameRotation, streamIndex,isKeyFrame,firstFrameTimeStamp, pts);
			}
		}
	}


	public int getResolutionWidth() {
		return resolutionWidth;
	}

	public int getResolutionHeight() {
		return resolutionHeight;
	}

	public AVRational getTimebase() {
		if (videoCodecContext != null) {
			return videoCodecContext.time_base();
		}
		return null;
	}


	public String getError() {
		return error;
	}


	public void setError(String error) {
		this.error = error;
	}


	public long getTotalVideoEncoderTime() {
		return totalVideoEncoderTime;
	}

	public long getTotalVideoEncodeQueueTime() {
		return totalVideoEncodeQueueTime;
	}
	
	public abstract VideoCodec getCodec();




}
