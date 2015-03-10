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
            return (mRecorder.getMaxAmplitude()/2700.0);
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
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;

        obj.put("value", 20 * Math.log10(mEMA / 32767.0));
        obj.put("timestamp", this.timeStamp);

        return obj;
    }
}
