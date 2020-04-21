// Copyright (c) Facebook Technologies, LLC and its affiliates. All Rights reserved.
package dev.hihi.virtualmobilevrheadset;

/**
 * When using NativeActivity, we currently need to handle loading of dependent shared libraries
 * manually before a shared library that depends on them is loaded, since there is not currently a
 * way to specify a shared library dependency for NativeActivity via the manifest meta-data.
 *
 * <p>The simplest method for doing so is to subclass NativeActivity with an empty activity that
 * calls System.loadLibrary on the dependent libraries, which is unfortunate when the goal is to
 * write a pure native C/C++ only Android activity.
 *
 * <p>A native-code only solution is to load the dependent libraries dynamically using dlopen().
 * However, there are a few considerations, see:
 * https://groups.google.com/forum/#!msg/android-ndk/l2E2qh17Q6I/wj6s_6HSjaYJ
 *
 * <p>1. Only call dlopen() if you're sure it will succeed as the bionic dynamic linker will
 * remember if dlopen failed and will not re-try a dlopen on the same lib a second time.
 *
 * <p>2. Must remember what libraries have already been loaded to avoid infinitely looping when
 * libraries have circular dependencies.
 */

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

public class VrActivity extends android.app.NativeActivity {
  static {
    System.loadLibrary("vrapi");
    System.loadLibrary("vrcinema");
  }

  public static final String TAG = "VrCinema";

  public static native void nativeSetVideoSize(long appPtr, int width, int height);

  public static native SurfaceTexture nativePrepareNewVideo(long appPtr);

  public static native long nativeSetAppInterface(android.app.NativeActivity act);

  SurfaceTexture movieTexture = null;
  Surface movieSurface = null;
  Long appPtr = 0L;

  MirrorEngine mMirrorEngine = new MirrorEngine();
  TouchCallback mTouchCallback = new TouchCallback();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate");

    super.onCreate(savedInstanceState);
    appPtr = nativeSetAppInterface(this);
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy");

    mMirrorEngine.stopClient();

    super.onDestroy();
  }

  @Override
  protected void onPause() {
    Log.d(TAG, "onPause()");
    mMirrorEngine.stopClient();
    super.onPause();
  }

  private boolean firstTime = true;

  @Override
  protected void onResume() {
    Log.d(TAG, "onResume()");
    if (firstTime) {

      movieTexture = nativePrepareNewVideo(appPtr);
      if (movieTexture == null) {
        Log.w(TAG, "startMovieAfterPermissionGranted - could not create movieTexture ");
        return;
      }
      movieSurface = new Surface(movieTexture);
      firstTime = false;
    }

    tryStartStreaming();
    super.onResume();
  }

  private void tryStartStreaming() {
    SharedPreferences sp = getSharedPreferences(PhoneActivity.SP_NAME, Context.MODE_PRIVATE);
    String ip = sp.getString(PhoneActivity.LAST_IP, null);
    if (TextUtils.isEmpty(ip)) {
      return;
    }

    SurfaceTexture surfaceTexture = movieTexture;
    if (surfaceTexture == null) {
      return;
    }

    mMirrorEngine.startClient(ip, true, new VideoDecoder.OnSizeChangeCallback() {
      @Override
      public void onChange(int width, int height, boolean isRotated) {
          Log.i(TAG, "onChange: " + width + ", " + height);
        nativeSetVideoSize(appPtr, width, height);
      }
    }, movieSurface, mTouchCallback);
  }

  // called from native code for starting movie
  public void startStreaming() {
    tryStartStreaming();
  }

  public void stopStreaming() {
    Log.d(TAG, "pauseMovie()");
    mMirrorEngine.stopClient();
  }

  // called from native code
  public void onTouchScreen(int action, float x, float y) {
    Log.i(TAG, "onTouchScreen: " + action + ", " + x + ", " + y);
    mTouchCallback.dispatchTouchEvent(action, x, y);
  }

  public static class TouchCallback implements MirrorEngine.TouchSurfaceInterface {

    private static final String TAG = "TouchCallback";

    public static class COMMAND {
      public static final int UNKNOWN = 0;
      public static final int GESTURE = 1;
    }

    private MirrorClientInterface mClient;

    @Override
    public void attachCommandClient(MirrorClientInterface client) {
      mClient = client;
    }

    @Override
    public void removeCommandClient() {
      mClient = null;
    }

    public void dispatchTouchEvent(int action, float x, float y) {
      final MirrorClientInterface client = mClient;
      if (client == null) {
        return;
      }

      int realX = (int) (x);
      int realY = (int) (y);

      byte[] bytes = new byte[6];
      bytes[0] = COMMAND.GESTURE;
      bytes[1] = (byte) action;
      bytes[2] = (byte) ((realX >> 8) & 0xff);
      bytes[3] = (byte) ((realX >> 0) & 0xff);
      bytes[4] = (byte) ((realY >> 8) & 0xff);
      bytes[5] = (byte) ((realY >> 0) & 0xff);
      client.sendBuf(bytes, bytes.length);
    }
  }

}
