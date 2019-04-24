package com.veri.help.vhsensor.imageprocess;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.veri.help.vhsensor.detector.FaceInfo;

import java.util.ArrayList;
import java.util.List;

import static com.veri.help.vhsensor.detector.MTCNN.SCALE_FACTOR;

public class ActualFacesView extends View {

    private static final String TAG = "ActualFacesView";
    private ArrayList<ActualFaces> actual_faces;
//    private Bitmap orig_bitmap;
    private final Paint points;
    private final Paint facepoints;
    private final Paint lines;
    private final Paint rect;
    private final Paint circle;
    private final Paint text;
    private float imgWidth;
    private float imgHeight;

//    public static Bitmap ret_bitmap;

    public ActualFacesView(final Context context, final AttributeSet set) {
        super(context, set);

        points = new Paint();
        points.setStrokeWidth(15);

        facepoints = new Paint();
        facepoints.setStrokeWidth(15);

        lines = new Paint();
        lines.setStrokeWidth(15);

        rect = new Paint();
        rect.setStrokeWidth(10);
        rect.setStyle(Paint.Style.STROKE);

        circle = new Paint();
        circle.setStrokeWidth(10);

        text = new Paint();
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setStrokeWidth(15);

    }

    public void showFaces(final ArrayList<ActualFaces> actual_faces,
                          float actImgWidth, float actImgHeight){ //, final Bitmap originalBitmap) {
        this.actual_faces = actual_faces;
        this.imgWidth = actImgWidth;
        this.imgHeight = actImgHeight;
//        this.orig_bitmap = originalBitmap;
        postInvalidate();
    }


    @Override
    public void onDraw(Canvas canvas) {

//        Log.d(TAG, "scale factor" + SCALE_FACTOR);

        float width = (float) getWidth() / imgWidth;
        float height = (float) getHeight() / imgHeight;

//        Log.d(TAG, "New Canvas size:" + getWidth() + ":" + getHeight());
//        Log.d(TAG, "New Width:" + width + "," + height);

        int int_width = (int) width;
        int int_height = (int) height;

        if (this.actual_faces != null) {

//            Log.d(TAG, "in here");

            for (int j=0; j < actual_faces.size(); j++ ) {  //saras, for testing have commented
//                for (int j=0; j < 1; j++ ) {

                if (j%5 == 0 ) { //(j==0){
                    //Log.d(TAG,"in loop 20");
                    points.setColor(Color.CYAN);
                    facepoints.setColor(Color.CYAN);
                    lines.setColor(Color.CYAN);
                    rect.setColor(Color.CYAN);
                    circle.setColor(Color.CYAN);
                    text.setColor(Color.CYAN);

                }
                else if (j%5 == 1 ) //(j==1)
                {
                    //Log.d(TAG,"in loop 21");
                    points.setColor(Color.RED);
                    facepoints.setColor(Color.RED);
                    lines.setColor(Color.RED);
                    rect.setColor(Color.RED);
                    circle.setColor(Color.RED);
                    text.setColor(Color.RED);
                }
                else if (j%5 == 2 ) //(j==2)
                {
                    //Log.d(TAG,"in loop 22");
                    points.setColor(Color.GREEN);
                    facepoints.setColor(Color.GREEN);
                    lines.setColor(Color.GREEN);
                    rect.setColor(Color.GREEN);
                    circle.setColor(Color.GREEN);
                    text.setColor(Color.GREEN);
                }
                else if (j%5 == 3 ) //(j==3)
                {
                    //Log.d(TAG,"in loop 23");
                    points.setColor(Color.YELLOW);
                    facepoints.setColor(Color.YELLOW);
                    lines.setColor(Color.YELLOW);
                    rect.setColor(Color.YELLOW);
                    circle.setColor(Color.YELLOW);
                    text.setColor(Color.YELLOW);
                }
                else if (j%5 == 4 ) //(j==4)
                {
                    //Log.d(TAG,"in loop 24");
                    points.setColor(Color.MAGENTA);
                    facepoints.setColor(Color.MAGENTA);
                    lines.setColor(Color.MAGENTA);
                    rect.setColor(Color.MAGENTA);
                    circle.setColor(Color.MAGENTA);
                    text.setColor(Color.MAGENTA);
                }
               /* else if (j >= 5 ) //(j%5==4)
                {
                    //Log.d(TAG,"in loop 24");
                    points.setColor(Color.BLACK);
                    facepoints.setColor(Color.BLACK);
                    lines.setColor(Color.BLACK);
                    rect.setColor(Color.BLACK);
                    circle.setColor(Color.BLACK);
                    text.setColor(Color.BLACK);
                }*/


                float x = (actual_faces.get(j).face_x  * width);
                float y = (actual_faces.get(j).face_y  * height);
                float w = (actual_faces.get(j).face_width * width);
                float h = (actual_faces.get(j).face_height * height);

/*                Log.d(TAG, "New canvas values act:" + imgWidth + "," +  imgHeight + "," +  actual_faces.get(j).face_x + "," + actual_faces.get(j).face_y + ","
                        +  actual_faces.get(j).face_width + "," +  actual_faces.get(j).face_height );

                Log.d(TAG, "New canvas values:" + getWidth() + "," +  getHeight() + "," +  x + "," + y + "," +  w + "," +  h );*/

                canvas.drawRect(x, y, w + x, h + y, rect);
//                canvas.drawText("Score:" + actual_faces.get(j).det_score, x - 15, y - 15, text);  //for UCCS
                canvas.drawText("SubjectID:" + actual_faces.get(j).subject_id, x - 15, y - 15, text);  //for protocol validation

                //As of now, no face keypoints data for the actual dataset
                /*for (int k = 0; k < 5; k++){
                    if (k == 0 )
                    {
                        circle.setColor(Color.CYAN);
                    }
                    else if (k == 1 )
                    {
                        circle.setColor(Color.RED);
                    }
                    else if (k == 2 )
                    {
                        circle.setColor(Color.GREEN);
                    }
                    else if (k == 3 )
                    {
                        circle.setColor(Color.YELLOW);
                    }
                    else if (k == 4 )
                    {
                        circle.setColor(Color.MAGENTA);
                    }
                    canvas.drawCircle(faces_detected.get(j).landmark[2 * k] * width,  faces_detected.get(j).landmark[2 * k + 1] * height, 4, circle);
                } */  //end of k for loop
            } //end of for loop
        }
    }
}