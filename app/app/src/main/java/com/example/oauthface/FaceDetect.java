package com.example.oauthface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.List;

public class FaceDetect {

    public interface FaceDetectionCallback {
        void onFacesDetected(List<Face> faces, Bitmap croppedFace);
    }

    public static void detectFaces(Bitmap bitmap, Context context, FaceDetectionCallback callback) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0); // Get the first face
                        Rect boundingBox = face.getBoundingBox();
                        Bitmap croppedFace = Bitmap.createBitmap(bitmap, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
                        callback.onFacesDetected(faces, croppedFace);
                    } else {
                        callback.onFacesDetected(faces, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FaceDetection", "Face detection failed", e);
                    callback.onFacesDetected(null, null);
                });
    }
}
