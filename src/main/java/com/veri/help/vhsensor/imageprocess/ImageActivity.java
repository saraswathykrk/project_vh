package com.veri.help.vhsensor.imageprocess;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.veri.help.vhsensor.R;
import com.veri.help.vhsensor.detector.FaceInfo;
import com.veri.help.vhsensor.detector.FacesView;
import com.veri.help.vhsensor.detector.MTCNN;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.veri.help.vhsensor.detector.FaceInfo.imageHeight;
import static com.veri.help.vhsensor.detector.FaceInfo.imageWidth;

public class ImageActivity extends Activity {

    ImageActivity currentActivity;

    private static final String TAG = ImageActivity.class.getName();

    private static int actualBitmapWidth;
    private static int actualBitmapHeight;

    private int sequence = 0;

    private float width_scale = 0.0f, height_scale = 0.0f;

    private class Image_data {
        Bitmap bitmap;
        int num_of_faces_detected;
        String filename;
        List<FaceInfo> faces;
    }

    boolean cancelled = false;

    int true_positives = 0;
    int true_negatives = 0;
    int false_positives = 0;
    int false_negatives = 0;
    int tot_true_pos = 0;
    int tot_true_neg = 0;
    int tot_false_pos = 0;
    int tot_false_neg = 0;

    private int processed_img_count = 0;
    private int tot_detected_faces = 0;
    private int tot_actual_faces = 0;
    private float recall = 0.0f;
    private float tot_recall = 0.0f;
    private float precision = 0.0f;
    private float tot_precision = 0.0f;

    private Button ImgChngBtn;
    private Button LoadImgBtn;
    private ImageView imageView;
    private ImageView actualView;
    private TextView textView1;
    private TextView textView2;
    private FacesView facesView;
    private ActualFacesView actfaceView;

    private MTCNN mtcnn;
    private String image_name;
    private ScriptIntrinsicYuvToRGB script;
    private RenderScript renderScript;

    private long detectionTime = 0;
    private long imagereaderCycleCounts = 0;
    private long conversionTime = 0;

