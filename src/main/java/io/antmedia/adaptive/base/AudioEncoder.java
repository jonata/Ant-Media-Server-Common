package io.antmedia.adaptive.base;

import static org.bytedeco.ffmpeg.global.avcodec.avcodec_fill_audio_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_DBL;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_DBLP;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S32;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S32P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_U8;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_U8P;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_get_bytes_per_sample;
import static org.bytedeco.ffmpeg.global.avutil.av_get_default_channel_layout;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.avutil.av_sample_fmt_is_planar;
import static org.bytedeco.ffmpeg.global.avutil.av_samples_get_buffer_size;
import static org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts;
import static org.bytedeco.ffmpeg.global.swresample.swr_convert;
import static org.bytedeco.ffmpeg.global.swresample.swr_free;
import static org.bytedeco.ffmpeg.global.swresample.swr_init;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.ShortPointer;

import io.antmedia.muxer.Muxer;

public abstract class AudioEncoder extends Encoder  {

	protected Pointer[] samplesIn;
	protected PointerPointer samplesInPtr;
	protected BytePointer[] samplesOut;
	protected SwrContext samplesConvertCtx = null;
	protected int samplesChannels = -1;
	protected int samplesFormat = -1;
	protected int samplesRate = -1;
	protected PointerPointer samplesOutPtr;
	protected AVFrame outputFrame;
	protected AVFrame rawAudioFrame;
	protected BytePointer rawBuffer;

	protected AVCodec audioCodec;
	protected AVCodecContext audioCodecContext;

	protected AVPacket avpacket;

	long pts = 0;

	private long tmpTimestampMS;
	private long totalResamplingTime;
	private long entranceTime;

	public AudioEncoder(int bitrate, String streamId) {
		super(bitrate, streamId);
	}


	public int getSampleRate() {
		if (audioCodecContext != null) {
			return audioCodecContext.sample_rate();
		}
		else {
			logger.error("Audio codec is not initialized. Call prepareCodec first. HashCode:{}", hashCode());
		}
		return 0;

	}

	public void prepareCodec(int sampleRate, int channelLayout, int streamIndex)  throws Exception {
		if (isStopped.get()) {
			//prepare codec and write trailer can be called in different threads
			//in other words, prepareCodec can be called after writingTrailer and it creates a leakage
			//so that if isStopped is true return immediately
			return;
		}
		synchronized (lock) {
			prepareCodecLocal(sampleRate, channelLayout, streamIndex);
		}
	}

	protected abstract void prepareCodecLocal(int sampleRate, int channelLayout, int streamIndex)  throws Exception;


