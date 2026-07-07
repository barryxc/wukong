package io.github.barryxc.screenshare.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenShareService extends Service {

    public static final String ACTION_START = "io.github.barryxc.screenshare.demo.action.START";
    public static final String ACTION_STOP = "io.github.barryxc.screenshare.demo.action.STOP";
    public static final String ACTION_STATUS = "io.github.barryxc.screenshare.demo.action.STATUS";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DENSITY_DPI = "density_dpi";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_MESSAGE = "message";

    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "screen_share_demo";
    private static final int TOUCH_MODE_NONE = 0;
    private static final int TOUCH_MODE_DRAG = 1;
    private static final int TOUCH_MODE_SCALE = 2;
    private static final long FRAME_INTERVAL_MS = 66L;
    private static final boolean SECURE_OVERLAY_PREVIEW = false;
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private ImageReader imageReader;
    private WindowManager windowManager;
    private FramePreviewView overlayView;
    private WindowManager.LayoutParams overlayParams;
    private int width;
    private int height;
    private int densityDpi;
    private long lastFrameUptimeMs;
    private int touchMode = TOUCH_MODE_NONE;
    private float downRawX;
    private float downRawY;
    private float downPointerDistance;
    private int downOverlayX;
    private int downOverlayY;
    private int downOverlayWidth;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            running = false;
            releaseDisplay();
            releaseImageReader();
            removeOverlayPreview();
            mediaProjection = null;
            surface = null;
            sendStatus(false, "Projection stopped by system");
            stopForegroundCompat();
            stopSelf();
        }
    };

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            handleStart(intent);
        } else if (ACTION_STOP.equals(action)) {
            releaseAll();
            sendStatus(false, "Stopped");
            stopForegroundCompat();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseAll();
        super.onDestroy();
    }

    private void handleStart(Intent intent) {
        startInForeground();
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = getParcelableIntent(intent, EXTRA_RESULT_DATA);
        int nextWidth = intent.getIntExtra(EXTRA_WIDTH, 1);
        int nextHeight = intent.getIntExtra(EXTRA_HEIGHT, 1);
        int nextDensityDpi = intent.getIntExtra(
                EXTRA_DENSITY_DPI,
                getResources().getDisplayMetrics().densityDpi);

        if (resultData == null) {
            sendStatus(false, "Invalid projection data");
            stopSelf();
            return;
        }

        releaseAll();
        width = nextWidth;
        height = nextHeight;
        densityDpi = nextDensityDpi;
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            sendStatus(false, "Failed to create MediaProjection");
            stopSelf();
            return;
        }
        mediaProjection.registerCallback(projectionCallback, mainHandler);
        showOverlayPreview();
    }

    private void showOverlayPreview() {
        if (overlayView != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new FramePreviewView(this);
        overlayView.setBackgroundColor(Color.BLACK);
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleOverlayTouch(event);
            }
        });

        int overlayWidth = dp(220);
        int overlayHeight = calculateOverlayHeight(overlayWidth);
        int overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (SECURE_OVERLAY_PREVIEW) {
            overlayFlags |= WindowManager.LayoutParams.FLAG_SECURE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                overlayWidth,
                overlayHeight,
                getOverlayWindowType(),
                overlayFlags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, getDisplayWidth() - overlayWidth - dp(16));
        params.y = dp(96);

        try {
            overlayParams = params;
            windowManager.addView(overlayView, params);
            sendStatus(true, "Waiting for preview frames");
            createImageReader();
        } catch (RuntimeException e) {
            overlayParams = null;
            overlayView = null;
            releaseAll();
            sendStatus(false, "Failed to show overlay: " + e.getClass().getSimpleName());
            stopForegroundCompat();
            stopSelf();
        }
    }

    @SuppressWarnings("deprecation")
    private int getOverlayWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private boolean handleOverlayTouch(MotionEvent event) {
        if (overlayParams == null || overlayView == null || windowManager == null) {
            return true;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchMode = TOUCH_MODE_DRAG;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            downOverlayX = overlayParams.x;
            downOverlayY = overlayParams.y;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            touchMode = TOUCH_MODE_SCALE;
            downPointerDistance = getPointerDistance(event);
            downOverlayWidth = overlayParams.width;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (touchMode == TOUCH_MODE_SCALE && event.getPointerCount() >= 2) {
                float pointerDistance = getPointerDistance(event);
                if (downPointerDistance > 0f) {
                    resizeOverlay(Math.round(downOverlayWidth * pointerDistance / downPointerDistance));
                }
                return true;
            }
            if (touchMode == TOUCH_MODE_DRAG) {
                int nextX = downOverlayX + Math.round(event.getRawX() - downRawX);
                int nextY = downOverlayY + Math.round(event.getRawY() - downRawY);
                moveOverlay(nextX, nextY);
                return true;
            }
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            touchMode = TOUCH_MODE_DRAG;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            downOverlayX = overlayParams.x;
            downOverlayY = overlayParams.y;
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchMode = TOUCH_MODE_NONE;
            return true;
        }
        return true;
    }

    private void moveOverlay(int nextX, int nextY) {
        overlayParams.x = clamp(nextX, 0, Math.max(0, getDisplayWidth() - overlayParams.width));
        overlayParams.y = clamp(nextY, 0, Math.max(0, getDisplayHeight() - overlayParams.height));
        updateOverlayLayout();
    }

    private void resizeOverlay(int nextWidth) {
        int widthBefore = overlayParams.width;
        int heightBefore = overlayParams.height;
        int centerX = overlayParams.x + widthBefore / 2;
        int centerY = overlayParams.y + heightBefore / 2;
        int clampedWidth = clamp(nextWidth, dp(140), getMaxOverlayWidth());
        int clampedHeight = calculateOverlayHeight(clampedWidth);

        overlayParams.width = clampedWidth;
        overlayParams.height = clampedHeight;
        overlayParams.x = clamp(centerX - clampedWidth / 2, 0, Math.max(0, getDisplayWidth() - clampedWidth));
        overlayParams.y = clamp(centerY - clampedHeight / 2, 0, Math.max(0, getDisplayHeight() - clampedHeight));
        updateOverlayLayout();
    }

    private void updateOverlayLayout() {
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
        } catch (RuntimeException ignored) {
            // The overlay can be removed while projection is being stopped.
        }
    }

    private float getPointerDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int calculateOverlayHeight(int overlayWidth) {
        return Math.max(dp(90), Math.round((float) overlayWidth * height / width));
    }

    private int getMaxOverlayWidth() {
        int maxWidthByScreen = Math.max(dp(160), getDisplayWidth() - dp(32));
        int maxWidthByHeight = Math.max(dp(160), Math.round((getDisplayHeight() - dp(48)) * (float) width / height));
        return Math.min(maxWidthByScreen, maxWidthByHeight);
    }

    private int getDisplayWidth() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }

    private int getDisplayHeight() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.heightPixels;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void createImageReader() {
        releaseImageReader();
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                updatePreviewFrame(reader);
            }
        }, mainHandler);
        surface = imageReader.getSurface();
        createVirtualDisplay();
    }

    private void updatePreviewFrame(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || overlayView == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastFrameUptimeMs < FRAME_INTERVAL_MS) {
                return;
            }
            lastFrameUptimeMs = now;
            overlayView.updateFrame(image);
        } catch (RuntimeException ignored) {
            // A frame can arrive while projection is being stopped.
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void createVirtualDisplay() {
        if (mediaProjection == null || surface == null || !surface.isValid()) {
            sendStatus(false, "Preview surface is unavailable");
            return;
        }
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenShareDemo",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    mainHandler);
            sendStatus(true, "Sharing screen");
        } catch (RuntimeException e) {
            releaseAll();
            sendStatus(false, "Failed to start projection: " + e.getClass().getSimpleName());
            stopForegroundCompat();
            stopSelf();
        }
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Share Demo",
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Intent launchIntent = new Intent(this, ScreenShareActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE
                        : 0);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Screen share is running")
                .setContentText("Tap to return to the demo")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void releaseAll() {
        running = false;
        releaseDisplay();
        releaseImageReader();
        removeOverlayPreview();
        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(projectionCallback);
            } catch (RuntimeException ignored) {
                // Projection may already be stopped by the system.
            }
            try {
                mediaProjection.stop();
            } catch (RuntimeException ignored) {
                // Keep cleanup idempotent.
            }
            mediaProjection = null;
        }
        surface = null;
    }

    private void releaseDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    private void releaseImageReader() {
        surface = null;
        lastFrameUptimeMs = 0L;
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
    }

    private void removeOverlayPreview() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The overlay may already be detached during projection shutdown.
            }
        }
        if (overlayView != null) {
            overlayView.release();
        }
        overlayView = null;
        windowManager = null;
        overlayParams = null;
        touchMode = TOUCH_MODE_NONE;
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void sendStatus(boolean running, String message) {
        ScreenShareService.running = running;
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_RUNNING, running);
        intent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(intent);
    }

    private Intent getParcelableIntent(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Intent.class);
        }
        return intent.getParcelableExtra(key);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FramePreviewView extends View {

        private final Rect sourceRect = new Rect();
        private final RectF destinationRect = new RectF();
        private Bitmap frameBitmap;
        private int frameWidth;
        private int frameHeight;

        FramePreviewView(Context context) {
            super(context);
        }

        void updateFrame(Image image) {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                return;
            }
            Image.Plane plane = planes[0];
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            if (pixelStride <= 0 || rowStride <= 0) {
                return;
            }
            int bitmapWidth = rowStride / pixelStride;
            int bitmapHeight = image.getHeight();
            if (frameBitmap == null
                    || frameBitmap.getWidth() != bitmapWidth
                    || frameBitmap.getHeight() != bitmapHeight) {
                release();
                frameBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            }
            ByteBuffer buffer = plane.getBuffer();
            buffer.rewind();
            frameBitmap.copyPixelsFromBuffer(buffer);
            frameWidth = image.getWidth();
            frameHeight = image.getHeight();
            invalidate();
        }

        void release() {
            if (frameBitmap != null) {
                frameBitmap.recycle();
                frameBitmap = null;
            }
            frameWidth = 0;
            frameHeight = 0;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            if (frameBitmap == null || frameWidth <= 0 || frameHeight <= 0) {
                return;
            }
            sourceRect.set(0, 0, frameWidth, frameHeight);
            float scale = Math.min(
                    (float) getWidth() / frameWidth,
                    (float) getHeight() / frameHeight);
            float drawWidth = frameWidth * scale;
            float drawHeight = frameHeight * scale;
            float left = (getWidth() - drawWidth) / 2f;
            float top = (getHeight() - drawHeight) / 2f;
            destinationRect.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(frameBitmap, sourceRect, destinationRect, null);
        }
    }
}
