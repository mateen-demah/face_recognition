/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

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

package com.mateendemah.face_recognition;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - <a href="https://github.com/tensorflow/models/tree/master/research/object_detection">...</a>
 * where you can find the training code.
 *
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - <a href="https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md">...</a>
 * - <a href="https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android">...</a>
 */
public class TFLiteObjectDetectionAPIModel
        implements SimilarityClassifier {

  //private static final int OUTPUT_SIZE = 512;
  private static final int OUTPUT_SIZE = 192;

  // Float model
  private static final float IMAGE_MEAN = 128.0f;
  private static final float IMAGE_STD = 128.0f;

  // Number of threads in the java app
  private boolean isModelQuantized;
  // Config values.
  private int inputSize;
  // Pre-allocated buffers.
  private final Vector<String> labels = new Vector<>();
  private int[] intValues;

  private ByteBuffer imgData;

  private Interpreter tfLite;

// Face Mask Detector Output
  private static float[][] output;

  private TFLiteObjectDetectionAPIModel(float[][] output) {
    TFLiteObjectDetectionAPIModel.output = output;
  }

  /** Memory-map the model file in Assets. */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
      throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /**
   * Initializes a native TensorFlow session for classifying images.
   *
   * @param assetManager The asset manager to be used to load assets.
   * @param modelFilename The filepath of the model GraphDef protocol buffer.
   * @param labelFilename The filepath of label file for classes.
   * @param inputSize The size of image input
   * @param isQuantized Boolean representing model is quantized or not
   */
  public static SimilarityClassifier create(
      final AssetManager assetManager,
      final String modelFilename,
      final String labelFilename,
      final int inputSize,
      final boolean isQuantized)
      throws IOException {

    final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel(output);

    InputStream labelsInput = assetManager.open(labelFilename);
    BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;
    while ((line = br.readLine()) != null) {
      d.labels.add(line);
    }
    br.close();

    d.inputSize = inputSize;

    try {
      d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    d.isModelQuantized = isQuantized;
    // Pre-allocate buffers.
    int numBytesPerChannel;
    if (isQuantized) {
      numBytesPerChannel = 1; // Quantized
    } else {
      numBytesPerChannel = 4; // Floating point
    }
    d.imgData = ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel);
    d.imgData.order(ByteOrder.nativeOrder());
    d.intValues = new int[d.inputSize * d.inputSize];

    return d;
  }


  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap, boolean storeExtra) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    // Preprocess the image data from 0-255 int to normalized float based
    // on the provided parameters.
    Log.d("PIXELS[] SIZE",  String.valueOf(intValues.length));
    Log.d("BMP WIDTH", String.valueOf(bitmap.getWidth()));
    Log.d("BMP HEIGHT", String.valueOf(bitmap.getHeight()));
    Log.d("BMP H * W", String.valueOf(bitmap.getHeight() * bitmap.getWidth()));
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

    imgData.rewind();
    for (int i = 0; i < inputSize; ++i) {
      for (int j = 0; j < inputSize; ++j) {
        int pixelValue = intValues[i * inputSize + j];
        if (isModelQuantized) {
          // Quantized model
          imgData.put((byte) ((pixelValue >> 16) & 0xFF));
          imgData.put((byte) ((pixelValue >> 8) & 0xFF));
          imgData.put((byte) (pixelValue & 0xFF));
        } else { // Float model
          imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
      }
    }
    Trace.endSection(); // preprocessBitmap

    // Copy the input data into TensorFlow.
    Trace.beginSection("feed");


    Object[] inputArray = {imgData};

    Trace.endSection();

// Here outputMap is changed to fit the Face Mask detector
    Map<Integer, Object> outputMap = new HashMap<>();

    float[][] embeedings = new float[1][OUTPUT_SIZE];
    outputMap.put(0, embeedings);


    // Run the inference call.
    Trace.beginSection("run");
    //tfLite.runForMultipleInputsOutputs(inputArray, outputMapBack);
    tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
    Trace.endSection();


    float distance = Float.MAX_VALUE;
    String id = "0";
    String label = "?";

    final int numDetectionsOutput = 1;
    final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
    Recognition rec = new Recognition(
            id,
            label,
            distance,
            new RectF());

    recognitions.add( rec );

    if (storeExtra) {
        rec.setExtra(embeedings);
    }

    Trace.endSection();
    return recognitions;
  }
}