	private boolean resampleAndEncode(long framePTS, int audioChannels, int sampleRate, int streamIndex, 
			AVCodecContext audioContext, Buffer[] samples, long timestampMS) throws Exception {

		boolean result = false;

		int inputSize = samples != null ? samples[0].limit() - samples[0].position() : 0;
		int inputFormat = AV_SAMPLE_FMT_NONE;
		int inputChannels = samples != null && samples.length > 1 ? 1 : audioChannels;
		int inputDepth = 0;
		int outputFormat = audioContext.sample_fmt();
		int outputChannels = samplesOut.length > 1 ? 1 : audioContext.channels();
		int outputDepth = av_get_bytes_per_sample(outputFormat);
		int ret;

		if (tmpTimestampMS == 0) {
			tmpTimestampMS = timestampMS;
		}

		if (samples != null && samples[0] instanceof ByteBuffer) {
			inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_U8P : AV_SAMPLE_FMT_U8;
			inputDepth = 1;
			for (int i = 0; i < samples.length; i++) {
				ByteBuffer b = (ByteBuffer)samples[i];
				if (samplesIn[i] instanceof BytePointer && samplesIn[i].capacity() >= inputSize && b.hasArray()) {
					((BytePointer)samplesIn[i]).position(0).put(b.array(), b.position(), inputSize);
				} else {
					samplesIn[i] = new BytePointer(b);
				}
			}
		} else if (samples != null && samples[0] instanceof ShortBuffer) {
			inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_S16P : AV_SAMPLE_FMT_S16;
			inputDepth = 2;
			for (int i = 0; i < samples.length; i++) {
				ShortBuffer b = (ShortBuffer)samples[i];
				if (samplesIn[i] instanceof ShortPointer && samplesIn[i].capacity() >= inputSize && b.hasArray()) {
					((ShortPointer)samplesIn[i]).position(0).put(b.array(), samples[i].position(), inputSize);
				} else {
					samplesIn[i] = new ShortPointer(b);
				}
			}
		} else if (samples != null && samples[0] instanceof IntBuffer) {
			inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_S32P : AV_SAMPLE_FMT_S32;
			inputDepth = 4;
			for (int i = 0; i < samples.length; i++) {
				IntBuffer b = (IntBuffer)samples[i];
				if (samplesIn[i] instanceof IntPointer && samplesIn[i].capacity() >= inputSize && b.hasArray()) {
					((IntPointer)samplesIn[i]).position(0).put(b.array(), samples[i].position(), inputSize);
				} else {
					samplesIn[i] = new IntPointer(b);
				}
			}
		} else if (samples != null && samples[0] instanceof FloatBuffer) {
			inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_FLTP : AV_SAMPLE_FMT_FLT;
			inputDepth = 4;
			for (int i = 0; i < samples.length; i++) {
				FloatBuffer b = (FloatBuffer)samples[i];
				if (samplesIn[i] instanceof FloatPointer && samplesIn[i].capacity() >= inputSize && b.hasArray()) {
					((FloatPointer)samplesIn[i]).position(0).put(b.array(), b.position(), inputSize);
				} else {
					samplesIn[i] = new FloatPointer(b);
				}
			}
		} else if (samples != null && samples[0] instanceof DoubleBuffer) {
			inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_DBLP : AV_SAMPLE_FMT_DBL;
			inputDepth = 8;
			for (int i = 0; i < samples.length; i++) {
				DoubleBuffer b = (DoubleBuffer)samples[i];
				if (samplesIn[i] instanceof DoublePointer && samplesIn[i].capacity() >= inputSize && b.hasArray()) {
					((DoublePointer)samplesIn[i]).position(0).put(b.array(), b.position(), inputSize);
				} else {
					samplesIn[i] = new DoublePointer(b);
				}
			}
		} else if (samples != null) {
			throw new IllegalStateException("Audio samples Buffer has unsupported type: " + samples);
		}


		if (samplesConvertCtx == null || samplesChannels != audioChannels || samplesFormat != inputFormat || samplesRate != sampleRate) {
			samplesConvertCtx = swr_alloc_set_opts(samplesConvertCtx, audioContext.channel_layout(), outputFormat, audioContext.sample_rate(),
					av_get_default_channel_layout(audioChannels), inputFormat, sampleRate, 0, null);
			if (samplesConvertCtx == null) {
				throw new NullPointerException("swr_alloc_set_opts() error: Cannot allocate the conversion context.");
			} else if ((ret = swr_init(samplesConvertCtx)) < 0) {
				throw new IllegalStateException("swr_init() error " + ret + ": Cannot initialize the conversion context.");
			}
			samplesChannels = audioChannels;
			samplesFormat = inputFormat;
			samplesRate = sampleRate;
		}

		for (int i = 0; samples != null && i < samples.length; i++) {
			samplesIn[i].position(samplesIn[i].position() * inputDepth).
			limit((samplesIn[i].position() + inputSize) * inputDepth);
		}



		while (true) {
			int inputCount = (int)Math.min(samples != null ? (samplesIn[0].limit() - samplesIn[0].position()) / (inputChannels * inputDepth) : 0, Integer.MAX_VALUE);
			int outputCount = (int)Math.min((samplesOut[0].limit() - samplesOut[0].position()) / (outputChannels * outputDepth), Integer.MAX_VALUE);
			inputCount = Math.min(inputCount, (outputCount * sampleRate + audioContext.sample_rate() - 1) / audioContext.sample_rate());
			for (int i = 0; samples != null && i < samples.length; i++) {
				samplesInPtr.put(i, samplesIn[i]);
			}
			for (int i = 0; i < samplesOut.length; i++) {
				samplesOutPtr.put(i, samplesOut[i]);
			}
			if ((ret = swr_convert(samplesConvertCtx, samplesOutPtr, outputCount, samplesInPtr, inputCount)) < 0) {
				throw new IllegalStateException("swr_convert() error " + ret + ": Cannot convert audio samples.");
			} else if (ret == 0) {
				break;
			}
			for (int i = 0; samples != null && i < samples.length; i++) {
				samplesIn[i].position(samplesIn[i].position() + inputCount * inputChannels * inputDepth);
			}
			for (int i = 0; i < samplesOut.length; i++) {
				samplesOut[i].position(samplesOut[i].position() + ret * outputChannels * outputDepth);
			}

			if (samples == null || samplesOut[0].position() >= samplesOut[0].limit()) {
				outputFrame.nb_samples(audioContext.frame_size());
				avcodec_fill_audio_frame(outputFrame, audioContext.channels(), outputFormat, samplesOut[0], (int)Math.min(samplesOut[0].limit(), Integer.MAX_VALUE), 0);
				for (int i = 0; i < samplesOut.length; i++) {
					outputFrame.data(i, samplesOut[i].position(0));
					outputFrame.linesize(i, (int)Math.min(samplesOut[i].limit(), Integer.MAX_VALUE));
				}
				outputFrame.quality(audioContext.global_quality());
				outputFrame.pts(pts);
				outputFrame.sample_rate(audioContext.sample_rate());
				pts += outputFrame.nb_samples();

				encode(outputFrame, streamIndex, tmpTimestampMS);
				tmpTimestampMS = 0;

				result = true;
			}
		}
		return result;
	}



