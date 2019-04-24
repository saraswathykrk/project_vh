package com.veri.help.vhsensor.detector;

import android.content.Context;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.rectangle;

public class MTCNN_old {

    // region Constants

    private static final String TAG = MTCNN.class.getName();

    private static final float MEANVAL = 127.5f;
    private static final float STDVAL = 0.0078125f;
    private static final int STEPSIZE = 128;

    private static final float PNETSTRIDE = 2;  //change this and check saras
    private static final float PNETCELLSIZE = 12;
    private static final int PNETMAXDETECTIONS = 5000;

    //    public static final float SCALE_FACTOR = 0.709f;
    public static final float SCALE_FACTOR = 0.503f;


    //    private static final float[] THRESHOLDS = {0.1f, 0.2f, 0.2f};
    private static final int MINSIZE = 75;
    private static final float[] THRESHOLDS = {0.6f, 0.7f, 0.7f}; // {0.2f, 0.4f, 0.6f};
//    private static final int MINSIZE = 75; // 35; //50; //20; //12;  //


    private static final int NETWORK_STAGE = 3;

    private static final String PNET_CAFFEMODEL = "det1_.caffemodel";
    private static final String PNET_PROTOTXT = "det1_.prototxt";
    private static final String RNET_CAFFEMODEL = "det2_half.caffemodel";
    private static final String RNET_PROTOTXT = "det2.prototxt";
    private static final String ONET_CAFFEMODEL = "det3-half.caffemodel";
    private static final String ONET_PROTOTXT = "det3-half.prototxt";


    // endregion

    private Net PNet;
    private Net RNet;
    private Net ONet;

    List<FaceInfo> candidateBoxes;
    List<FaceInfo> totalBoxes;

    class score_index_list{
        float scores;
        int index;

        public score_index_list(float score, int i)
        {
            this.scores = score;
            this.index = i;
        }
    }

    public MTCNN_old(Context context) {
        Log.d(TAG, "Loading MTCNN model");
        PNet = Dnn.readNetFromCaffe(getPath(PNET_PROTOTXT, context), getPath(PNET_CAFFEMODEL, context));
        RNet = Dnn.readNetFromCaffe(getPath(RNET_PROTOTXT, context), getPath(RNET_CAFFEMODEL, context));
        ONet = Dnn.readNetFromCaffe(getPath(ONET_PROTOTXT, context), getPath(ONET_CAFFEMODEL, context));

        totalBoxes = new ArrayList<FaceInfo>();
        candidateBoxes = new ArrayList<FaceInfo>();


    }

    // region Helper Methods

