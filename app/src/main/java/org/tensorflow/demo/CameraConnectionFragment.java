/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.tensorflow.demo.action.Send;
import org.tensorflow.demo.bluetooth.BluetoothService;
import org.tensorflow.demo.env.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraConnectionFragment extends Fragment {
  private static final Logger LOGGER = new Logger();

  /**
   * The camera preview size will be chosen to be the smallest frame by pixel size capable of
   * containing a DESIRED_SIZE x DESIRED_SIZE square.
   */
  private static final int MINIMUM_PREVIEW_SIZE = 320;

  private RecognitionScoreView scoreView;

  /**
   * Conversion from screen rotation to JPEG orientation.
   */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
  private static final String FRAGMENT_DIALOG = "dialog";



  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  /**
   * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
   * {@link TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                    final SurfaceTexture texture, final int width, final int height) {
              openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    final SurfaceTexture texture, final int width, final int height) {
              configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
            }
          };

  /**
   * ID of the current {@link CameraDevice}.
   */
  private String cameraId;

  /**
   * An {@link AutoFitTextureView} for camera preview.
   */
  private AutoFitTextureView textureView;

  /**
   * A {@link CameraCaptureSession } for camera preview.
   */
  private CameraCaptureSession captureSession;

  /**
   * A reference to the opened {@link CameraDevice}.
   */
  private CameraDevice cameraDevice;

  /**
   * The rotation in degrees of the camera sensor from the display.
   */
  private Integer sensorOrientation;

  /**
   * The {@link android.util.Size} of camera preview.
   */
  private Size previewSize;


  private Send send;
  public static final int FIND_PPBALL =0;
  public static final int FIND_WALLET =1;
  public static final int FIND_LIKEPPBALL = 2;
  public static final int FIND_OBSTACLE = 3;
  public static final int FIND_GLASS=4;


  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
  public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
  public static final int WRITE = 0x40;
  private static final char[] hexArray = { 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 97, 98, 99, 100, 101, 102 };
  private String mDeviceName;
  private String mDeviceAddress;

  private byte mKByte = 0x32;
  private byte mBByte = 0x32;
  private byte mVByte = 0x00;
  private byte mDByte = 0x00;
  private BluetoothAdapter mBluetoothAdapter = null;
  private BluetoothDevice device = null;
  private static BluetoothService mBluetoothService = null;
  private byte[] mReadBuffer = new byte[1024];
  private final byte mHead = 0x10;
  private byte[] mWriteByte = new byte[7];
  public static Thread mCThread;
  public static boolean SendPPBall = false;
  private int start_num =0;
  /**
   * {@link android.hardware.camera2.CameraDevice.StateCallback}
   * is called when {@link CameraDevice} changes its state.
   */
  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice cd) {
              // This method is called when the camera is opened.  We start camera preview here.
              cameraOpenCloseLock.release();
              cameraDevice = cd;
              createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(final CameraDevice cd) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
            }

            @Override
            public void onError(final CameraDevice cd, final int error) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
              final Activity activity = getActivity();
              if (null != activity) {
                activity.finish();
              }
            }
          };

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread backgroundThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler backgroundHandler;

  /**
   * An additional thread for running inference so as not to block the camera.
   */
  private HandlerThread inferenceThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler inferenceHandler;

  /**
   * An {@link ImageReader} that handles preview frame capture.
   */
  private ImageReader previewReader;

  /**
   * {@link android.hardware.camera2.CaptureRequest.Builder} for the camera preview
   */
  private CaptureRequest.Builder previewRequestBuilder;

  /**
   * {@link CaptureRequest} generated by {@link #previewRequestBuilder}
   */
  private CaptureRequest previewRequest;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private final Semaphore cameraOpenCloseLock = new Semaphore(1);

  /**
   * Shows a {@link Toast} on the UI thread.
   *
   * @param text The message to show
   */
  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
              });
    }
  }

  /**
   * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
   * width and height are at least as large as the respective requested values, and whose aspect
   * ratio matches with the specified value.
   *
   * @param choices     The list of sizes that the camera supports for the intended output class
   * @param width       The minimum desired width
   * @param height      The minimum desired height
   * @param aspectRatio The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  private static Size chooseOptimalSize(
          final Size[] choices, final int width, final int height, final Size aspectRatio) {
    // Collect the supported resolutions that are at least as big as the preview Surface
    final List<Size> bigEnough = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
        LOGGER.i("Adding size: " + option.getWidth() + "x" + option.getHeight());
        bigEnough.add(option);
      } else {
        LOGGER.i("Not adding size: " + option.getWidth() + "x" + option.getHeight());
      }
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  public static CameraConnectionFragment newInstance() {
    return new CameraConnectionFragment();
  }

  @Override
  public void onCreate(final Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    final Intent intent = getActivity().getIntent();
    mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
    mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
    mWriteByte[0] = (byte)0xEF;
    mWriteByte[2] = mKByte;
    mWriteByte[3] = mBByte;
    mWriteByte[4] = 0x00;
    mWriteByte[5] = 0x00;
    mWriteByte[6] = (byte)0xFE;
    mWriteByte[0] = (byte)0xFE;
    start_num=0;
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
    // mBluetoothService = new BluetoothService(this, mBluetoothAdapter, mReadHandler);
    mBluetoothService = new BluetoothService(getActivity(), mBluetoothAdapter, mReadHandler);
    mBluetoothService.mCHandler = mConnectHandler;
    mBluetoothService.connect(device);
  }

  @Override
  public View onCreateView(
          final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camera_connection_fragment, container, false);
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    scoreView = (RecognitionScoreView) view.findViewById(R.id.results);
  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    IntentFilter ReadIntentFilter = new IntentFilter();
    ReadIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
    getActivity().registerReceiver(mReadReceiver, ReadIntentFilter);
    Log.d("receiver", "ok");
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  /**
   * Sets up member variables related to camera.
   *
   * @param width  The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  private void setUpCameraOutputs(final int width, final int height) {
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // For still image captures, we use the largest available size.
        final Size largest =
                Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CompareSizesByArea());

        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        previewSize =
                chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
        } else {
          textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }

        CameraConnectionFragment.this.cameraId = cameraId;
        return;
      }
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
              .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  /**
   * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
   */
  private void openCamera(final int width, final int height) {
    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  /**
   * Closes the current {@link CameraDevice}.
   */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /**
   * Starts a background thread and its {@link Handler}.
   */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("ImageListener");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());

    inferenceThread = new HandlerThread("InferenceThread");
    inferenceThread.start();
    inferenceHandler = new Handler(inferenceThread.getLooper())  {
        @Override
        public void handleMessage(Message msg){
            send = new Send();
            switch (msg.what){
                  case FIND_OBSTACLE:
                    Toast toast4=Toast.makeText(getActivity(), "识别到障碍物", Toast.LENGTH_SHORT);
                    toast4.show();
                    mWriteByte[1] = 0x41;//forward
                    mBluetoothService.write(mWriteByte);
                    break;
                  case FIND_PPBALL:
                    Toast toast1=Toast.makeText(getActivity(), "识别到乒乓球", Toast.LENGTH_SHORT);
                      mWriteByte[1] = 0x44;//right
                     mBluetoothService.write(mWriteByte);
                      toast1.show();
                        break;
                    case FIND_LIKEPPBALL:
                      Toast toast2=Toast.makeText(getActivity(), "识别到疑似乒乓球", Toast.LENGTH_SHORT);
                      mWriteByte[1] = 0x43;//left
                      mBluetoothService.write(mWriteByte);
                      toast2.show();
                        break;
                    case FIND_WALLET:
                      send.sendPic(getActivity(), "wallet", "wallet");
                      Toast toast3=Toast.makeText(getActivity(), "识别到贵重物品", Toast.LENGTH_SHORT);
                      toast3.show();
                      mWriteByte[1] = 0x42;//back
                      mBluetoothService.write(mWriteByte);
                      break;
                    default:break;
                }


                super.handleMessage(msg);
            }
        };
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    inferenceThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;

      inferenceThread.join();
      inferenceThread = null;
      inferenceThread = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  private final TensorflowImageListener tfPreviewListener = new TensorflowImageListener();

  private final CameraCaptureSession.CaptureCallback captureCallback =
          new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureProgressed(
                    final CameraCaptureSession session,
                    final CaptureRequest request,
                    final CaptureResult partialResult) {}

            @Override
            public void onCaptureCompleted(
                    final CameraCaptureSession session,
                    final CaptureRequest request,
                    final TotalCaptureResult result) {}
          };

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  private void createCameraPreviewSession() {
    try {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      final Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

      // Create the reader for the preview frames.
      previewReader =
              ImageReader.newInstance(
                      previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(tfPreviewListener, backgroundHandler);

      previewRequestBuilder.addTarget(previewReader.getSurface());

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice.createCaptureSession(
              Arrays.asList(surface, previewReader.getSurface()),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                  // The camera is already closed
                  if (null == cameraDevice) {
                    return;
                  }

                  // When the session is ready, we start displaying the preview.
                  captureSession = cameraCaptureSession;
                  try {
                    // Auto focus should be continuous for camera preview.
                    previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Flash is automatically enabled when necessary.
                    previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    // Finally, we start displaying the camera preview.
                    previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(
                            previewRequest, captureCallback, backgroundHandler);
                  } catch (final CameraAccessException e) {
                    LOGGER.e(e, "Exception!");
                  }
                }

                @Override
                public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                  showToast("Failed");
                }
              },
              null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }

    LOGGER.i("Getting assets.");
    tfPreviewListener.initialize(
            getActivity().getAssets(), scoreView, inferenceHandler, sensorOrientation);
    LOGGER.i("Tensorflow initialized.");
  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
   * This method should be called after the camera preview size is determined in
   * setUpCameraOutputs and also the size of `mTextureView` is fixed.
   *
   * @param viewWidth  The width of `mTextureView`
   * @param viewHeight The height of `mTextureView`
   */
  private void configureTransform(final int viewWidth, final int viewHeight) {
    final Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    final Matrix matrix = new Matrix();
    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    final float centerX = viewRect.centerX();
    final float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      final float scale =
              Math.max(
                      (float) viewHeight / previewSize.getHeight(),
                      (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }

  /**
   * Compares two {@code Size}s based on their areas.
   */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /**
   * Shows an error message dialog.
   */
  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(final String message) {
      final ErrorDialog dialog = new ErrorDialog();
      final Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
              .setMessage(getArguments().getString(ARG_MESSAGE))
              .setPositiveButton(
                      android.R.string.ok,
                      new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialogInterface, final int i) {
                          activity.finish();
                        }
                      })
              .create();
    }
  }

  public Handler mConnectHandler = new Handler(){
    @Override
    public void handleMessage(Message msg){
      if(msg.what == BluetoothService.CONNECTION_FAIL){
        Log.d("handle", "fail");
        Toast discToast = Toast.makeText(getActivity(), "connection fail", Toast.LENGTH_SHORT);
        discToast.show();
        getActivity().finish();
      }else if(msg.what == BluetoothService.CONNECTION_SUCCESS){
        Toast connToast = Toast.makeText(getActivity(), "connection success", Toast.LENGTH_SHORT);
        connToast.show();
        Log.d("handle", "success");
        //mCThread = new Thread(new mCycalThread());
        //mCThread.start();
        mWriteByte[1] = 0x41;
        mBluetoothService.write(mWriteByte);
      }
    }
  };



  public Handler mReadHandler = new Handler(){
    @Override
    public void handleMessage(Message msg) {
      mReadBuffer = (byte[]) msg.obj;
      //Toast readToast = Toast.makeText(ButtonControlActivity.this,
      //		hex2string(mReadBuffer).subSequence(0, 14), Toast.LENGTH_SHORT);
      //readToast.show();
      Log.d("READDATA", mReadBuffer.toString());
      if ((mReadBuffer[0] == (byte) 0xEF) && (mReadBuffer[6] == (byte) 0xFE)) {
        if (mReadBuffer[1] == 0x30) {
          //mKByte = mReadBuffer[2];
          //mK.setText(byte2string(mKByte));
          //mBByte = mReadBuffer[3];
          //mB.setText(byte2string(mBByte));
        }
      }
    }
  };


  private Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg){
      if(msg.what == CameraConnectionFragment.WRITE){
        mBluetoothService.write((byte[])msg.obj);
      }
    }
  };

  private class mCycalThread implements Runnable {
    @Override
    public void run() {
      // TODO Auto-generated method stub
      while (true) {
        try {
          Thread.sleep(100);
          Message msg = new Message();
          mWriteByte[0] = (byte)0xEF;
          mWriteByte[2] = mKByte;
          mWriteByte[3] = mBByte;
          mWriteByte[4] = 0x00;
          mWriteByte[5] = 0x00;
          mWriteByte[6] = (byte)0xFE;
          mWriteByte[0] = (byte)0xFE;
          //mWriteByte[3] = mBByte;
          //mWriteByte[4] = mKByte;
          //mWriteByte[6] = (byte)0xEF;
          msg.what = CameraConnectionFragment.WRITE;
          msg.obj = mWriteByte;
          mHandler.sendMessage(msg);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
          //System.out.println("thread error...");
        }
      }
    }
  };


  @Override
  public void onDestroy() {
    super.onDestroy();
    getActivity().unregisterReceiver(mReadReceiver);
    mConnectHandler = null;
    //mReadHandler = null;
    mHandler = null;
    if (mBluetoothService != null){
      mBluetoothService.stop();
    }
    if(mCThread!=null){
      mCThread.interrupt();
    }
  }


  private final BroadcastReceiver mReadReceiver = new BroadcastReceiver()
  {
    @Override
    public void onReceive(Context context, Intent intent) {
      // TODO Auto-generated method stub
      String str = intent.getAction();
      if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(str)){
        Log.d("ACTION", "ACTION_ACL_DISCONNECT_REQUESTED");
        Toast revToast = Toast.makeText(getActivity(), "connection fail", Toast.LENGTH_SHORT);
        revToast.show();
        getActivity().finish();
      }
    }
  };

}