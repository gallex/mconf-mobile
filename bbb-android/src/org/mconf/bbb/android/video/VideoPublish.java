package org.mconf.bbb.android.video;

import java.util.ArrayList;
import java.util.List;

import org.mconf.bbb.BigBlueButtonClient;
import org.mconf.bbb.video.IVideoPublishListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.hardware.Camera;

import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.Video;

public class VideoPublish extends Thread implements RtmpReader {
	private class VideoPublishHandler extends IVideoPublishListener {
		
		public VideoPublishHandler(int userId, String streamName, RtmpReader reader, BigBlueButtonClient context) {			
			super(userId, streamName, reader, context);
		}
				
	}
	
	private static final Logger log = LoggerFactory.getLogger(VideoPublish.class);
	
	public Camera mCamera;
	public int bufSize;
    public int frameRate = CaptureConstants.DEFAULT_FRAME_RATE;
    public int width = CaptureConstants.DEFAULT_WIDTH;
    public int height = CaptureConstants.DEFAULT_HEIGHT;
    public int bitRate = CaptureConstants.DEFAULT_BIT_RATE;
    public int GOP = CaptureConstants.DEFAULT_GOP;
   
    private byte[] sharedBuffer;
    
    public boolean isCapturing = false;
    public boolean isReadyToResume = false;
    public boolean allowResume = false;
    public boolean nativeEncoderInitialized = false;
    public boolean restartWhenResume;
    public boolean paused = false; // false when: STOPPED, STARTED or RESUMED. true when: PAUSED
    
    private int firstTimeStamp = 0;
	private int lastTimeStamp = 0;
	private String streamId;
	private int userId;
	
	private List<Video> framesList = new ArrayList<Video>();
	
	private VideoPublishHandler videoPublishHandler;
	
	private BigBlueButtonClient context;
	
	private VideoCapture mVideoCapture;	
	private boolean framesListAvailable = false;
	        
    public VideoPublish(BigBlueButtonClient context, int userId, boolean restartWhenResume) {
    	this.context = context;    	 
    	this.userId = userId;
    	
    	this.restartWhenResume = restartWhenResume;
    }
    
    public void startPublisher(){
    	videoPublishHandler = new VideoPublishHandler(userId, streamId, this, context);
    	videoPublishHandler.start();
    }        	
    
    public void stopPublisher(){
    	videoPublishHandler.stop(context);
    }
    
    public void readyToResume(VideoCapture videoCapture) {
    	mVideoCapture = videoCapture;
    	isReadyToResume = true;
	}
    
    public int RequestResume() {
    	if(mVideoCapture == null){
    		log.debug("Error: resume requested but there is not a VideoCapture class available");
    		return CaptureConstants.E_COULD_NOT_REQUEST_RESUME;
    	}
    	mVideoCapture.resumeCapture();
    	mVideoCapture = null;
    	return CaptureConstants.E_OK;
	}
    
    public void initNativeEncoder(){
    	sharedBuffer = new byte[bufSize]; // the encoded frame will never be bigger than the not encoded
    	
    	initEncoder(width, height, frameRate, bitRate, GOP);
    	  	
    	streamId = width+"x"+height+userId; 
    	
    	nativeEncoderInitialized = true;
    }
    
    public void endNativeEncoder(){
    	isCapturing = false;
    	nativeEncoderInitialized = false;
        	
    	endEncoder();
    }
    
    @Override
    public void run() {
       	isCapturing = true;
       	
    	initSenderLoop();
    }
    
    public byte[] assignJavaBuffer()
	{
    	return sharedBuffer;
	}
    
    public int onReadyFrame (int bufferSize, int timeStamp)
    {    	
    	if(firstTimeStamp == 0){
    		firstTimeStamp = timeStamp;
    	}    	
    	timeStamp = timeStamp - firstTimeStamp;
    	int interval = timeStamp - lastTimeStamp;
    	lastTimeStamp = timeStamp;
    	
    	byte[] aux = new byte[bufferSize];
    	System.arraycopy(sharedBuffer, 0, aux, 0, bufferSize); //\TODO see if we can avoid this copy
    	
       	Video video = new Video(timeStamp, aux, bufferSize);
   	    video.getHeader().setDeltaTime(interval);
		video.getHeader().setStreamId(this.videoPublishHandler.videoConnection.streamId);
		
		if(framesListAvailable && framesList != null){
			framesList.add(video);
		}
		
    	return 0;
    }

	@Override
	public void close() {		
		framesListAvailable = false;
		if(framesList != null){
			framesList.clear();
		}
		framesList = null;
	}

	@Override
	public Metadata getMetadata() {
		return null;
	}

	@Override
	public Video[] getStartMessages() {
		framesListAvailable = true;
		Video[] startMessages = new Video[0];
        return startMessages;
	}

	@Override
	public long getTimePosition() {
		return 0;
	}

	@Override
	public boolean hasNext() {
		while(isCapturing && framesListAvailable && framesList != null && framesList.isEmpty()){
			try {
				this.wait(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(isCapturing && framesListAvailable && framesList != null){
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Video next() {
		if(framesListAvailable && framesList != null){
			return framesList.remove(0);
		} else {
			Video emptyVideo = new Video();
	        return emptyVideo;
		}
	}

	@Override
	public long seek(long timePosition) {
		return 0;
	}

	@Override
	public void setAggregateDuration(int targetDuration) {
	}
	
	private native int initEncoder(int width, int height, int frameRate, int bitRate, int GOP);
	private native int endEncoder();
    private native int initSenderLoop();
}