    private static String getPath(String filename, Context context) {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(assetManager.open(filename));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage
            File outputFile = new File(context.getFilesDir(), filename);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            fileOutputStream.write(data);
            fileOutputStream.close();

            return outputFile.getAbsolutePath();

        } catch (IOException exception) {
            Log.e(TAG, "Failed to read caffe model from assets");
            exception.printStackTrace();
        }
        return "";
    }

    // endregion

    // region Image Calculation Methods

    private float IoU(float xMin1, float yMin1, float xMax1, float yMax1,
                      float xMin2, float yMin2, float xMax2, float yMax2,
                      boolean isIoM) {
        float intersection_width = Math.min(xMax1, xMax2) - Math.max(xMin1, xMin2) + 1;
        float intersection_height = Math.min(yMax1, yMax2) - Math.max(yMin1, yMin2) + 1;  //added 1 here, saras do we need to add 1 here?

        if (intersection_width <= 0 || intersection_height <= 0)
            return 0;
        float intersection_area = intersection_height * intersection_width;

        if (isIoM) {
            return intersection_area / Math.min((xMax1 - xMin1 + 1) * (yMax1 - yMin1 + 1), (xMax2 - xMin2 + 1) * (yMax2 - yMin2 + 1));
        } else {
            return intersection_area / ((xMax1 - xMin1 + 1) * (yMax1 - yMin1 + 1) + (xMax2 - xMin2 + 1) * (yMax2 - yMin2 + 1) - intersection_area);
        }
    }

    private void BBoxRegression(List<FaceInfo> bboxes) {
        for (FaceInfo faceInfo : bboxes) {
            FaceBox faceBox = faceInfo.bbox;
            float[] bboxReg = faceInfo.bboxReg;
            float width = faceBox.xMax - faceBox.xMin + 1;
            float height = faceBox.yMax - faceBox.yMin + 1;
            faceBox.xMin += bboxReg[0] * width;
            faceBox.yMin += bboxReg[1] * height;
            faceBox.xMax += bboxReg[2] * width;
            faceBox.yMax += bboxReg[3] * height;
        }
    }

    private void BBoxPad(List<FaceInfo> bboxes, int width, int height) {
        for (FaceInfo faceInfo : bboxes) {
            FaceBox faceBox = faceInfo.bbox;
            //Log.d(TAG, "ONet faces - " + faceBox.xMin + "," + faceBox.yMin + "," + faceBox.xMax + "," + faceBox.yMax);
            faceBox.xMin = Math.round(Math.max(faceBox.xMin, 0.0f));
            faceBox.yMin = Math.round(Math.max(faceBox.yMin, 0.0f));
            faceBox.xMax = Math.round(Math.min(faceBox.xMax, width - 1.0f));
            faceBox.yMax = Math.round(Math.min(faceBox.yMax, height - 1.0f));  //yMin changed to yMax by saras
            //Log.d(TAG, "ONet faces modif - " + faceBox.xMin + "," + faceBox.yMin + "," + faceBox.xMax + "," + faceBox.yMax);
        }
    }

    private void BBoxPadSquare(List<FaceInfo> bboxes, int width, int height) {
        for (FaceInfo faceInfo : bboxes) {
            FaceBox faceBox = faceInfo.bbox;
            float faceBoxWidth = faceBox.xMax - faceBox.xMin + 1;
            float faceBoxHeight = faceBox.yMax - faceBox.yMin + 1;
            float side = (faceBoxHeight > faceBoxWidth) ? faceBoxHeight : faceBoxWidth;
            faceBox.xMin = Math.round(Math.max(faceBox.xMin + (faceBoxWidth - side) * 0.5f, 0.0f));
            faceBox.yMin = Math.round(Math.max(faceBox.yMin + (faceBoxHeight - side) * 0.5f, 0.0f));
            faceBox.xMax = Math.round(Math.min(faceBox.xMin + side - 1, width - 1.0f));
            faceBox.yMax = Math.round(Math.min(faceBox.yMin + side - 1, height - 1.0f));
        }
    }

    private void GenerateBBox(Mat confidence, Mat regBox, float scale, float threshold) {
        int featureMapWidth = confidence.size(3);
        int featureMapHeight = confidence.size(2);
        int spatialSize = featureMapHeight * featureMapWidth;

        float[] confidenceData = new float[confidence.size(0) * confidence.size(1) * confidence.size(2) * confidence.size(3)];
        confidence = confidence.reshape(1, 1);

        confidence.get(0, 0, confidenceData);

        float[] regData = new float[regBox.size(0) * regBox.size(1) * regBox.size(2) * regBox.size(3)];
        regBox = regBox.reshape(1, 1);
        regBox.get(0, 0, regData);

        candidateBoxes.clear();
        for (int i = 0; i < spatialSize; i++) {
            if (confidenceData[spatialSize + i] <= 1 - threshold) {  //saras
//            if (confidenceData[i] <= 1 - threshold) {
                int y = i / featureMapWidth;
                int x = i - featureMapWidth * y;

                FaceInfo faceInfo = new FaceInfo();
                FaceBox faceBox = faceInfo.bbox;

                faceBox.xMin = x * PNETSTRIDE / scale;
                faceBox.yMin = y * PNETSTRIDE / scale;
                faceBox.xMax = (x * PNETSTRIDE + PNETCELLSIZE - 1.0f) / scale;
                faceBox.yMax = (y * PNETSTRIDE + PNETCELLSIZE - 1.0f) / scale;

                faceInfo.bboxReg[0] = regData[i];
                faceInfo.bboxReg[1] = regData[i + spatialSize];
                faceInfo.bboxReg[2] = regData[i + 2 * spatialSize];
                faceInfo.bboxReg[3] = regData[i + 3 * spatialSize];

                faceBox.score = confidenceData[spatialSize + i];  //saras
//                faceBox.score = confidenceData[i];
                candidateBoxes.add(faceInfo);
            }
        }
    }

    private void getMaxScoreIndex(float[] scores, float threshold, int top_k,
                                  ArrayList<score_index_list> score_index_vec){

        if (score_index_vec != null){
            Log.d(TAG, "score_index_vec is not null");
        }

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > threshold) {
                score_index_vec.add(new score_index_list(scores[i], i));
            }
        }

        Collections.sort(score_index_vec, new Comparator<score_index_list>() {
            @Override
            public int compare(score_index_list s1, score_index_list s2) {
                int val = Float.compare(s1.scores, s2.scores);
                if (val == 0){
                    return 0;
                }
                else if(val == 1){
                    return -1;
                }
                else {
                    return 1;
                }
            }
        });

        // Keep top_k scores if needed.
        if (top_k > 0 && top_k < score_index_vec.size())
        {
            for (int i = score_index_vec.size(); i > top_k; i--)
            {
                Log.d(TAG, "score_index_vec values:" + score_index_vec.get(i));
                score_index_vec.remove(i);
            }
        }
    }

    private List<FaceInfo> NMSFaster_new(List<FaceInfo> bboxes, float[] scores, float score_threshold,
                                         float nms_threshold, float eta, int top_k, List<Integer> indices) {
        long detectStart = System.currentTimeMillis();

        List<FaceInfo> bboxesNms = new ArrayList<>();

        if (bboxes.size() != scores.length) {
            Log.d(TAG, "Size of boxes not same");
        }

        ArrayList<score_index_list> score_index_vec = new ArrayList<>();
        getMaxScoreIndex(scores, score_threshold, top_k, score_index_vec);

        float adaptive_threshold = nms_threshold;

        indices.clear();

        for (int i = 0; i < score_index_vec.size(); i++)
        {
            int idx = score_index_vec.get(i).index;
            boolean keep = true;
            for (int k = 0; k < indices.size() && keep; ++k)
            {
                int kept_idx = indices.get(k);
                float overlap = IoU(bboxes.get(idx).bbox.xMin, bboxes.get(idx).bbox.yMin, bboxes.get(idx).bbox.xMax, bboxes.get(idx).bbox.yMax,
                        bboxes.get(kept_idx).bbox.xMin, bboxes.get(kept_idx).bbox.yMin, bboxes.get(kept_idx).bbox.xMax, bboxes.get(kept_idx).bbox.yMax,
                        false);
                keep = overlap <= adaptive_threshold;
            }
            if (keep)
                indices.add(idx);
            bboxesNms.add(bboxes.get(idx));
            if (keep && eta < 1 && adaptive_threshold > 0.5) {
                adaptive_threshold *= eta;
            }
        }
        return bboxesNms;
    }


    private List<FaceInfo> NMSFaster(final List<FaceInfo> bboxes, float threshold) {
        long detectStart = System.currentTimeMillis();

        List<FaceInfo> bboxesNms = new ArrayList<>();

        if (bboxes.size() == 0)
            return bboxesNms;

        List<Integer> indices = new LinkedList<>();

        for (int i = 0; i < bboxes.size(); i++)
            indices.add(i);

        Collections.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -(int) Math.signum(bboxes.get(o1).bbox.yMax - bboxes.get(o2).bbox.yMax);
            }
        });

        while (indices.size() > 0) {
//            int lastIndex = indices.size() - 1;
            int lastIndex = 0;
            int i = indices.get(lastIndex);
            bboxesNms.add(bboxes.get(i));

            float xx1 = 0, yy1 = 0, xx2 = 0, yy2 = 0;
            for (int j : indices) {
                if (j == i)
                    continue;
                xx1 = Math.max(bboxes.get(i).bbox.xMin, bboxes.get(j).bbox.xMin);
                yy1 = Math.max(bboxes.get(i).bbox.yMin, bboxes.get(j).bbox.yMin);
                xx2 = Math.min(bboxes.get(i).bbox.xMax, bboxes.get(j).bbox.xMax);
                yy2 = Math.min(bboxes.get(i).bbox.yMax, bboxes.get(j).bbox.yMax);
            }

            float width = Math.max(0, xx2 - xx1 + 1);
            float height = Math.max(0, yy2 - yy1 + 1);

            List<Integer> removableIndices = new ArrayList<>();
            removableIndices.add(lastIndex);

            int currentIndexOfIndices = 0;
            for (int j : indices) {
                if (i == j) {
                    currentIndexOfIndices++;
                    continue;
                }
                float area = (bboxes.get(j).bbox.xMax - bboxes.get(j).bbox.xMin + 1) * (bboxes.get(j).bbox.yMax - bboxes.get(j).bbox.yMin + 1);
                float overlap = (width * height) / area;

                if (overlap > threshold)
                    removableIndices.add(currentIndexOfIndices);

                currentIndexOfIndices++;
            }
            for (int j = removableIndices.size() - 1; j >= 0; j--) {
                indices.remove(j);
            }
        }
        long detectEnd = System.currentTimeMillis();
        Log.d(TAG, "Time Taken for faster nms - " + (detectEnd - detectStart) + "ms");
        return bboxesNms;
    }

    private List<FaceInfo> NMS(List<FaceInfo> bboxes, float threshold, char method) {

        long detectStart = System.currentTimeMillis();

      /*  List<FaceInfo> bboxesNms = NMSFaster(bboxes, threshold);
        return bboxesNms;*/
//        List<FaceInfo> response = NMSFaster(bboxes, threshold);
//        if (response.size() == 0)
//            return response;  //saras not sure if this is being used



        List<FaceInfo> bboxesNms = new ArrayList<>();
        if (bboxes.size() == 0)
            return bboxesNms;

        Collections.sort(bboxes, new Comparator<FaceInfo>() {
            @Override
            public int compare(FaceInfo o1, FaceInfo o2) {
                return (int) Math.signum(o1.bbox.score - o2.bbox.score);
            }
        });

        int selectedIndex = 0;
        int numBBox = bboxes.size();

        boolean[] maskMerged = new boolean[numBBox];

        boolean allMerged = false;

        while (!allMerged) {
            while (selectedIndex < numBBox && maskMerged[selectedIndex]) {
                selectedIndex++;
            }
            if (selectedIndex == numBBox) {
                allMerged = true;
                continue;
            }
            bboxesNms.add(bboxes.get(selectedIndex));
            maskMerged[selectedIndex] = true;

            FaceBox selectedBBox = bboxes.get(selectedIndex).bbox;
            float x1_selected = selectedBBox.xMin;
            float y1_selected = selectedBBox.yMin;
            float x2_selected = selectedBBox.xMax;
            float y2_selected = selectedBBox.yMax;

            selectedIndex++;

            for (int i = selectedIndex; i < numBBox; i++) {
                if (maskMerged[i])
                    continue;
                FaceBox bbox_i = bboxes.get(i).bbox;
                switch (method) {
                    case 'u':
                    case 'U':
                        if (IoU(x1_selected, y1_selected, x2_selected, y2_selected,
                                bbox_i.xMin, bbox_i.yMin, bbox_i.xMax, bbox_i.yMax,
                                false) > threshold)
                            maskMerged[i] = true;
                        break;
                    case 'm':
                    case 'M':
                        if (IoU(x1_selected, y1_selected, x2_selected, y2_selected,
                                bbox_i.xMin, bbox_i.yMin, bbox_i.xMax, bbox_i.yMax,
                                true) > threshold)
                            maskMerged[i] = true;
                        break;
                }
            }
        }
        long detectEnd = System.currentTimeMillis();
        Log.d(TAG, "Time Taken for normal nms - " + (detectEnd - detectStart) + "ms");
        return bboxesNms;
    }

    // endregion

    // region Inference Methods

    private List<FaceInfo> ProposalNet(Mat image, int minSize, float threshold, float factor) {
        long funcStart = System.currentTimeMillis();

        Mat resized = new Mat();
        int width = image.cols();
        int height = image.rows();

        float scale = 12.0f / minSize;
        float minDim = Math.min(width, height) * scale;

        List<Float> scales = new ArrayList<>();

        while (minDim >= 12) {
            scales.add(scale);
            minDim *= factor;
            scale *= factor;
        }

        long nmsLatency = 0;
        long preNMSBoxesPerStep = 0;
        totalBoxes.clear();

        for (float scaleValue : scales) {
            int scaledWidth = (int) Math.ceil(width * scaleValue);
            int scaledHeight = (int) Math.ceil(height * scaleValue);

            Imgproc.resize(image, resized, new Size(scaledWidth, scaledHeight), 0, 0, Imgproc.INTER_LINEAR);
            Mat inputBlob = Dnn.blobFromImage(resized, 1.0f / 255.0f, new Size(), new Scalar(0, 0, 0), false);

            PNet.setInput(inputBlob, "data");

            List<String> targetNodes = new ArrayList<>();
            List<Mat> targetBlobs = new ArrayList<>();

            targetNodes.add("conv4-2");
            targetNodes.add("prob1");

            PNet.forward(targetBlobs, targetNodes);

            Mat reg = targetBlobs.get(0);
            Mat prob = targetBlobs.get(1);

            GenerateBBox(prob, reg, scaleValue, threshold);
            preNMSBoxesPerStep += candidateBoxes.size();
            long nmsStart = System.currentTimeMillis();
            List<FaceInfo> bboxesNMS = NMS(candidateBoxes, 0.5f, 'u');
//            List<FaceInfo> bboxesNMS = NMSFaster(candidateBoxes, prob, 0.5f, nms_threshold,  'u');
            nmsLatency += (System.currentTimeMillis() - nmsStart);
            if (bboxesNMS.size() > 0)
                totalBoxes.addAll(bboxesNMS);
        }
        int numBox = totalBoxes.size();
        Log.i(TAG, "PNet NMS boxes passed in pyramid - " + preNMSBoxesPerStep);
        List<FaceInfo> resBoxes = new ArrayList<>();
        if (numBox != 0) {
            long nmsStart = System.currentTimeMillis();
            Log.i(TAG, "PNet NMS boxes passed final - " + numBox);
            resBoxes = NMS(totalBoxes, 0.7f, 'u');
            nmsLatency += (System.currentTimeMillis() - nmsStart);
            BBoxRegression(resBoxes);
            BBoxPadSquare(resBoxes, width, height);
        }
        Log.i(TAG, "PNet NMS time - " + nmsLatency);
        long funcEnd = System.currentTimeMillis();
        Log.d(TAG, "Time Taken for PNet - " + (funcEnd - funcStart) + "ms");
        return resBoxes;
    }

    private List<FaceInfo> NextStage(Mat image, List<FaceInfo> preStageResult, int inputWidth, int inputHeight, int stageNum, float threshold) {


        long funcStart = System.currentTimeMillis();

        Log.d(TAG, "Input Width" + inputWidth + "," + inputHeight);
        List<FaceInfo> result = new ArrayList<>();
        int batchsize = preStageResult.size();

        if (batchsize == 0)
            return result;

        Mat confidence = null;
        Mat regBox = null;
        Mat regLandmark = null;

        List<Mat> targetBlobs = new ArrayList<>();

        if (!(stageNum == 2 || stageNum == 3))
            return result;

//        int spatialSize = inputHeight * inputWidth;

        List<Mat> inputs = new ArrayList<>();

        for (int i = 0; i < batchsize; i++) {
            FaceBox faceBox = preStageResult.get(i).bbox;
            Mat roi = new Mat(image, new Rect(new Point((int) faceBox.xMin, (int) faceBox.yMin), new Point((int) faceBox.xMax, (int) faceBox.yMax)));
            Imgproc.resize(roi, roi, new Size(inputWidth, inputHeight));
            inputs.add(roi);
        }

        Mat blobInput = Dnn.blobFromImages(inputs, STDVAL, new Size(), new Scalar(MEANVAL, MEANVAL, MEANVAL), false);

        List<String> targetNodes = new ArrayList<>();
        switch (stageNum) {
            case 2:
                RNet.setInput(blobInput, "data");
                targetNodes.add("conv5-2");
                targetNodes.add("prob1");
                RNet.forward(targetBlobs, targetNodes);
                regBox = targetBlobs.get(0);
                confidence = targetBlobs.get(1);
                break;
            case 3:
                ONet.setInput(blobInput, "data");
                targetNodes.add("conv6-2");
                targetNodes.add("conv6-3");
                targetNodes.add("prob1");
                ONet.forward(targetBlobs, targetNodes);
                regBox = targetBlobs.get(0);
                regLandmark = targetBlobs.get(1);
                confidence = targetBlobs.get(2);
                break;
        }
//        Log.d(TAG, "Confidence length - " + (confidence.size(0) * confidence.size(1)));
        float[] confidenceData = new float[confidence.size(0) * confidence.size(1)];
//        Log.d(TAG, "Regbox length - " + (regBox.size(0) * regBox.size(1)));
        float[] regData = new float[regBox.size(0) * regBox.size(1)];

        confidence = confidence.reshape(1, 1);
        regBox = regBox.reshape(1, 1);
        confidence.get(0, 0, confidenceData);
        regBox.get(0, 0, regData);
        float[] landmarkData = null;
        if (regLandmark != null) {
            landmarkData = new float[regLandmark.size(0) * regLandmark.size(1)];
            regLandmark.reshape(1, 1);
            regLandmark.get(0, 0, landmarkData);
        }
        for (int k = 0; k < batchsize; k++) {
            if (confidenceData[2 * k + 1] >= threshold) {  //saras
                FaceInfo info = new FaceInfo();
                info.bbox.score = confidenceData[2 * k + 1];
                info.bbox.xMin = preStageResult.get(k).bbox.xMin;
                info.bbox.yMin = preStageResult.get(k).bbox.yMin;
                info.bbox.xMax = preStageResult.get(k).bbox.xMax;
                info.bbox.yMax = preStageResult.get(k).bbox.yMax;

                for (int i = 0; i < 4; i++) {
                    info.bboxReg[i] = regData[4 * k + i];
                }
                if (regLandmark != null) {
                    float width = info.bbox.xMax - info.bbox.xMin + 1.0f;
                    float height = info.bbox.yMax - info.bbox.yMin + 1.0f;
                    for (int i = 0; i < 5; i++) {
                        info.landmark[2 * i] = landmarkData[10 * k + 2 * i] * width + info.bbox.xMin;
                        info.landmark[2 * i + 1] = landmarkData[10 * k + 2 * i + 1] * height + info.bbox.yMin;
                    }
                }
                result.add(info);
            }
        }
        long funcEnd = System.currentTimeMillis();
        Log.d(TAG, "Time Taken for Next Stage Func - " + (funcEnd - funcStart) + "ms");
        return result;
    }

    // endregion

    public List<FaceInfo> detectFaces(Mat image) {
        long detectStart = System.currentTimeMillis();
        float[] threshold = THRESHOLDS;

        List<FaceInfo> PNetResult = new ArrayList<>();
        List<FaceInfo> RNetResult = new ArrayList<>();
        List<FaceInfo> ONetResult = new ArrayList<>();

        Log.d(TAG, "in here 1:" + image.cols() + "," + image.rows());

        if (NETWORK_STAGE >= 1) {
            Log.d(TAG, "in here 2, scalefactor" + SCALE_FACTOR);
            Log.i(TAG, "PNet stage");
            PNetResult = ProposalNet(image, MINSIZE, threshold[0], SCALE_FACTOR);
            Log.i(TAG, "PNet results count - " + PNetResult.size());

        }

        Log.d(TAG, "in here 3");
        if (NETWORK_STAGE >= 2 && PNetResult.size() > 0) {
            Log.d(TAG, "in here 4");
            if (PNetResult.size() > PNETMAXDETECTIONS) {
                PNetResult = PNetResult.subList(0, PNETMAXDETECTIONS);
            }

            int num = PNetResult.size();
            int size = (int) Math.ceil(1.0f * num / STEPSIZE);
            for (int iter = 0; iter < size; iter++) {
                int start = iter * STEPSIZE;
                int end = Math.min(start + STEPSIZE, num);
                List<FaceInfo> input = PNetResult.subList(start, end);
                List<FaceInfo> result = NextStage(image, input, 24, 24, 2, threshold[1]);
                RNetResult.addAll(result);
            }
            RNetResult = NMS(RNetResult, 0.4f, 'm');
            BBoxRegression(RNetResult);
            BBoxPadSquare(RNetResult, image.cols(), image.rows());
        }

        Log.d(TAG, "in here 5");

        if (NETWORK_STAGE >= 3 && RNetResult.size() > 0) {
            Log.d(TAG, "in here 6");
            int num = RNetResult.size();
            Log.d(TAG, "RNet output size - " + num);
            int size = (int) Math.ceil(1.0f * num / STEPSIZE);
            for (int iter = 0; iter < size; iter++) {  //saras
                int start = iter * STEPSIZE;
                int end = Math.min(start + STEPSIZE, num);
                List<FaceInfo> input = RNetResult.subList(start, end);
                List<FaceInfo> result = NextStage(image, input, 48, 48, 3, threshold[2]);
                ONetResult.addAll(result);
            }
            BBoxRegression(ONetResult);
            ONetResult = NMS(ONetResult, 0.4f, 'm');
            Log.d(TAG, "ONet output size - " + ONetResult.size());

            for(int i = 0; i < ONetResult.size(); i++) {

                Log.d(TAG, "ONet outputs bbox Xmin" + ONetResult.get(i).bbox.xMin);
                Log.d(TAG, "ONet outputs bbox Ymin" + ONetResult.get(i).bbox.yMin);
                Log.d(TAG, "ONet outputs bbox Xmax" + ONetResult.get(i).bbox.xMax);
                Log.d(TAG, "ONet outputs bbox Ymax" + ONetResult.get(i).bbox.yMax);
                Log.d(TAG, "ONet outputs bbox score" + ONetResult.get(i).bbox.score);


                //Log.d(TAG, "ONet outputs" + ONetResult.get(i).bboxReg);
                for (int j =0; j < ONetResult.get(i).bboxReg.length; j++)
                {
                    Log.d(TAG, "ONet outputs bboxReg" + ONetResult.get(i).bboxReg[j]);
                }
                Log.d(TAG, "ONet landmark outputs size" + ONetResult.get(i).landmark.length);
                for (int j =0; j < ONetResult.get(i).landmark.length; j++)
                {
                    Log.d(TAG, "ONet outputs landmark" + ONetResult.get(i).landmark[j]);
                }
                //Log.d(TAG, "ONet outputs" + ONetResult.get(i).landmarkReg);
                for (int j =0; j < ONetResult.get(i).landmarkReg.length; j++)
                {
                    Log.d(TAG, "ONet outputs landmarkReg" + ONetResult.get(i).landmarkReg[j]);
                }
            }

            BBoxPad(ONetResult, image.cols(), image.rows());
            long detectEnd = System.currentTimeMillis();
            Log.d(TAG, "Time Taken for detect Faces - " + (detectEnd - detectStart) + "ms");
        }

        Log.d(TAG, "in here 7");

        switch (NETWORK_STAGE) {
            case 1:
                Log.d(TAG, "in here 8");
                return PNetResult;
            case 2:
                Log.d(TAG, "in here 9");
                return RNetResult;
            case 3:
            default:
                Log.d(TAG, "in here 10");
                return ONetResult;
        }
    }

}