	@Override
	public boolean writeFrame(AVFrame frame, int streamIndex, long timestampMS) throws Exception {

		synchronized (lock) {
			entranceTime = System.nanoTime();
			boolean result = false;
			if (!running.get()) {
				logger.warn("Audio Encoder is not running right now. hashCode: {} for stream:{}", hashCode(), streamId);
				return false;
			}

			if (frame == null) {
				return encode(frame, streamIndex, timestampMS);
			}

			if (frame.format() != audioCodecContext.sample_fmt() || 
					frame.sample_rate() != audioCodecContext.sample_rate() ||
					frame.channels() != audioCodecContext.channels() ||
					frame.nb_samples() != audioCodecContext.frame_size()) {


				int sampleFormat = frame.format();
				int planes = av_sample_fmt_is_planar(sampleFormat) != 0 ? (int)frame.channels() : 1;
				int dataSize = av_samples_get_buffer_size((IntPointer)null, frame.channels(),
						frame.nb_samples(), frame.format(), 1) / planes; 

				if (dataSize < 0) {
					throw new IllegalStateException("Cannot get buffer size correctly, channel count: " + frame.channels() + " number of samples: " + frame.nb_samples() + " frame format: " + frame.format());
				}

				Buffer[] samplesBuf = new Buffer[planes];

				for (int i = 0; i < planes; i++) {
					BytePointer p = frame.data(i);
					{
						p.capacity(dataSize);
						ByteBuffer b   = p.asBuffer();
						switch (sampleFormat) {
						case AV_SAMPLE_FMT_U8:
						case AV_SAMPLE_FMT_U8P:  samplesBuf[i] = b; break;
						case AV_SAMPLE_FMT_S16:
						case AV_SAMPLE_FMT_S16P: samplesBuf[i] = b.asShortBuffer();  break;
						case AV_SAMPLE_FMT_S32:
						case AV_SAMPLE_FMT_S32P: samplesBuf[i] = b.asIntBuffer();    break;
						case AV_SAMPLE_FMT_FLT:
						case AV_SAMPLE_FMT_FLTP: samplesBuf[i] = b.asFloatBuffer();  break;
						case AV_SAMPLE_FMT_DBL:
						case AV_SAMPLE_FMT_DBLP: samplesBuf[i] = b.asDoubleBuffer(); break;
						default: assert false;
						}
					}
					samplesBuf[i].position(0).limit(frame.nb_samples());
				}
				//!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				//TODO: No need convert from bytepointer to buffer. Bytepointer can be given as parameter to 
				// below function. It needs refactor. However it gives weird memory erros
				//!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				result = resampleAndEncode(frame.pts(), frame.channels(), frame.sample_rate(), streamIndex, audioCodecContext, samplesBuf, timestampMS);
			}
			else {
				result = encode(frame, streamIndex, timestampMS);
			}
			totalProcessingTime += System.nanoTime() - entranceTime;
			encodedPacketCount++;
			return result;
		}
	}

