package com.unity.lostcryptlargescreenexample;

import com.unity3d.player.UnityPlayerActivity;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowManager;
import android.util.Log;
import android.view.Display;
import android.util.DisplayMetrics;

import org.json.JSONObject;
import org.json.JSONException;
import android.graphics.Rect;
import android.view.Surface;

import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Consumer;
import java.util.concurrent.Executor;

// For hinge angle sensor readings
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;



import static androidx.core.content.ContextCompat.getSystemService;


public class LargeScreenPlayableActivity extends UnityPlayerActivity {
	String TAG = "LargeScreenPlayable";
	private SensorManager mSensorManager;
    private Sensor mHingeAngleSensor;
    private SensorEventListener mSensorListener;
    private float lastValue = -1.0f;
	
    static Context mContext;
    FoldingFeature lastFoldingFeature = null;
    WindowInfoTrackerCallbackAdapter wit;
    private final LayoutStateChangeCallback layoutStateChangeCallback =
            new LayoutStateChangeCallback();
	

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mContext = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        wit = new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this));
        wit.addWindowLayoutInfoListener(
            this, Runnable::run, layoutStateChangeCallback);
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.d(TAG, "on Configuration changed: " + newConfig.toString());

        // winConfig={ mBounds=Rect(0, 0 - 1080, 2092) mAppBounds=Rect(0, 0 - 1080, 1896) mMaxBounds=Rect(0, 0 - 1080, 2092) mDisplayRotation=ROTATION_180 mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_180}}
        try {
            Display display = getWindowManager().getDefaultDisplay();
            JSONObject json = new JSONObject();

            int rotation = display.getRotation();
            if (rotation == Surface.ROTATION_0)
                json.put("rotation", "ROTATION_0");
            else if (rotation == Surface.ROTATION_90)
                json.put("rotation", "ROTATION_90");
            else if (rotation == Surface.ROTATION_180)
                json.put("rotation", "ROTATION_180");
            else if (rotation == Surface.ROTATION_270)
                json.put("rotation", "ROTATION_270");

            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
                json.put("orientation", "ORIENTATION_PORTRAIT");
            else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
                json.put("orientation", "ORIENTATION_LANDSCAPE");
            else
                json.put("orientation", "ORIENTATION_UNDEFINED");

            DisplayMetrics screenMetrics = new DisplayMetrics();
            display.getMetrics(screenMetrics);
            json.put("screenWidth", screenMetrics.widthPixels);
            json.put("screenHeight", screenMetrics.heightPixels);

            Rect r = new Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
            json.put("visibleFrameLeft", r.left);
            json.put("visibleFrameRight", r.right);
            json.put("visibleFrameTop", r.top);
            json.put("visibleFrameBottom", r.bottom);

		    // This will be sent to the C# layer in Unity, and can be received by the gameObject "ConfigurationManager"
            mUnityPlayer.UnitySendMessage("ConfigurationManager", "onConfigurationChanged", json.toString());
        } catch (JSONException e) {
        }  
    }

    protected void HandleFoldingFeatures(FoldingFeature foldingFeature) {        
         try {
            JSONObject json = new JSONObject();
            if (foldingFeature != null) {
                if (foldingFeature.getOrientation() == FoldingFeature.Orientation.HORIZONTAL) {
                    json.put("orientation", "HINGE_ORIENTATION_HORIZONTAL");
                } else {
                    json.put("orientation", "HINGE_ORIENTATION_VERTICAL");
                }

                if (foldingFeature.getState() == FoldingFeature.State.FLAT) {
                    json.put("state", "FLAT");
                } else {
                    json.put("state", "HALF_OPENED");
                }
                            
                json.put("isSeparating", foldingFeature.isSeparating() ? "1" : "0");
                Rect r = foldingFeature.getBounds();
                json.put("boundsLeft", r.left);
                json.put("boundsTop", r.top);
                json.put("boundsRight", r.right);
                json.put("boundsBottom", r.bottom);
            } else {
                json.put("state", "CLOSED");
            }
            Log.d(TAG, "HandleFoldingFeatures: " + json.toString());    
            mUnityPlayer.UnitySendMessage("ConfigurationManager", "onFoldChanged", json.toString());
        } catch (JSONException e) {
            Log.d(TAG, "Exception json");
            throw new RuntimeException(e);
        }
    }
	
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    protected void onLayoutStateChange(WindowLayoutInfo newLayoutInfo){
        Log.d(TAG, "onLayoutStateChange: " + newLayoutInfo.toString());

        lastFoldingFeature = null;
        if (newLayoutInfo.getDisplayFeatures().size() > 0) {
            newLayoutInfo.getDisplayFeatures().forEach(displayFeature -> {
                if(displayFeature instanceof FoldingFeature)
                {   
                    // only set if it's a fold, not other feature type. only works for single-fold devices.
                    FoldingFeature foldingFeature = (FoldingFeature)displayFeature;
                    lastFoldingFeature = foldingFeature;
                    HandleFoldingFeatures(foldingFeature);
                    Log.d(TAG, "Fold changed: " + foldingFeature.toString());

                } else {
                    Log.d(TAG, "Fold changed: [Closed]");
                }
            });
        }  
    }

    Executor runOnUiThreadExecutor()
    {
        return new MyExecutor();
    }
	
    class MyExecutor implements Executor
    {
        Handler handler = new Handler(Looper.getMainLooper());
        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }

    public FoldingFeature getFoldingFeature()
    {
        return lastFoldingFeature;
    }

    class LayoutStateChangeCallback implements Consumer<WindowLayoutInfo> {
        @Override
        public void accept(WindowLayoutInfo newLayoutInfo) {
            // Use newLayoutInfo to update the Layout
            LargeScreenPlayableActivity.this.runOnUiThread( () -> {
               LargeScreenPlayableActivity.this.onLayoutStateChange(newLayoutInfo);
           });
        }
    }
}