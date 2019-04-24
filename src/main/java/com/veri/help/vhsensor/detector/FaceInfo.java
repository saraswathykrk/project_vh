package com.veri.help.vhsensor.detector;

public class FaceInfo {

    float[] bboxReg;
    float[] landmarkReg;
    public float[] landmark;  //modified to public by saras for images
    public FaceBox bbox;  //modified to public by saras
    FaceInfo(){
        bboxReg=new float[4];
        landmarkReg=new float[10];
        landmark=new float[10];
        bbox=new FaceBox();
    }
    public float det_area;

    public static int imageWidth = 1600;    //added by saras for images
    public static int imageHeight = 1200;   //added by saras for images
}