	public abstract boolean encode(AVFrame audioFrame, int streamIndex, long timestampMS) throws Exception;


	@Override
	public void writeTrailer() {
		isStopped.set(true);
		synchronized (lock) {
			running.set(false); 
			for (Muxer muxer : muxerList) {
				muxer.writeTrailer();
			}

			if (audioCodecContext != null) {
				avcodec_free_context(audioCodecContext);
				audioCodecContext.close();
				audioCodecContext = null;
			}	

			if (rawAudioFrame != null) {
				av_frame_free(rawAudioFrame);
				rawAudioFrame.close();
				rawAudioFrame = null;
			}

			if (outputFrame != null) {
				av_frame_free(outputFrame);
				outputFrame.close();
				outputFrame = null;
			}

			if (samplesConvertCtx != null) {
				swr_free(samplesConvertCtx);
				samplesConvertCtx.close();
				samplesConvertCtx = null;
			}

			if (samplesOut != null) {
				for (int i = 0; i < samplesOut.length; i++) {
					av_free(samplesOut[i].position(0));
					samplesOut[i].close();
				}
				samplesOut = null;
			}	

			if (rawBuffer != null) {
				av_free(rawBuffer.position(0));
				rawBuffer.close();
				rawBuffer = null;
			}


			logger.info("writing trailer codec name: {} bitrate:{} for stream:{}", this.getCodecName(), this.getBitrate(), streamId);
			if (encodedPacketCount > 0) {
				long avarageProcessingTime = totalProcessingTime / encodedPacketCount;
				logger.info("Total processing time {}, Average processing time {} ms per frame on average for stream:{}", 
						(int)(totalProcessingTime / 1e6), (int)(avarageProcessingTime / 1e6), streamId);
			}

			if (avpacket != null) {
				avpacket.close();
				avpacket = null;
			}
		}
	}


	/**
	 * Write raw audio to encoder. It only supports AV_SAMPLE_FMT_S16 format right now.
	 * @param numberOfFrames
	 * @param sampleRate
	 * @param channelCount
	 * @param data
	 * @throws Exception
	 */
	public synchronized void writeRawAudio(int numberOfFrames, int sampleRate, int channelCount, byte[] data, long timestampMS, int streamIndex) throws Exception
	{
		if (rawAudioFrame == null) {
			rawAudioFrame = av_frame_alloc();
			rawAudioFrame.pts(0);
		}

		rawAudioFrame.nb_samples(numberOfFrames);
		rawAudioFrame.sample_rate(sampleRate);
		rawAudioFrame.format(AV_SAMPLE_FMT_S16);
		rawAudioFrame.channels(channelCount);
		long localPts = timestampMS * sampleRate / 1000;

		rawAudioFrame.pts(localPts);
		rawAudioFrame.channel_layout(av_get_default_channel_layout(channelCount));

		int bufferSize = av_samples_get_buffer_size((IntPointer)null, channelCount, numberOfFrames,
				AV_SAMPLE_FMT_S16, 0);
		if (rawBuffer == null || rawBuffer.limit() < bufferSize) {
			if (rawBuffer != null) {
				av_free(rawBuffer);
			}
			rawBuffer = new BytePointer(av_malloc(bufferSize)).capacity(bufferSize);
		}

		int ret = avcodec_fill_audio_frame(rawAudioFrame, channelCount, AV_SAMPLE_FMT_S16,
				rawBuffer, bufferSize, 0);

		if (ret < 0) {
			throw new IllegalStateException("avcodec_fill_audio_frame does not return successfully");
		}

		rawBuffer.put(data, 0, data.length);

		rawAudioFrame.data(0, rawBuffer);
		rawAudioFrame.linesize(0, numberOfFrames);

		writeFrame(rawAudioFrame, streamIndex, timestampMS);
	}


	public AVRational getTimebase() {
		return audioCodecContext.time_base();
	}

	@Override
	public String getCodecName() {
		if (audioCodec != null) {
			return audioCodec.name().getString();
		}
		return null;
	}

}