    static {
        OpenCVLoader.initDebug();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        if (Build.VERSION.SDK_INT >= 23) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ImageActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        try {
            currentActivity = ImageActivity.this;

            ImgChngBtn = findViewById(R.id.processImg);
            ImgChngBtn.setVisibility(View.GONE);

            LoadImgBtn = findViewById(R.id.btnload);
            LoadImgBtn.setVisibility(View.VISIBLE);

            imageView = findViewById(R.id.firstImg);
            facesView = findViewById(R.id.faces_detected);

            actualView = findViewById(R.id.actualImg);
            actfaceView = findViewById(R.id.faces_actual);

            textView1 = findViewById(R.id.textView1);
            textView1.setText(getString(R.string.LoadData));

            textView2 = findViewById(R.id.textView2);
            textView2.setText(getString(R.string.LoadData));
            textView2.setVisibility(View.GONE);

            mtcnn = new MTCNN(this);

            renderScript = RenderScript.create(this);
            script = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));

        } catch (Exception exception) {
            Log.d(TAG, "Weird flex?" + exception);
            exception.printStackTrace();
        }

    }

    public void loadImageData(View view) {

        String mCSVfile = "new_validation_3_6.csv"; //"new_UCCS_detection_baseline.txt"; // //"UCCS-v2-detection-baseline-validation.txt";  //modify the validation file accordingly, saras
        AssetManager manager = this.getAssets();

        InputStream inStream = null;
        try {
            inStream = manager.open(mCSVfile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        DBHandler dbHandler = new DBHandler(this, null, null, 1);
        String loadImgStatus = dbHandler.loadHandler(inStream);
        String imgTableStatus = dbHandler.addHandler();  //need to include exception, saras

        String dbStatus = loadImgStatus + " and " + imgTableStatus;

        ImgChngBtn.setVisibility(View.VISIBLE);
        LoadImgBtn.setVisibility(View.GONE);
        textView2.setVisibility(View.VISIBLE);

        textView1.setText(dbStatus);
        textView2.setText(R.string.ButtonText);
    }

    /*public void processImageData(View view) {
        textView1.setText(null);
        textView2.setText("");
        ImgChngBtn.setText(R.string.btn_msg);
        imageView.setImageBitmap(null);
        facesView.showFaces(null, null);
        actualView.setImageBitmap(null);
        actfaceView.showFaces(null, 0.0f, 0.0f);

        ImgChngBtn.setEnabled(false);
        ImgChngBtn.setClickable(false);
        ImgChngBtn.setFocusable(false);

        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                DBHandler dbHandler = new DBHandler(getApplicationContext(), null, null, 1);
                image_name = dbHandler.findImgHandler();

                Log.d(TAG, "Image_name:" + image_name + "value");

                String not_found = "not found";

                if (image_name.trim().equals("not found")) {
                    textView1.setText(getString(R.string.IMG_PROCESSED));
                    ImgChngBtn.setText(getString(R.string.BTN_IMG_PROCESSED));
                }

                File dir = new File("/storage/emulated/0/images/process");

                String fileWithPath = dir + "/" + image_name;

                Image_data final_img_data = setBitmapAndProcess(fileWithPath);

                if (final_img_data != null) {
                    dbHandler.UpdateImgHandler(image_name, "Y");

                    Log.d(TAG, "image width in on click" + imageView.getWidth() + ":" + imageView.getHeight());

                    Bitmap save_bitmap = createNewBitmap(final_img_data);
                    saveBitmap(save_bitmap);

                    ArrayList<ActualFaces> actualFaces = dbHandler.findHandler(image_name);
                    createActualBitmap(actualFaces, final_img_data);
                } else {
                    textView1.setText(String.format("%s : Image not found!", image_name));
                    dbHandler.UpdateImgHandler(image_name, "N");
                    ImgChngBtn.setText(getString(R.string.PROCESS_NEXT));
                    cancelled = true;
                }
            }
        });


        handlerThread.quit();

        ImgChngBtn.setText(R.string.process_images);
        ImgChngBtn.setEnabled(true);
        ImgChngBtn.setClickable(true);
        ImgChngBtn.setFocusable(true);
    };*/


    public void processImageData(final View view){
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {  //runOnUiThread(new Runnable() {
            public void run() {
                // if (!cancelled) {
                 textView1.setText(null);
                 textView2.setText("");
                 ImgChngBtn.setText(R.string.btn_msg);
                 imageView.setImageBitmap(null);
                 facesView.showFaces(null, null);
                 actualView.setImageBitmap(null);
                 actfaceView.showFaces(null, 0.0f, 0.0f);

                 ImgChngBtn.setEnabled(false);
                 ImgChngBtn.setClickable(false);
                 ImgChngBtn.setFocusable(false);

                 DBHandler dbHandler = new DBHandler(getApplicationContext(), null, null, 1);
                 image_name = dbHandler.findImgHandler();
//                    image_name = "0eaf51b7f1c83ba15a45610f753e66c9.jpg";
                 //                image_name = "IMG_5485_30.jpg";

                 Log.d(TAG, "Image_name:" + image_name + "value");

                 String not_found = "not found";

                 if (image_name.trim().equals("not found")) {
                     textView1.setText(getString(R.string.IMG_PROCESSED));
                     ImgChngBtn.setText(getString(R.string.BTN_IMG_PROCESSED));
                 }

                 File dir = new File("/storage/emulated/0/images/process");

                 String fileWithPath = dir + "/" + image_name;

                 Image_data final_img_data = setBitmapAndProcess(fileWithPath);

                 if (final_img_data != null) {
                     dbHandler.UpdateImgHandler(image_name, "Y");

                     Log.d(TAG, "image width in on click" + imageView.getWidth() + ":" + imageView.getHeight());

                     Bitmap save_bitmap = createNewBitmap(final_img_data);
                     saveBitmap(save_bitmap);

                     ArrayList<ActualFaces> actualFaces = dbHandler.findHandler(image_name);
                     createActualBitmap(actualFaces, final_img_data);
                 } else {
                     textView1.setText(String.format("%s : Image not found!", image_name));
                     dbHandler.UpdateImgHandler(image_name, "N");
                     ImgChngBtn.setText(getString(R.string.PROCESS_NEXT));
                     cancelled = true;
                 }

                 ImgChngBtn.setText(R.string.process_images);
                 ImgChngBtn.setEnabled(true);
                 ImgChngBtn.setClickable(true);
                 ImgChngBtn.setFocusable(true);
                 //        textView2.setText(R.string.ButtonText);
                 handler.postDelayed(this, 100000);
             }
         };
        handler.postDelayed(runnable, 100000);
    };

    private int getResourceId(String resName){
        return getResources().getIdentifier(resName, "drawable", getPackageName());
    }


    public Image_data setBitmapAndProcess(String filename){
        textView1.setText(R.string.wait_msg);

        Log.d(TAG, "imageView size:" + imageView.getWidth() + ":" + imageView.getHeight());

        Bitmap originalBitmap = getResizedBitmap(imageView.getWidth(), imageView.getHeight(), filename);

        if (originalBitmap == null) {
            DBHandler dbHandler = new DBHandler(this, null, null, 1);
            dbHandler.UpdateImgHandler(image_name, "E");
            return null;
        }

        Log.d(TAG, "original bitmap size:" + originalBitmap.getWidth() + ":" + originalBitmap.getHeight());

        List<FaceInfo> faces = processImage(originalBitmap, filename);

        Image_data image_data = new Image_data();
        image_data.bitmap = originalBitmap;
        image_data.num_of_faces_detected = faces.size();
        image_data.filename = filename;
        image_data.faces = faces;
        tot_detected_faces += faces.size();
        return image_data;
    }


    public Bitmap getResizedBitmap(int targetW, int targetH,  String filename) {

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        //inJustDecodeBounds = true <-- will not load the bitmap into memory
        bmOptions.inJustDecodeBounds = true;
        //BitmapFactory.decodeResource(getResources(), res_id, bmOptions);
        Bitmap output = BitmapFactory.decodeFile(filename, bmOptions);

        /*if (output == null){
            Log.d(TAG, "image s null");
            //return null;
        }*/

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        actualBitmapHeight = photoH;
        actualBitmapWidth = photoW;

        width_scale = (float) actualBitmapWidth/imageWidth;
        height_scale = (float) actualBitmapHeight/imageHeight;

        Log.d(TAG, "Actual bitmap size:" + photoW + "," + photoH);

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        //Bitmap bmp = BitmapFactory.decodeResource(getResources(), res_id, bmOptions);
        Bitmap bmp = BitmapFactory.decodeFile(filename, bmOptions);
        if (bmp == null){
            Log.d(TAG, "image s again null");
            return null;
        }
        return(bmp);
    }

    private List<FaceInfo> processImage(Bitmap originalBitmap, String filename) {

        int size = 0;
        Log.d(TAG, "in processImage");
        long preprocessingStart = System.currentTimeMillis();

        Log.d(TAG, "Bitmap config:" + originalBitmap.getConfig());
        Log.d(TAG, "Bitmap size:" + originalBitmap.getWidth() + "," + originalBitmap.getHeight());


        Bitmap jpegbmp = Bitmap.createScaledBitmap(originalBitmap, imageWidth, imageHeight, false);
        Log.d(TAG, "Scaled Bitmap size:" + jpegbmp.getWidth() + "," + jpegbmp.getHeight());
        Log.d(TAG, "Image size:" + imageWidth + "," + imageHeight);

//        try {

            Mat imageMat4c = new Mat(imageWidth, imageHeight, CvType.CV_8UC4);
            Utils.bitmapToMat(jpegbmp, imageMat4c);

            Mat imageMat3c = new Mat(imageWidth, imageHeight, CvType.CV_8UC3);
            Imgproc.cvtColor(imageMat4c, imageMat3c, Imgproc.COLOR_RGBA2BGR);

            long preprocessingEnd = System.currentTimeMillis();
            Log.d(TAG, "Preprocessing time - " + (preprocessingEnd - preprocessingStart) + "ms");
            Log.d(TAG, "matrix size / values" + imageMat3c.cols() + "," + imageMat3c.rows());

            long detectStart = System.currentTimeMillis();
            List<FaceInfo> faces = mtcnn.detectFaces(imageMat3c);
            long detectEnd = System.currentTimeMillis();

            size = faces.size();
            if (size > 0) {
                writeToFile(faces);
                addFacetoDB(faces, filename);
                facesView.showFaces(faces, jpegbmp);
            } else {
                facesView.showFaces(null, jpegbmp);
            }
            Log.d(TAG, "Faces detected now - " + faces.size() + " in time - " + (detectEnd - detectStart) + "ms");

            textView1.setText(String.format(getString(R.string.NumFaceDetect), ++processed_img_count, image_name, //filename.split("/")[1],
                                                        faces.size(), detectEnd - detectStart));
            detectionTime += (detectEnd - detectStart);
            conversionTime += (preprocessingEnd - preprocessingStart);

            /*if (++imagereaderCycleCounts % 10 == 0) {
                Log.d(TAG, "Average detection time - " + (detectionTime * 1.0 / imagereaderCycleCounts));
                Log.d(TAG, "Average conversion time - " + (conversionTime * 1.0 / imagereaderCycleCounts));

            }*/

            if (processed_img_count % 10 == 0) {
                Log.d(TAG, "Average detection time - " + (detectionTime * 1.0 / processed_img_count));
                Log.d(TAG, "Average conversion time - " + (conversionTime * 1.0 / processed_img_count));
            }

        /*} catch (Exception exception) {
            exception.printStackTrace();
        }*/
        return faces;

    }

    public void writeToFile(List<FaceInfo> faces_detected){
        File fileDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator +"MyDir");
        if(!fileDir.exists()){
            try{
                final boolean mkdir = fileDir.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator +"images" +File.separator+"Face_data.csv");
        Log.d(TAG, "File path for csv:" + file);
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if(file.exists()) {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
                for (int j=0; j < faces_detected.size(); j++ ) {
                    float x = (faces_detected.get(j).bbox.xMin * width_scale);
                    float y = (faces_detected.get(j).bbox.yMin * height_scale);
                    float w = (faces_detected.get(j).bbox.xMax - faces_detected.get(j).bbox.xMin) * width_scale;
                    float h = (faces_detected.get(j).bbox.yMax - faces_detected.get(j).bbox.yMin) * height_scale;
                    float area = w * h;
                    faces_detected.get(j).det_area = area;
                    float score = faces_detected.get(j).bbox.score;

                    String data = image_name + "," + x + "," + y +"," + w +"," + h + "," + area + "," + score + "\n";
                    writer.append(data);
                }
                writer.close();
                fileOutputStream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public Bitmap createNewBitmap(Image_data image_data)
    {
        Bitmap imageBitmap = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(imageBitmap);
        float scale = getResources().getDisplayMetrics().density;
        Paint p = new Paint();
        p.setColor(Color.CYAN);
        p.setTextSize(24*scale);

        Bitmap scaled_bitmap = Bitmap.createScaledBitmap(image_data.bitmap,
                canvas.getWidth(), canvas.getHeight(), false);

        canvas.drawBitmap(scaled_bitmap, 0, 0, null);
        canvas.drawText("Detected Faces : " + image_data.num_of_faces_detected,(float) imageView.getWidth()/4, (float) imageView.getHeight()/6, p );

        Log.d(TAG, "Canvas - scaled_bitmap size:" + scaled_bitmap.getWidth() + ":" + scaled_bitmap.getHeight());

        facesView.draw(canvas);
        imageView.setImageBitmap(imageBitmap);
        return imageBitmap;

    }

    public void saveBitmap(Bitmap bitmap){
        String sdCard = Environment.getExternalStorageDirectory().toString();
        File img_dir = new File(sdCard + "/images");
        boolean mkdirs = img_dir.mkdirs();
        String fname = image_name;
        File file = new File(img_dir, fname);
        Log.d(TAG,"file:" + file + "," + sdCard + "," + fname );
        if (file.exists()){
            boolean file_delete = file.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createActualBitmap(ArrayList<ActualFaces> actualFaces, Image_data image_data)
    {
        Bitmap actualBitmap = Bitmap.createBitmap(actualView.getWidth(), actualView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas new_canvas = new Canvas(actualBitmap);

        Log.d(TAG, "Passed bitmap size:" + image_data.bitmap.getWidth() + ":" + image_data.bitmap.getHeight());

        float scale = getResources().getDisplayMetrics().density;
        Paint p = new Paint();
        p.setColor(Color.CYAN);
        p.setTextSize(24*scale);

        Bitmap scaled_bitmap1 = Bitmap.createScaledBitmap(image_data.bitmap, new_canvas.getWidth(), new_canvas.getHeight(), false);

        //Bitmap scaled_bitmap1 = getResizedBitmap(actualView.getWidth(), actualView.getHeight(), res_id);

        new_canvas.drawBitmap(scaled_bitmap1, 0, 0, null);
        new_canvas.drawText("Actual Faces : " + actualFaces.size(),(float) actualView.getWidth()/4, (float) actualView.getHeight()/6, p );

        Log.d(TAG, "New scaled_bitmap size:" + scaled_bitmap1.getWidth() + ":" + scaled_bitmap1.getHeight());
        Log.d(TAG, "New Canvas size:" + new_canvas.getWidth() + ":" + new_canvas.getHeight());


        tot_actual_faces += actualFaces.size();
        actfaceView.showFaces(actualFaces, actualBitmapWidth, actualBitmapHeight);
        actfaceView.draw(new_canvas);
        actualView.setImageBitmap(actualBitmap);
        float recall = (float) image_data.num_of_faces_detected / actualFaces.size();
        float tot_recall = (float) tot_detected_faces / tot_actual_faces;
        String jac_ind = calculate_true_pos(actualFaces, image_data);
        if (jac_ind.length() > 1){
            jac_ind = jac_ind.substring(0, jac_ind.length()-1);
        }
        else{
            jac_ind = "0";
        }
/*        recall = (float) true_positives/(true_positives + false_negatives);
        precision = (float) true_positives/(true_positives + false_positives);
        tot_recall = (float) tot_true_pos/(tot_true_pos + tot_false_neg);
        tot_precision = (float) tot_true_pos/(tot_true_pos + tot_false_pos);*/
//        textView2.setText(String.format("Recall: %s, Total Recall: %s, Jaccard Index: %s, True Pos: %d, False Pos: %d", recall, tot_recall, jac_ind, true_positives, false_positives));
        textView2.setText(String.format("Recall: %s, Total Recall: %s, True Pos: %d, False Pos: %d", recall, tot_recall, true_positives, false_positives));
        Log.d(TAG,"Recall: " +  recall + ", Total Recall: " + tot_recall + ", Jaccard Index: " + jac_ind);
    }

    public String calculate_true_pos(ArrayList<ActualFaces> actualFaces, Image_data image_data){
        String final_iou = "";
        true_positives = 0;
        false_positives = 0;
        for (int i=0; i < image_data.num_of_faces_detected; i++)
        {
            for (int j=0; j < actualFaces.size(); j++)
            {
                float boxA_0 = actualFaces.get(j).actualBbox.face_x;
                float boxA_1 = actualFaces.get(j).actualBbox.face_y;
                float boxA_2 = actualFaces.get(j).actualBbox.face_x + actualFaces.get(j).actualBbox.face_width;
                float boxA_3 = actualFaces.get(j).actualBbox.face_y + actualFaces.get(j).actualBbox.face_height;

                float boxB_0 = image_data.faces.get(i).bbox.xMin * width_scale;
                float boxB_1 = image_data.faces.get(i).bbox.yMin * height_scale;
                float boxB_2 = image_data.faces.get(i).bbox.xMax * width_scale;
                float boxB_3 = image_data.faces.get(i).bbox.yMax * height_scale;

                float xA = Math.max(boxA_0, boxB_0);
                float yA = Math.max(boxA_1, boxB_1);
                float xB = Math.min(boxA_2, boxB_2);
                float yB = Math.min(boxA_3, boxB_3);

                float interArea = Math.max(0, xB - xA + 1) * Math.max(0, yB - yA + 1);

                float boxAArea = (boxA_2 - boxA_0 + 1) * (boxA_3 - boxA_1 + 1);
                float boxBArea = (boxB_2 - boxB_0 + 1) * (boxB_3 - boxB_1 + 1);

                float iou = interArea / (Math.max(boxAArea/4.0f, interArea)  + boxBArea - interArea);

                Log.d(TAG, "IOU for image : " + image_data.filename + " is :" + iou + "for i:" + i + "," + boxBArea + ", for j:" + j + "," + boxAArea);

                if (iou > 0.0f) {
                    final_iou = String.format("%s,%s", iou, final_iou);

                    if (iou >= 0.5f) {
                        true_positives++;
                        break;
                    }
                }


            }
        }
        false_positives = image_data.num_of_faces_detected - true_positives;
        return final_iou;



       /* for (int i=0; i< actualFaces.size(); i++){
            float ground_truth = actualFaces.get(i).actualBbox.g_area;
            float x = actualFaces.get(i).actualBbox.face_x;
            float y = actualFaces.get(i).actualBbox.face_y;

            Log.d(TAG, "J - Ground truth :" + ground_truth + "," + x + "," + y);
        }

        for (int i=0; i< image_data.faces.size(); i++){
            float detected_area = image_data.faces.get(i).det_area;
            float det_x = image_data.faces.get(i).bbox.xMin * width_scale;
            float det_y = image_data.faces.get(i).bbox.yMin * height_scale;

            Log.d(TAG, "J - Detected values :" + detected_area + "," + det_x + "," + det_y);
        }*/

        //float jaccard = ground_truth



    }



    /*public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;

        actualBitmapHeight = height;
        actualBitmapWidth = width;

        Log.d(TAG, "Actual bitmap size:" + width + "," + height);
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }*/


    public void addFacetoDB(List<FaceInfo> faces_detected, String filename) {
        FaceDetectHandler facedetHandler = new FaceDetectHandler(this, null, null, 1);
        facedetHandler.addHandler(sequence++, filename, faces_detected, width_scale, height_scale);
        facedetHandler.findHandler(image_name);
    }


}