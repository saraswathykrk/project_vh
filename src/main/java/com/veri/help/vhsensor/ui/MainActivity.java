package com.veri.help.vhsensor.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.veri.help.vhsensor.R;
import com.veri.help.vhsensor.camera.AutoFitTextureView;
import com.veri.help.vhsensor.detector.FaceInfo;
import com.veri.help.vhsensor.detector.FacesView;
import com.veri.help.vhsensor.detector.MTCNN;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    static MainActivity currentActivity;

    // region Constants

    private static final String TAG = MainActivity.class.getName();

    private static final int REQUEST_CAMERA_PERMISSION = 42;
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final String ERROR_MESSAGE = "";

    // endregion

    // region Camera Variables

    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private CameraCaptureSession cameraCaptureSession;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private AutoFitTextureView textureView;
    private FacesView facesView;  //added for analysis saras
    private ImageReader imageReader;
    private Size previewSize;
    private String cameraId;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // endregion

    private MTCNN mtcnn;
    private ScriptIntrinsicYuvToRGB script;
    private RenderScript renderScript;

    // region Metrics Variables

    private long detectionTime = 0;
    private long imagereaderCycleCounts = 0;
    private long conversionTime = 0;

    // endregion

    // region Camera Listeners and Callbacks

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
            if (currentActivity != null) {
                currentActivity.finish();
            }
        }
    };

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image currentImage = reader.acquireLatestImage();
            byte[] bytes;
            try {
                long preprocessingStart = System.currentTimeMillis();
                ByteBuffer buffer = currentImage.getPlanes()[0].getBuffer();
                bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
//                byte[] jpegBytes = convertYuvToRgb(bytes, previewSize);
                Bitmap jpegbmp = convertYuvToRgb(bytes, previewSize);
                Log.d(TAG, "preview size" + previewSize + "," + currentImage.getHeight() + "," + currentImage.getWidth());

                Mat imageMat4c = new Mat(currentImage.getWidth(), currentImage.getHeight(), CvType.CV_8UC4);  //commented by saras for testing
                Mat imageMat3c = new Mat(currentImage.getWidth(), currentImage.getHeight(), CvType.CV_8UC3);
                Utils.bitmapToMat(jpegbmp, imageMat4c);  //commented by saras for testing

//                  imageMat4c.put(0, 0, jpegBytes);
                Imgproc.cvtColor(imageMat4c, imageMat3c, Imgproc.COLOR_RGBA2BGR);  //commented by saras for testing

                long preprocessingEnd = System.currentTimeMillis();
                Log.d(TAG, "Preprocessing time - " + (preprocessingEnd - preprocessingStart) + "ms");
                Log.d(TAG, "matrix values" + imageMat3c.cols() + "," + imageMat3c.rows());
                long detectStart = System.currentTimeMillis();
                List<FaceInfo> faces = mtcnn.detectFaces(imageMat3c);
                long detectEnd = System.currentTimeMillis();
                if (faces.size() > 0){
                    facesView = findViewById(R.id.faces_detected);  //added for analysis saras
                    facesView.showFaces(faces, null);
                }
                else {
                    facesView = findViewById(R.id.faces_detected);  //added for analysis saras
                    facesView.showFaces(null, null);
                }
                Log.d(TAG, "Faces detected now - " + faces.size() + " in time - " + (detectEnd - detectStart) + "ms");
                detectionTime += (detectEnd - detectStart);
                conversionTime += (preprocessingEnd - preprocessingStart);
                if (++imagereaderCycleCounts % 10 == 0) {
                    Log.d(TAG, "Average detection time - " + (detectionTime * 1.0 / imagereaderCycleCounts));
                    Log.d(TAG, "Average conversion time - " + (conversionTime * 1.0 / imagereaderCycleCounts));

                }

            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                if (currentImage != null)
                    currentImage.close();
            }
        }
    };

    // endregion

    // region Image Processing

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap convertYuvToRgb(byte[] yuvByteArray, Size dimensions) {
        Log.d(TAG, "Attempting conversion");
        try {
//            Type.Builder yuvType = new Type.Builder(renderScript, Element.U8(renderScript)).setX(yuvByteArray.length);
            Type.Builder yuvType = new Type.Builder(renderScript, Element.U8(renderScript)).setX(dimensions.getWidth()).setY(dimensions.getHeight()).setYuvFormat(ImageFormat.YUV_420_888);
            Allocation input = Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT);
            Type.Builder rgbaType = new Type.Builder(renderScript, Element.RGBA_8888(renderScript)).setX(dimensions.getWidth()).setY(dimensions.getHeight());
            Allocation output = Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT);

            input.copyFrom(yuvByteArray);
            script.setInput(input);
            script.forEach(output);

            Bitmap bmpout = Bitmap.createBitmap(dimensions.getWidth(), dimensions.getHeight(),Bitmap.Config.ARGB_8888);
            Log.d(TAG, "bitmap dimensions" + dimensions.getHeight() + "," + dimensions.getWidth());
            output.copyTo(bmpout);
//            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//            bmpout.compress(Bitmap.CompressFormat.JPEG, 100, stream);
//            byte[] jpegByteArray = stream.toByteArray();
//            bmpout.recycle();
//            Log.d(TAG, "Conversion done");
//            return jpegByteArray;
            Log.d(TAG, "bitmap values" + bmpout.getHeight() + "," + bmpout.getWidth() + "," + bmpout.getByteCount());
            return bmpout;
        } catch (Exception exception) {
            Log.e(TAG, "ALl hope is lost");
            exception.printStackTrace();
        }

        return null;
    }

    // endregion

    static {
        OpenCVLoader.initDebug();
    }

    // region Overridden Activity Methods

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            currentActivity = MainActivity.this;
            textureView = findViewById(R.id.autoFitTextureView);
            facesView = findViewById(R.id.faces_detected);  //added for analysis saras
            mtcnn = new MTCNN(this);

            renderScript = RenderScript.create(this);
            script = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));
        } catch (Exception exception) {
            Log.d(TAG, "Weird flex?");
            exception.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (textureView.isAvailable()) {
            Log.d(TAG,"Texture view widths" + textureView.getWidth() + "," + textureView.getHeight());
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    // endregion

    // region Camera Functions

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
//        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout waiting for camera lock");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null)
                    continue;
                previewSize = map.getOutputSizes(SurfaceTexture.class)[3];
                Log.d(TAG, "Selected Preview - " + previewSize.getWidth() + "x" + previewSize.getHeight());
                this.cameraId = cameraId;

                imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
        } catch (CameraAccessException exception) {
            exception.printStackTrace();
        } catch (NullPointerException exception) {
            exception.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

        } catch (InterruptedException exception) {
            throw new RuntimeException("Interrupted while trying to lock camera closing", exception);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null)
                        return;

                    cameraCaptureSession = session;
                    try {
                        previewRequest = previewRequestBuilder.build();
                        cameraCaptureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // endregion

    // region Permissions

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(R.string.request_camera_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (currentActivity != null)
                                MainActivity.this.finish();
                        }
                    }).create().show();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.request_camera_permission)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MainActivity.this.finish();
                            }
                        })
                        .create();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // endregion
}
