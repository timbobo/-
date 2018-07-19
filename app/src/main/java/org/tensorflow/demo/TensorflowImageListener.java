/* Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.demo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;

import junit.framework.Assert;

import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with Tensorflow.
 */
public class TensorflowImageListener  implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private static final boolean SAVE_PREVIEW_BITMAP = false;

  // These are the settings for the original v1 Inception model. If you want to
  // use a model that's been produced from the TensorFlow for Poets codelab,
  // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
  // INPUT_NAME = "Mul:0", and OUTPUT_NAME = "final_result:0".
  // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
  // the ones you produced.

  private static final int NUM_CLASSES = 1001;
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input:0";
    private static final String OUTPUT_NAME = "output:0";
  /*
  private static final int NUM_CLASSES = 1001;
  private static final int INPUT_SIZE = 299;
  private static final int IMAGE_MEAN = 128;
  private static final float IMAGE_STD = 128;
  private static final String INPUT_NAME = "Mul:0";
  private static final String OUTPUT_NAME = "softmax:0";
*/
  private int walletNum = 0;
    private int keyboardNum = 0;
    private int glassNum = 0;
  private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
  private static final String LABEL_FILE =
          "file:///android_asset/imagenet_comp_graph_label_strings.txt";

  private Integer sensorOrientation;

  private final TensorflowClassifier tensorflow = new TensorflowClassifier();

  private int previewWidth = 0;
  private int previewHeight = 0;
  private byte[][] yuvBytes;
  private int[] rgbBytes = null;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private boolean computing = false;
  private Handler handler;
  private boolean mouse = false;
  private int wallet_num=0;
    private int glass_num=0;
  private Bitmap sendBitmap = null;

  private RecognitionScoreView scoreView;

  public void initialize(
          final AssetManager assetManager,
          final RecognitionScoreView scoreView,
          final Handler handler,
          final Integer sensorOrientation) {
    Assert.assertNotNull(sensorOrientation);
    tensorflow.initializeTensorflow(
            assetManager, MODEL_FILE, LABEL_FILE, NUM_CLASSES, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD,
            INPUT_NAME, OUTPUT_NAME);
    this.scoreView = scoreView;
    this.handler = handler;
    this.sensorOrientation = sensorOrientation;
  }

  private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
    Assert.assertEquals(dst.getWidth(), dst.getHeight());
    final float minDim = Math.min(src.getWidth(), src.getHeight());

    final Matrix matrix = new Matrix();

    // We only want the center square out of the original rectangle.
    final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
    final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
    matrix.preTranslate(translateX, translateY);

    final float scaleFactor = dst.getHeight() / minDim;
    matrix.postScale(scaleFactor, scaleFactor);

    // Rotate around the center if necessary.
    if (sensorOrientation != 0) {
      matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
      matrix.postRotate(sensorOrientation);
      matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
    }

    final Canvas canvas = new Canvas(dst);
    canvas.drawBitmap(src, matrix, null);
  }


  @Override
  public void onImageAvailable(final ImageReader reader) {
    Image image = null;
    try {
      image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      // No mutex needed as this method is not reentrant.
      if (computing) {
        image.close();
        return;
      }


      computing = true;

      Trace.beginSection("imageAvailable");

      final Plane[] planes = image.getPlanes();

      // Initialize the storage bitmaps once when the resolution is known.
      if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
        previewWidth = image.getWidth();
        previewHeight = image.getHeight();

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);
        sendBitmap  = rgbFrameBitmap;
        yuvBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
          yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
        }
      }
      for (int i = 0; i < planes.length; ++i) {
        planes[i].getBuffer().get(yuvBytes[i]);
      }

      final int yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();
      ImageUtils.convertYUV420ToARGB8888(
              yuvBytes[0],
              yuvBytes[1],
              yuvBytes[2],
              rgbBytes,
              previewWidth,
              previewHeight,
              yRowStride,
              uvRowStride,
              uvPixelStride,
              false);

      image.close();
    } catch (final Exception e) {
      if (image != null) {
        image.close();
      }
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }

    rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
    //drawResizedBitmap_0(rgbFrameBitmap, croppedBitmap);
    drawResizedBitmap(rgbFrameBitmap, croppedBitmap);


    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    handler.post(
            new Runnable() {
                @Override
                public void run() {
                    final List<Classifier.Recognition> results = tensorflow.recognizeImage(croppedBitmap);
                    if (results != null) {
                    }

                    LOGGER.v("%d results", results.size());
                    for (final Classifier.Recognition result : results) {
                        if (results.size() == 1) {
                            if (result.getTitle().equals("ping-pong ball")) {
                                Message message4 = new Message();
                                message4.what = CameraConnectionFragment.FIND_PPBALL;
                                handler.sendMessage(message4);
                        }
                    }else{
                        LOGGER.v("Result: " + result.getTitle());
                        if ((result.getTitle().equals("ping-pong ball")) || (result.getTitle().equals("golf ball")) || (result.getTitle().equals("orange")) || (result.getTitle().equals("lemon"))) {
                            if( (result.getTitle().equals("ping-pong ball") && (result.getConfidence() >= 0.16))||(result.getTitle().equals("orange") && (result.getConfidence() >= 0.2))){
                                LOGGER.e("开启" + result.getConfidence());
                                Message message2 = new Message();
                                message2.what = CameraConnectionFragment.FIND_PPBALL;
                                handler.sendMessage(message2);
                                break;
                            }
                            if((result.getTitle().equals("orange"))){
                                result.getTitle().replace("orange","ping-pong ball");
                            }
                            LOGGER.e("扫描到疑似乒乓球" + result.getConfidence());
                            Message message = new Message();
                            message.what = CameraConnectionFragment.FIND_LIKEPPBALL;
                            handler.sendMessage(message);
                        } else if (result.getTitle().equals("wallet") && result.getConfidence() > 0.4) {
                            LOGGER.e("扫描到钱包" + result.getConfidence());
                            if (wallet_num < 1) {
                                saveImage(sendBitmap, "wallet");
                                Message message = new Message();
                                message.what = CameraConnectionFragment.FIND_WALLET;
                                handler.sendMessage(message);
                                wallet_num++;
                            }
                        }else if (result.getTitle().equals("glasses")||result.getTitle().equals("glass")||result.getTitle().equals("sun glasses") && result.getConfidence() > 0.15) {
                            LOGGER.e("扫描到眼镜" + result.getConfidence());
                            if (glass_num < 1) {
                                saveImage(sendBitmap, "glass");
                                Message message = new Message();
                                message.what = CameraConnectionFragment.FIND_GLASS;
                                handler.sendMessage(message);
                                glass_num++;
                            }
                        }
                            else if (result.getTitle().equals("mouse")||result.getTitle().equals("computer keyboard")||result.getTitle().equals("space bar")) {
                            Message message = new Message();
                            message.what = CameraConnectionFragment.FIND_OBSTACLE;
                            handler.sendMessage(message);
                        }
                    }


                    /**
                     if (result.getTitle().equals("wallet")&&(result.getConfidence()>0.35)) {
                     LOGGER.e("扫描到钱包" + result.getConfidence());
                     if (walletNum < 1) {
                     Message message = new Message();
                     message.what = CameraConnectionFragment.FIND_WALLET;
                     walletNum++;
                     //  saveImage(sendBitmap, "wallet");
                     handler.sendMessage(message);
                     }
                     }
                     if (((result.getTitle().equals("sunglass"))||(result.getTitle().equals("sunglasses")))&&(result.getConfidence()>0.35)) {
                     LOGGER.e("扫描眼镜" + result.getConfidence());
                     if (glassNum < 1) {
                     Message message = new Message();
                     message.what = CameraConnectionFragment.FIND_GLASSES;
                     glassNum++;
                     saveImage(sendBitmap, "glass");
                     handler.sendMessage(message);
                     }
                     }
                     if (((result.getTitle().equals("space bar"))||(result.getTitle().equals("computer keyboard")))&&(result.getConfidence()>0.35)) {
                     LOGGER.e("扫描键盘" + result.getConfidence());
                     if (keyboardNum < 1) {
                     Message message = new Message();
                     message.what = CameraConnectionFragment.FIND_KEYBOARD;
                     keyboardNum++;
                     // saveImage(sendBitmap, "keyboard");
                     handler.sendMessage(message);
                     }
                     }
                     **/
                }
                scoreView.setResults(results);
                computing=false;
            }
  });

      Trace.endSection();
  }

 public static File saveImage(Bitmap bmp,String name) {
    File appDir = new File(Environment.getExternalStorageDirectory(), "tensorflowpic");
    if (!appDir.exists()) {
        appDir.mkdir();
    }
    String fileName = name + ".jpg";
    File file = new File(appDir, fileName);
    try {
        FileOutputStream fos = new FileOutputStream(file);
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.flush();
        fos.close();
    } catch (FileNotFoundException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }
   return file;
}

}