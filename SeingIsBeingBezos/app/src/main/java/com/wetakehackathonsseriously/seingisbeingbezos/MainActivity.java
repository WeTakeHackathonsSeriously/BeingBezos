package com.wetakehackathonsseriously.seingisbeingbezos;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private TextureView cameraPreview;
    private CameraDevice camera;
    private CameraCaptureSession cameraCaptureSession;
    private String cameraID = "";
    private Size imageDimension;
    private Surface surface;
    private CameraManager cameraManager;
    private ImageReader snapshotReader;
    private Size imageSize;
//    private ImageLabellingSurfaceView imageLabellingSurfaceView;
    private Paint paint;
    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView;
    private Paint textPaint;
    private Paint boxPaint;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreview = (TextureView)findViewById(R.id.textureView);
        cameraPreview.setSurfaceTextureListener(textureListener);

        surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        surfaceView.setZOrderOnTop(true);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setTextSize(30);
        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cm) {
            camera = cm;
            configureCamera();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            camera = null;
        }
    };

    private void configureCamera() {
        try {
            Size[] sizes = cameraManager.getCameraCharacteristics(cameraID).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            imageSize = sizes[0];
        } catch (CameraAccessException e) {}
        // prepare list of surfaces to be used in capture requests
        List<Surface> sfl = new ArrayList<Surface>();
        SurfaceTexture texture = cameraPreview.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
        surface = new Surface(texture);
        sfl.add(surface); // surface for viewfinder preview
        snapshotReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 10);
        ImageReader.OnImageAvailableListener snapshotListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                image = snapshotReader.acquireLatestImage();
                Log.i("Camera", "Got image " + String.valueOf(image.getHeight()));
                new BackendConnector().execute(image);

            }
        };
        snapshotReader.setOnImageAvailableListener(snapshotListener, null);
        sfl.add(snapshotReader.getSurface());
        // configure camera with all the surfaces to be ever used
        try {
            camera.createCaptureSession(sfl,
                    new CaptureSessionListener(), null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class CaptureSessionListener extends
            CameraCaptureSession.StateCallback {
        @Override
        public void onConfigureFailed(final CameraCaptureSession session) {
            Log.d("Camera", "CaptureSessionConfigure failed");
        }

        @Override
        public void onConfigured(final CameraCaptureSession session) {
            Log.d("Camera", "CaptureSessionConfigure onConfigured");
            cameraCaptureSession = session;

            try {
                CaptureRequest.Builder previewRequestBuilder = camera
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(surface);
                cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(),
                        null, null);
            } catch (CameraAccessException e) {
                Log.d("Camera", "setting up preview failed");
                e.printStackTrace();
            }

            final Handler handler = new Handler();
            final int delay = 500;

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(this, delay);
                    CaptureRequest.Builder snapshotCaptureRequest = null;
                    try {
                        snapshotCaptureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        snapshotCaptureRequest.addTarget(snapshotReader.getSurface());
                       cameraCaptureSession.capture(snapshotCaptureRequest.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }, delay);
        }
    }

    private void openCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e("Camera", "is camera open");
        try {
            cameraID = cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
                return;
            }
            cameraManager.openCamera(cameraID, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e("Camera", "openCamera X");
    }

    public void drawLabelImageData(Map<String, Object> apiData) {
        synchronized (this) {
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            float left, right, top, bottom;
            left = (float)apiData.get("left") * surfaceView.getWidth();
            top = (float)apiData.get("top") * surfaceView.getHeight();
            right = (float)apiData.get("right") * surfaceView.getWidth();
            bottom = (float)apiData.get("bottom") * surfaceView.getHeight();


            canvas.drawRect(left, top, right, bottom, paint);
            String text = (String)apiData.get("label") + ": £" + String.valueOf((Double)apiData.get("rel"));
            Rect textBounds = new Rect();
            textPaint.getTextBounds(text, 0, text.length() - 1, textBounds);
            if ((right - left) < (textBounds.right - textBounds.left)) {
                float currLen = right - left;
                right += textBounds.right - currLen;
            }
            canvas.drawRect(left, bottom, right, bottom + textPaint.getTextSize() + 5, boxPaint);
            canvas.drawText(text, left + 3, bottom+textPaint.getTextSize() - 5, textPaint);
            surfaceHolder.unlockCanvasAndPost(canvas);

        }
    }

    public class BackendConnector extends AsyncTask<Image, Void, Map<String, Object>> {

        @Override
        protected Map<String, Object> doInBackground(Image... images) {
            Image image = images[0];
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            Log.i("Backend", String.valueOf(bytes.length));
            byte[] imageDataB64Encoded = android.util.Base64.encode(bytes, Base64.DEFAULT);
            Log.i("Backend", String.valueOf(imageDataB64Encoded.length));
            Map<String, Object> returnData = new HashMap<>();

            try {
                URL url = new URL("https://being-bezos.herokuapp.com/api/upload/");
                HttpsURLConnection client = (HttpsURLConnection) url.openConnection();
                client.setRequestMethod("POST");
                client.setDoOutput(true);
                client.setDoInput(true);

                DataOutputStream dos = new DataOutputStream(client.getOutputStream());
                String text = "image=";
                dos.writeBytes("image=");
                dos.write(imageDataB64Encoded);
                dos.flush();
                dos.close();


                InputStreamReader inputStreamReader = new InputStreamReader(client.getInputStream());
//                BufferedReader reader = new BufferedReader(inputStreamReader);
//                StringBuilder sb = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null)
//                {
//                    sb.append(line + "\n");
//                }
//                // Response from server after login process will be stored in response variable.
//                Log.i("Backend", sb.toString());

                JsonReader jsonReader = new JsonReader(inputStreamReader);
//                jsonReader.beginObject();
//                returnData.put("name", jsonReader.nextName());
                jsonReader.beginArray();
                jsonReader.beginObject();
                jsonReader.nextName();
                jsonReader.beginArray();
                returnData.put("left", (float)jsonReader.nextDouble());
                returnData.put("top", (float)jsonReader.nextDouble());
                returnData.put("right", (float)jsonReader.nextDouble());
                returnData.put("bottom", (float)jsonReader.nextDouble());
                jsonReader.endArray();
                jsonReader.nextName();
                returnData.put("label", jsonReader.nextString());
                jsonReader.nextName();
                returnData.put("rel", jsonReader.nextDouble());
                jsonReader.close();
                inputStreamReader.close();

            } catch (Exception e) {
                e.printStackTrace();
                image.close();
                return null;
            }

            image.close();
            return returnData;
        }

        @Override
        protected void onPostExecute(Map<String, Object> apiData) {
            super.onPostExecute(apiData);
            drawLabelImageData(apiData);
        }
    }
}
