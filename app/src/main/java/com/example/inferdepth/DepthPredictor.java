package com.example.inferdepth;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DepthPredictor {
    public static final String TAG = "DepthPredictor";

    /** The runtime device type used for executing classification. */
    public enum Device {
        CPU,
        NNAPI,
        GPU
    }

    private TensorImage leftInputImageBuffer;
    private TensorImage rightInputImageBuffer;
    final private TensorBuffer depthOutputBuffer;
    final int modelInputWidth, modelInputHeight;

    private final Interpreter tflite;


    /** Initializes a {@code Classifier}. */
    public DepthPredictor(Activity activity, Device device, int numThreads) throws IOException {
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());
        final Interpreter.Options tfliteOptions = new Interpreter.Options();
        switch (device) {
            case NNAPI:
                NnApiDelegate nnApiDelegate = new NnApiDelegate();
                tfliteOptions.addDelegate(nnApiDelegate);
                break;
            case GPU:
                GpuDelegate gpuDelegate = new GpuDelegate(new GpuDelegate.Options().setPrecisionLossAllowed(false));
                tfliteOptions.addDelegate(gpuDelegate);
                break;
            case CPU:
                break;
        }
        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);

        // Reads type and shape of input and output tensors, respectively.
        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
        modelInputHeight = imageShape[1];
        modelInputWidth = imageShape[2];
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        int depthTensorIndex = 0;
        int[] depthImageShape =
                tflite.getOutputTensor(depthTensorIndex).shape(); // {1, h, w, 1}
        DataType depthDataType = tflite.getOutputTensor(depthTensorIndex).dataType();

        // Creates the input tensor.
        leftInputImageBuffer = new TensorImage(imageDataType);
        rightInputImageBuffer = new TensorImage(imageDataType);

        // Creates the output tensor and its processor.
        depthOutputBuffer = TensorBuffer.createFixedSize(depthImageShape, depthDataType);

        // Creates the post processor for the output probability.
//        probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }

    /** Runs inference and returns the classification results. */
    public Bitmap predictDepth(final Bitmap leftViewBitmap, final Bitmap rightViewBitmap) {
        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadStereoViews");
        long startTimeForLoadImage = SystemClock.uptimeMillis();
        leftInputImageBuffer = loadImage(leftViewBitmap, leftInputImageBuffer);
        rightInputImageBuffer = loadImage(rightViewBitmap, rightInputImageBuffer);
        Object[] inputs = {leftInputImageBuffer.getBuffer(), rightInputImageBuffer.getBuffer()};
        Map<Integer, Object> output = Collections.singletonMap(0, depthOutputBuffer.getBuffer().rewind());
        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        Log.v(TAG, "Timecost to load stereo images: " + (endTimeForLoadImage - startTimeForLoadImage));

        // Runs the inference call.
        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();
        tflite.runForMultipleInputsOutputs(inputs, output);
        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        Log.v(TAG, "Timecost to run model inference: " + (endTimeForReference - startTimeForReference));

        Trace.endSection();
        return byteBufferToBitmap(depthOutputBuffer.getBuffer(), modelInputWidth, modelInputHeight);
    }

    private Bitmap byteBufferToBitmap(ByteBuffer imgBuffer, final int imageWidth, final int imageHeight){
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap depthImg = Bitmap.createBitmap(imageWidth, imageHeight, conf);
        final float maxDisparity = 255;
        imgBuffer.rewind();
        for(int y=0; y<imageHeight; y++)
            for(int x = 0; x < imageWidth; x++) {
                float disparity = imgBuffer.getFloat();
                int scaledDisparity = (int) (Math.min(disparity, maxDisparity)*255/maxDisparity);
                depthImg.setPixel(x, y, Color.argb(255, scaledDisparity, scaledDisparity, scaledDisparity));
            }
        return depthImg;
    }

    private String getModelPath(){
        return "pwc_net.tflite";
    }

    /** Loads input image, and applies preprocessing. */
    private TensorImage loadImage(final Bitmap bitmap, TensorImage imageBuffer) {
        // Loads bitmap into a TensorImage.
        imageBuffer.load(bitmap);

        // Creates processor for the TensorImage.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(modelInputHeight, modelInputWidth))
                        .build();
        return imageProcessor.process(imageBuffer);
    }


}
