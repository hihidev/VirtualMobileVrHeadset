package dev.hihi.virtualmobilevrheadset;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;

public class MyTextureView extends TextureView implements MirrorEngine.TouchSurfaceInterface {

    private static final String TAG = "MyTextureView";

    public static class COMMAND {
        public static final int UNKNOWN = 0;
        public static final int GESTURE = 1;
    }

    public static class GESTURE {
        public static final int UNKNOWN = 0;
        public static final int ACTION_MOVE = 1;
        public static final int ACTION_UP = 2;
        public static final int ACTION_DOWN = 3;
    }

    private MirrorClientInterface mClient;

    private int mVideoSourceWidth = 0;
    private int mVideoSourceHeight = 0;
    private boolean mIsRotated = false;

    public MyTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MyTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyTextureView(Context context) {
        super(context);
    }

    @Override
    public void attachCommandClient(MirrorClientInterface client) {
        mClient = client;
    }

    @Override
    public void removeCommandClient() {
        mClient = null;
    }

    public void setVideoSourceSize(int width, int height, boolean isRotated) {
        mVideoSourceWidth = width;
        mVideoSourceHeight = height;
        mIsRotated = isRotated;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mVideoSourceWidth == 0 || mVideoSourceHeight == 0) {
            Log.w(TAG, "mVideoSourceWidth or mVideoSourceHeight == 0");
            return super.dispatchTouchEvent(event);
        }
        final MirrorClientInterface client = mClient;
        if (client == null) {
            return super.dispatchTouchEvent(event);
        }
        int action = GESTURE.UNKNOWN;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                action = GESTURE.ACTION_DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
                action = GESTURE.ACTION_MOVE;
                break;
            case MotionEvent.ACTION_UP:
                action = GESTURE.ACTION_UP;
                break;
            default:
                Log.w(TAG, "Unkonwn event action: " + event.getAction());
                return super.dispatchTouchEvent(event);
        }
        float viewWidth = mIsRotated ? getHeight() : getWidth();
        float viewHeight = mIsRotated ? getWidth() : getHeight();

        float x = mIsRotated ? event.getY() : event.getX();
        float y = mIsRotated ? viewHeight - event.getX() : event.getY();

        x = Math.min(x, viewWidth);
        x = Math.max(x, 0);
        y = Math.min(y, viewHeight);
        y = Math.max(y, 0);

        float videoWidth = mIsRotated ? mVideoSourceHeight : mVideoSourceWidth;
        float videoHeight = mIsRotated ? mVideoSourceWidth : mVideoSourceHeight;

        int realX = (int) (x * videoWidth / viewWidth);
        int realY = (int) (y * videoHeight / viewHeight);

        byte[] bytes = new byte[6];
        bytes[0] = COMMAND.GESTURE;
        bytes[1] = (byte) action;
        bytes[2] = (byte) ((realX >> 8) & 0xff);
        bytes[3] = (byte) ((realX >> 0) & 0xff);
        bytes[4] = (byte) ((realY >> 8) & 0xff);
        bytes[5] = (byte) ((realY >> 0) & 0xff);
        client.sendBuf(bytes, bytes.length);
        return true;
    }
}
