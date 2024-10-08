package com.github.haocen2004.login_simulation.utils;

import static android.app.Activity.RESULT_OK;
import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.github.haocen2004.login_simulation.data.Constant.BAG_ALTER_NOTIFICATION;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.github.haocen2004.login_simulation.R;
import com.github.haocen2004.login_simulation.activity.MainActivity;
import com.github.haocen2004.login_simulation.data.dialog.ButtonData;
import com.github.haocen2004.login_simulation.data.dialog.DialogData;
import com.github.haocen2004.login_simulation.data.dialog.DialogLiveData;
import com.hjq.window.EasyWindow;
import com.hjq.window.draggable.SpringDraggable;
import com.king.wechat.qrcode.WeChatQRCodeDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FabScanner extends Service {
    private volatile static FabScanner INSTANCE;
    private final String TAG = "FabScanner";
    private final List<EasyWindow<?>> toastInst = new ArrayList<>();
    boolean isScreenCaptureStarted;
    private MediaProjectionManager mProjectionManager = null;
    private MediaProjection sMediaProjection;
    private Activity activity;
    private Logger Log;
    private boolean hasData;
    //    OnImageCaptureScreenListener listener;
    private int mDensity;
    private Display mDisplay;
    private int mWidth;
    private int mHeight;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private Handler mHandler;
    private String[] url;
    private QRScanner qrScanner;
    private int mResultCode;
    private Intent mResultData;
    private boolean needStop;
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private int counter = 0;
    private SharedPreferences pref;
    private boolean showMIUIAlert = true;

    public FabScanner() {
    }

    public FabScanner(Activity activity) {
        this.activity = activity;
        Log = Logger.getLogger(activity);
        mProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        hasData = false;
        isScreenCaptureStarted = false;
        needStop = false;
        pref = getDefaultSharedPreferences(activity);

        new Thread() {
            @Override
            public void run() {
                try {
                    Looper.prepare();
                    mHandler = new Handler();
                    Looper.loop();
                } catch (Exception e) {
                    mHandler = new Handler();
                }
            }
        }.start();
    }

    public static FabScanner getINSTANCE(Activity activity) {
        if (INSTANCE == null) {
            synchronized (FabScanner.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FabScanner(activity);
                }
            }
        }
        return INSTANCE;
    }

    public void setActivityResultLauncher(ActivityResultLauncher<Intent> activityResultLauncher) {
        this.activityResultLauncher = activityResultLauncher;
    }

    public ActivityResultCallback<ActivityResult> getResultApiCallback() {
        return callback -> {
            if (callback.getResultCode() == RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent service = new Intent(activity, FabScanner.class);
                    service.putExtra("code", callback.getResultCode());
                    service.putExtra("data", callback.getData());
//                    service.putExtra("fragment");
                    activity.startForegroundService(service);
                } else {
                    mResultCode = callback.getResultCode();
                    mResultData = callback.getData();
                    sMediaProjection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
                    hasData = true;
                    showAlertScanner();

                }
            }
        };
    }

    public void setQrScanner(QRScanner qrScanner) {
        this.qrScanner = qrScanner;
    }

    public void setsMediaProjection(MediaProjection sMediaProjection) {
        this.sMediaProjection = sMediaProjection;
        hasData = true;
        showAlertScanner();
    }

    public void showAlertScanner() {
        if (showMIUIAlert && Tools.isMIUI(activity)) {
            String alertMsg = "检测到MIUI系统\n如果是MIUI13+需要同时在左上角权限提醒中允许屏幕共享\n否则扫码器将无法正确获取屏幕内容\n\n该内容每次都会出现";
            DialogData dialogData = new DialogData("悬浮窗扫码 - MIUI 额外提醒", alertMsg);
            dialogData.setPositiveButtonData(new ButtonData("我已知晓") {
                @Override
                public void callback(DialogHelper dialogHelper) {
                    showMIUIAlert = false;
                    showAlertScanner();
                    super.callback(dialogHelper);
                }
            });
            DialogLiveData.getINSTANCE().addNewDialog(dialogData);
        } else {
            Logger.d(TAG, "toast: " + toastInst);
            Logger.d(TAG, "count: " + counter);
            if (!hasData) {

                activityResultLauncher.launch(mProjectionManager.createScreenCaptureIntent());

//                fragment.startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQ_PERM_RECORD);
            } else if (toastInst.size() == 0) {
                counter++;
                toastInst.add(createNewToast());
//            Logger.setUseSnackbar(false);
            } else {
                counter++;
                if (counter < 7) {
                    Log.makeToast("你只可以打开一个悬浮窗！");
                } else if (counter < 10) {
                    Log.makeToast("你就只会开悬浮窗吗？");
                } else {
                    toastInst.add(createNewToast());
                }
            }
        }
    }

    private void setActiveDisplay(boolean isActive) {

        for (EasyWindow<?> EasyWindow2 : toastInst) {
            ImageView imageView = EasyWindow2.getContentView().findViewById(R.id.fab_scanner_image);
//            Logger.d(TAG,imageView.getColorFilter().toString());
            if (isActive) {

//                imageView.setImageResource(R.drawable.baseline_photo_camera_24);
                imageView.setColorFilter(Color.RED);
            } else {
                imageView.clearColorFilter();
//                imageView.setImageResource(R.drawable.ic_menu_camera);
            }
        }
    }

    private EasyWindow<?> createNewToast() {

        @SuppressLint("WrongConstant") EasyWindow<?> EasyWindow = new EasyWindow<>(activity.getApplication())
                .setContentView(R.layout.fab_scanner)
                .setGravity(Gravity.END | Gravity.BOTTOM)
                .setYOffset(200)
                .setDraggable(new SpringDraggable())
                .setOnClickListener((toast, view1) -> {
                    if (needStop) {
                        for (EasyWindow<?> EasyWindow1 : toastInst) {
                            EasyWindow1.cancel();
                            EasyWindow1.recycle();
                            toastInst.remove(EasyWindow1);
                        }
                        stopProjection();
                        needStop = false;
                        return;
                    }
                    setActiveDisplay(true);

                    isScreenCaptureStarted = true;
                    WindowManager window = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                    mDisplay = window.getDefaultDisplay();
                    final DisplayMetrics metrics = new DisplayMetrics();
                    mDisplay.getRealMetrics(metrics);
                    mDensity = metrics.densityDpi;
                    mWidth = metrics.widthPixels;
                    mHeight = metrics.heightPixels;

                    //start capture reader
                    mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 5);
                    try {
                        mVirtualDisplay = sMediaProjection.createVirtualDisplay(
                                "ScreenShot",
                                mWidth,
                                mHeight,
                                mDensity,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                mImageReader.getSurface(),
                                null,
                                mHandler);
                    } catch (Exception e) {
                        try {
                            e.printStackTrace();
                            Log.makeToast("悬浮窗进程异常！");
                            for (EasyWindow<?> EasyWindow1 : toastInst) {
                                EasyWindow1.cancel();
                                EasyWindow1.recycle();
                                toastInst.remove(EasyWindow1);
                            }
                            setActiveDisplay(false);
                            needStop = true;
                            stopForeground(true);
                        } catch (Exception ignore) {
                        }
                    }
                    mImageReader.setOnImageAvailableListener(reader -> {

                        if (isScreenCaptureStarted) {

                            Bitmap bitmap = null;
                            Image image;
                            int failedCount = 0;
                            while ((image = mImageReader.acquireLatestImage()) != null) {
                                try {

                                    int width = image.getWidth();
                                    int height = image.getHeight();

                                    final Image.Plane[] planes = image.getPlanes();
                                    final ByteBuffer buffer = planes[0].getBuffer();
                                    int pixelStride = planes[0].getPixelStride();
                                    int rowStride = planes[0].getRowStride();
                                    int rowPadding = rowStride - pixelStride * width;
                                    bitmap = Bitmap.createBitmap(
                                            width + rowPadding / pixelStride
                                            , height, Bitmap.Config.ARGB_8888);
                                    bitmap.copyPixelsFromBuffer(buffer);
                                    Bitmap.createBitmap(bitmap, 0, 0, width, height);
                                    if (pref.getBoolean("fab_save_img", false)) {
                                        try {
                                            String path = activity.getExternalFilesDir(null) + "/screenshot/";
                                            File dir = new File(path);
                                            if (!dir.exists()) {
                                                dir.mkdirs();
                                            }
                                            File file = new File(path + System.currentTimeMillis() + ".jpg");
                                            FileOutputStream out = new FileOutputStream(file);
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                                            out.flush();
                                            out.close();
                                            if (file.length() < 100000) {
                                                file.delete();
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    List<String> urls = WeChatQRCodeDetector.detectAndDecode(bitmap);
                                    url = urls.toArray(new String[0]);
                                    if (urls.size() < 1) {
                                        failedCount++;
                                        if (pref.getBoolean("capture_continue_before_result", false)) {
                                            if (!pref.getBoolean("keep_capture_no_cooling_down", false)) {
                                                try {
                                                    Thread.sleep(500);
                                                } catch (Exception ignore) {
                                                }
                                            }
                                            continue;
                                        }
//                                        continue;
                                    }
                                    Logger.d(TAG + failedCount, urls.toString());
                                    if (qrScanner.parseUrl(url)) {
                                        Toast.makeText(activity, "扫码成功\n处理中....", Toast.LENGTH_SHORT).show();
//                                        Logger.setFabMode(true);
                                        qrScanner.start();
                                        if (!PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("keep_capture", false)) {
                                            stopProjection();
                                            needStop = true;
                                            for (EasyWindow<?> EasyWindow1 : toastInst) {
                                                EasyWindow1.cancel();
                                                EasyWindow1.recycle();
                                                toastInst.remove(EasyWindow1);
                                            }
                                        } else {
                                            Logger.d(TAG, "keep capture is true,continue.");
                                            isScreenCaptureStarted = false;
                                            setActiveDisplay(false);
                                        }
                                    } else {
//                                            Log.makeToast("未找到二维码");  toast 由 qrScanner 发出
                                        isScreenCaptureStarted = false;
                                        setActiveDisplay(false);
                                    }
                                    try {
                                        mVirtualDisplay.release();
                                        mVirtualDisplay = null;
                                    } catch (Exception ignore) {
                                        mVirtualDisplay = null;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.makeToast("fab未找到二维码");
                                    isScreenCaptureStarted = false;
                                    setActiveDisplay(false);
                                } finally {

                                    if (null != bitmap) {
                                        bitmap.recycle();
                                    }
                                    image.close();
                                }
                            }
                        }
                    }, mHandler);
                    if (sMediaProjection != null) {
                        sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
                    }
                });
        EasyWindow.show();
        return EasyWindow;
    }

    public void stopProjection() {
        try {
            stopForeground(true);
        } catch (Exception ignore) {
        }
        isScreenCaptureStarted = false;
        setActiveDisplay(false);
        mHandler.post(() -> {
            if (sMediaProjection != null) {
                Logger.d(TAG, "stopProjection");
                sMediaProjection.stop();
                sMediaProjection = null;
            }
            if (toastInst.size() > 0) {
                for (EasyWindow<?> EasyWindow : toastInst) {
                    while (EasyWindow.isShowing()) {
                        EasyWindow.cancel();
                        Logger.d(TAG, EasyWindow.toString());
                        EasyWindow.recycle();
                        Logger.d(TAG, "cancelled");
                    }
                    Logger.d(TAG, "Toast stopped");
                }
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        mResultCode = intent.getIntExtra("code", -1);
        mResultData = intent.getParcelableExtra("data");
        if (mProjectionManager == null) {
            mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        sMediaProjection = mProjectionManager.getMediaProjection(mResultCode, Objects.requireNonNull(mResultData));

        FabScanner.getINSTANCE(null).setsMediaProjection(sMediaProjection);
        return super.onStartCommand(intent, flags, startId);
    }

    @SuppressLint("InlinedApi")
    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());
        Intent nfIntent = new Intent(this, MainActivity.class);
        nfIntent.setAction(Intent.ACTION_MAIN);
        nfIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        nfIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher))
                .setContentTitle("扫码器悬浮窗扫码后台进程")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText("安卓10及以上适配所需")
                .setWhen(System.currentTimeMillis());

        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder.setChannelId("scanner_alert_channel");
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("scanner_alert_channel", "扫码器悬浮窗后台进程", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build();
        notification.defaults = Notification.DEFAULT_SOUND;
        startForeground(BAG_ALTER_NOTIFICATION, notification);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (toastInst.size() > 0) {
            for (EasyWindow<?> EasyWindow : toastInst) {
                EasyWindow.cancel();
                EasyWindow.recycle();
            }
        }
        stopForeground(true);
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mHandler.post(() -> {
                if (mVirtualDisplay != null) {
                    mVirtualDisplay.release();
                }
                if (mImageReader != null) {
                    mImageReader.setOnImageAvailableListener(null, null);
                }
                sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                if (toastInst.size() > 0) {
                    for (EasyWindow<?> EasyWindow : toastInst) {
                        EasyWindow.cancel();
                        EasyWindow.recycle();
                    }
                }
            });
        }
    }
}
