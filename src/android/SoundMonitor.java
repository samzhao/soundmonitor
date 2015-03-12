package plugin.samz.soundmonitor;

import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Handler;
import android.os.Looper;

/**
 * This class echoes a string called from JavaScript.
 */
public class SoundMonitor extends CordovaPlugin {

    private static final String TAG = "AudioRecord";
    static final int SAMPLE_RATE_IN_HZ = 8000;
    static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                    AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
    AudioRecord mAudioRecord;
    boolean isGetVoiceRun;
    Object mLock;

    private AudioRecord ar = null;
    private int minSize;

    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;

    public long TIMEOUT = 30000;        // Timeout in msec to shut off listener

    int status;
    float amplitude;
    long timeStamp;
    long lastAccessTime;

    static final private double EMA_FILTER = 0.6;

    private MediaRecorder mRecorder = null;
    private double mEMA = 0.0;

    private CallbackContext callbackContext;

    public SoundMonitor() {
        this.amplitude = 0;
        this.timeStamp = 0;
        this.setStatus(SoundMonitor.STOPPED);
        mLock = new Object();
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("start")) {
            this.start();
        }
        else if (action.equals("stop")) {
            this.stop();
        }
        else if (action.equals("getStatus")) {
            int i = this.getStatus();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, i));
        }
        else if (action.equals("getAmplitude")) {
            if (this.status != SoundMonitor.RUNNING) {
                int r = this.start();
                if (r == SoundMonitor.ERROR_FAILED_TO_START) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.IO_EXCEPTION, SoundMonitor.ERROR_FAILED_TO_START));
                    return true;
                }
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    public void run() {
                        SoundMonitor.this.timeout();
                    }
                }, 2000);
            }
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getSoundAmplitude()));
        }
        else if (action.equals("setTimeout")) {
            this.setTimeout(args.getLong(0));
        }
        else if (action.equals("getTimeout")) {
            long l = this.getTimeout();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, l));
        } else {
            return false;
        }
        return true;
    }

    public void onDestroy() {
        this.stop();
    }

    public void onReset() {
        this.stop();
    }

    public int start() {
        if ((this.status == SoundMonitor.RUNNING) || (this.status == SoundMonitor.STARTING)) {
            return this.status;
        }

        // minSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // ar = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000,AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
        // ar.startRecording();

        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");

            try
            {
                mRecorder.prepare();
            }catch (java.io.IOException ioe) {

            }catch (java.lang.SecurityException e) {
            }
            try
            {
                mRecorder.start();
            }catch (java.lang.SecurityException e) {
            }

            mEMA = 0.0;
        }

        this.lastAccessTime = System.currentTimeMillis();
        this.setStatus(SoundMonitor.STARTING);

        return this.status;
    }

    public void stop() {
        if (this.status != SoundMonitor.STOPPED) {
        }

        // if (ar != null) {
        //     ar.stop();
        // }

        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }

        this.setStatus(SoundMonitor.STOPPED);
    }

    private void timeout() {
        if (this.status == SoundMonitor.STARTING) {
            this.setStatus(SoundMonitor.ERROR_FAILED_TO_START);
            if (this.callbackContext != null) {
                this.callbackContext.error("Sound listener failed to start.");
            }
        }
    }

    public int getStatus() {
        return this.status;
    }

    public double getAmplitude() {
        this.lastAccessTime = System.currentTimeMillis();

        // short[] buffer = new short[minSize];
        // ar.read(buffer, 0, minSize);
        // int max = 0;
        // for (short s : buffer) {
        //     if (Math.abs(s) > max) {
        //         max = Math.abs(s);
        //     }
        // }
        // return max;

        if (mRecorder != null) {
            // double pressure = mRecorder.getMaxAmplitude() / 51805.5336;
            // return 20 * Math.log10(pressure / (2*Math.pow(10, -5)));

            double ratio = (double)mRecorder.getMaxAmplitude() / 1;
            double db = 0;
            if (ratio > 1) {
                db = 20 * Math.log10(ratio);
            }
        } else {
            return 0;
        }
    }

    public void setTimeout(long timeout) {
        this.TIMEOUT = timeout;
    }

    public long getTimeout() {
        return this.TIMEOUT;
    }

    private void setStatus(int status) {
        this.status = status;
    }

    public double getNoiseLevel() {
            if (isGetVoiceRun) {
                return;
            }
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
            if (mAudioRecord == null) {
            }
            isGetVoiceRun = true;

            double final_volume = 0;

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    mAudioRecord.startRecording();
                    short[] buffer = new short[BUFFER_SIZE];
                    while (isGetVoiceRun) {
                        //r是实际读取的数据长度，一般而言r会小于buffersize
                        int r = mAudioRecord.read(buffer, 0, BUFFER_SIZE);
                        long v = 0;
                        // 将 buffer 内容取出，进行平方和运算
                        for (int i = 0; i < buffer.length; i++) {
                            v += buffer[i] * buffer[i];
                        }
                        // 平方和除以数据总长度，得到音量大小。
                        double mean = v / (double) r;
                        double volume = 10 * Math.log10(mean);
                        final_volume = volume;
                        // 大概一秒十次
                        synchronized (mLock) {
                            try {
                                mLock.wait(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mAudioRecord.stop();
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }).start();

            return final_volume;
        }

    private JSONObject getSoundAmplitude() throws JSONException {
        JSONObject obj = new JSONObject();

        // short[] buffer = new short[minSize];
        // ar.read(buffer, 0, minSize);
        // int max = 0;
        // for (short s : buffer) {
        //     if (Math.abs(s) > max) {
        //         max = Math.abs(s);
        //     }
        // }

        double amp = getAmplitude();
        // mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;

        obj.put("value", amp);
        obj.put("timestamp", this.timeStamp);

        return obj;
    }
}
