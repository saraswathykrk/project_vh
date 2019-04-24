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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.veri.help.vhsensor.detector.FaceInfo.imageHeight;
import static com.veri.help.vhsensor.detector.FaceInfo.imageWidth;

public class ImageActivityOld extends Activity {

    ImageActivityOld currentActivity;

    private static final String TAG = ImageActivityOld.class.getName();

    private static int actualBitmapWidth;
    private static int actualBitmapHeight;

    private float width_scale = 0.0f, height_scale = 0.0f;

    private class Image_data {
        Bitmap bitmap;
        int num_of_faces_detected;
    }

    private Button ImgChngBtn;
    private Button LoadImgBtn;
    private ImageView imageView;
    private ImageView actualView;
    private TextView textView1;
    private TextView textView2;
    private FacesView facesView;
    private ActualFacesView actfaceView;

    private MTCNN mtcnn;
    private ScriptIntrinsicYuvToRGB script;
    private RenderScript renderScript;

    ArrayList<Integer> resIdList = new ArrayList<Integer>();
    ArrayList<String> res_files = new ArrayList<String>();
    Field[] drawables = R.drawable.class.getFields();

    private int res_count = 0;
    public String res_fname;

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
                ActivityCompat.requestPermissions(ImageActivityOld.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        try {
            currentActivity = ImageActivityOld.this;

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

            int ind = 0;
            for (Field f : drawables) {


                if (f.getName().startsWith("img")) {
                    String s1 =f.getName();
                    res_files.add(s1);
                    resIdList.add(getResources().getIdentifier(f.getName(), "drawable", getPackageName()));
                    ind++;
                }
            }

            renderScript = RenderScript.create(this);
            script = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));

        } catch (Exception exception) {
            Log.d(TAG, "Weird flex?" + exception);
            exception.printStackTrace();
        }

    }

    public void loadImageData(View view) {

        String mCSVfile = "validation.csv";
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

    public void processImageData(View view) {
        ImgChngBtn.setText(R.string.btn_msg);
        imageView.setImageBitmap(null);
        actfaceView.showFaces(null, 0.0f, 0.0f);

        ImgChngBtn.setEnabled(false);
        ImgChngBtn.setClickable(false);
        ImgChngBtn.setFocusable(false);

        if (res_count < res_files.size()-1){
            res_count++;
            Log.d(TAG, "Res count:" +res_count);
        }
        else {
            res_count = 0;
            Log.d(TAG, "Res count:" +res_count);
        }

        Image_data final_img_data = setBitmapAndProcess(res_count);

        Log.d(TAG, "image width in on click" + imageView.getWidth() + ":" + imageView.getHeight());

        Bitmap save_bitmap = createNewBitmap(final_img_data);
        saveBitmap(save_bitmap);

        DBHandler dbHandler = new DBHandler(this, null, null, 1);
        ArrayList<ActualFaces> actualFaces = dbHandler.findHandler(res_files.get(res_count).split("_")[1] + ".jpg");
        createActualBitmap(actualFaces, final_img_data);

        ImgChngBtn.setText(R.string.process_images);
        ImgChngBtn.setEnabled(true);
        ImgChngBtn.setClickable(true);
        ImgChngBtn.setFocusable(true);
//        textView2.setText(R.string.ButtonText);

    }

    private int getResourceId(String resName){
        return getResources().getIdentifier(resName, "drawable", getPackageName());
    }

    public Image_data setBitmapAndProcess(int count){
        textView1.setText(R.string.wait_msg);

        Log.d(TAG,"List of res - Resources Id:" + resIdList.get(count));
        int id = resIdList.get(count);

        res_fname = res_files.get(count);
        Log.d(TAG, "res_fname:" + res_fname);
        imageView.setImageResource(id);

        Log.d(TAG, "imageView size:" + imageView.getWidth() + ":" + imageView.getHeight());

        if (id == 0) {
            Log.e(TAG, "Lookup id for resource '" + res_fname + "' failed");
        }

        Bitmap originalBitmap = getResizedBitmap(imageView.getWidth(), imageView.getHeight(), id);
        Log.d(TAG, "original bitmap size:" + originalBitmap.getWidth() + ":" + originalBitmap.getHeight());

        int num_faces = processImage(originalBitmap, res_fname);

        Image_data image_data = new Image_data();

        image_data.bitmap = originalBitmap;
        image_data.num_of_faces_detected = num_faces;
        return image_data;
    }

    public Bitmap getResizedBitmap(int targetW, int targetH,  int res_id) {

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        //inJustDecodeBounds = true <-- will not load the bitmap into memory
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), res_id, bmOptions);
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

        Bitmap bmp = BitmapFactory.decodeResource(getResources(), res_id, bmOptions);
        return(bmp);
    }

    private int processImage(Bitmap originalBitmap, String filename) {

        int size = 0;
        Log.d(TAG, "in processImage");
        long preprocessingStart = System.currentTimeMillis();

        Log.d(TAG, "Bitmap config:" + originalBitmap.getConfig());
        Log.d(TAG, "Bitmap size:" + originalBitmap.getWidth() + "," + originalBitmap.getHeight());


        Bitmap jpegbmp = Bitmap.createScaledBitmap(originalBitmap, imageWidth, imageHeight, false);
        Log.d(TAG, "Scaled Bitmap size:" + jpegbmp.getWidth() + "," + jpegbmp.getHeight());
        Log.d(TAG, "Image size:" + imageWidth + "," + imageHeight);

        try {

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

            textView1.setText(String.format(getString(R.string.NumFaceDetect), String.format("%s.jpg", filename.split("_")[1]), faces.size(), detectEnd - detectStart));
            detectionTime += (detectEnd - detectStart);
            conversionTime += (preprocessingEnd - preprocessingStart);

            /*if (++imagereaderCycleCounts % 10 == 0) {
                Log.d(TAG, "Average detection time - " + (detectionTime * 1.0 / imagereaderCycleCounts));
                Log.d(TAG, "Average conversion time - " + (conversionTime * 1.0 / imagereaderCycleCounts));

            }*/

        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return size;

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
                    float score = faces_detected.get(j).bbox.score;

                    String data = res_fname.substring(4) + "," + x + "," + y +"," + w +"," + h + "," + score + "\n";
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
        String fname = res_fname + ".jpg";
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

        actfaceView.showFaces(actualFaces, actualBitmapWidth, actualBitmapHeight);
        actfaceView.draw(new_canvas);
        actualView.setImageBitmap(actualBitmap);
        textView2.setText(String.format("%s%s", getString(R.string.Recall), (float) image_data.num_of_faces_detected / actualFaces.size()));
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
        FaceDetectHandler dbHandler = new FaceDetectHandler(this, null, null, 1);
        dbHandler.addHandler(getResourceId(filename), filename, faces_detected, width_scale, height_scale);
        dbHandler.findHandler(res_fname);
    }


}