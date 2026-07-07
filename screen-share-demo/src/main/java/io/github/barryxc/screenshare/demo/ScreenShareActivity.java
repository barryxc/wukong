package io.github.barryxc.screenshare.demo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ScreenShareActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    private static final int REQUEST_OVERLAY_PERMISSION = 1003;
    private static final int MAX_CAPTURE_SIZE = 720;

    private MediaProjectionManager projectionManager;
    private TextView statusView;
    private Button startButton;
    private Button stopButton;
    private boolean sharing;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ScreenShareService.ACTION_STATUS.equals(intent.getAction())) {
                return;
            }
            sharing = intent.getBooleanExtra(ScreenShareService.EXTRA_RUNNING, false);
            String message = intent.getStringExtra(ScreenShareService.EXTRA_MESSAGE);
            updateStatus(message == null ? "Idle" : message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        buildContentView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ScreenShareService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        sharing = ScreenShareService.isRunning();
        updateStatus(sharing ? "Sharing screen" : "Idle");
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            stopSharing();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen share permission denied", Toast.LENGTH_SHORT).show();
            updateStatus("Permission denied");
            return;
        }
        startSharing(resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            requestScreenCapture();
        }
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.WHITE);

        TextView titleView = new TextView(this);
        titleView.setText("Screen Share Demo");
        titleView.setTextColor(Color.rgb(32, 33, 36));
        titleView.setTextSize(22);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(95, 99, 104));
        statusView.setTextSize(15);
        statusView.setText("Idle");
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView previewHintView = new TextView(this);
        previewHintView.setText("Preview is shown in a floating window");
        previewHintView.setTextColor(Color.rgb(95, 99, 104));
        previewHintView.setTextSize(15);
        previewHintView.setGravity(Gravity.CENTER);
        previewHintView.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(previewHintView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(0, dp(16), 0, 0);

        startButton = new Button(this);
        startButton.setText("Start");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prepareStart();
            }
        });
        buttonContainer.addView(startButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSharing();
            }
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        stopParams.leftMargin = dp(12);
        buttonContainer.addView(stopButton, stopParams);

        root.addView(buttonContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        updateStatus("Idle");
    }

    private void prepareStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            return;
        }
        if (!canDrawOverlay()) {
            requestOverlayPermission();
            return;
        }
        requestScreenCapture();
    }

    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Please allow overlay permission", Toast.LENGTH_SHORT).show();
        updateStatus("Overlay permission required");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    private void requestScreenCapture() {
        updateStatus("Waiting for system permission");
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            captureIntent = projectionManager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            captureIntent = projectionManager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    private void startSharing(int resultCode, Intent data) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int[] captureSize = calculateCaptureSize(metrics);

        Intent serviceIntent = new Intent(this, ScreenShareService.class);
        serviceIntent.setAction(ScreenShareService.ACTION_START);
        serviceIntent.putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(ScreenShareService.EXTRA_RESULT_DATA, data);
        serviceIntent.putExtra(ScreenShareService.EXTRA_WIDTH, captureSize[0]);
        serviceIntent.putExtra(ScreenShareService.EXTRA_HEIGHT, captureSize[1]);
        serviceIntent.putExtra(ScreenShareService.EXTRA_DENSITY_DPI, metrics.densityDpi);
        startForegroundServiceCompat(serviceIntent);
        sharing = true;
        updateStatus("Starting");
    }

    private int[] calculateCaptureSize(DisplayMetrics metrics) {
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        float scale = Math.min(1f, (float) MAX_CAPTURE_SIZE / Math.max(width, height));
        return new int[]{
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }

    private void stopSharing() {
        Intent serviceIntent = new Intent(this, ScreenShareService.class);
        serviceIntent.setAction(ScreenShareService.ACTION_STOP);
        startService(serviceIntent);
        sharing = false;
        updateStatus("Stopped");
    }

    private void startForegroundServiceCompat(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateStatus(String status) {
        statusView.setText(status);
        startButton.setEnabled(!sharing);
        stopButton.setEnabled(sharing);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
