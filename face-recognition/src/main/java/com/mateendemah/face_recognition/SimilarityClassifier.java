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

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

/** Generic interface for interacting with different recognition engines. */
public interface SimilarityClassifier {

  List<Recognition> recognizeImage(Bitmap bitmap, boolean getExtra);

  /** An immutable result returned by a Classifier describing what was recognized. */
  class Recognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final String id;

    /** Display name for the recognition. */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Lower should be better.
     */
    private final Float distance;
    private float[][] extra;

    /** Optional location within the source image for the location of the recognized object. */
    private final RectF location;

    public Recognition(
            final String id, final String title, final Float distance, final RectF location) {
      this.id = id;
      this.title = title;
      this.distance = distance;
      this.location = location;
      this.extra = null;
    }

    public void setExtra(float[][] extra) {
        this.extra = extra;
    }
    public float[][] getExtra() {
        return this.extra;
    }

    @NonNull
    @Override
    public String toString() {
      String resultString = "";
      if (id != null) {
        resultString += "[" + id + "] ";
      }

      if (title != null) {
        resultString += title + " ";
      }

      if (distance != null) {
        resultString += String.format("(%.1f%%) ", distance * 100.0f);
      }

      if (location != null) {
        resultString += location + " ";
      }

      return resultString.trim();
    }
  }
}
