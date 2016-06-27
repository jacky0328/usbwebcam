package com.ford.openxc.webcam;


import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.core.Rect;

import android.content.Context;
public class WebcamManager extends Service {

    private static String TAG = "WebcamManager";

    private IBinder mBinder = new WebcamBinder();
    private Webcam mWebcam;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    //System.loadLibrary("modulNative");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    
    
    public class WebcamBinder extends Binder {
        public WebcamManager getService() {
            return WebcamManager.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service starting");

        mWebcam = new NativeWebcam("/dev/video4");
        
        
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, mLoaderCallback);
        
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
              Log.i(TAG, "OpenCV load not successfully");
        }
        
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service being destroyed");
        mWebcam.stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service binding in response to " + intent);
        return mBinder;
    }

    public Bitmap getFrame() {
        if(!mWebcam.isAttached()) {
            stopSelf();
        }
        Bitmap bmp = mWebcam.getFrame();
        
        int src_w = bmp.getWidth();
        int src_h = bmp.getHeight();
        
        int out_width  = 320;
        int out_height = 240;
        
        Log.i(TAG, "bmp_width : " + src_w);
        Log.i(TAG, "bmp_height : " + src_h);
        
        Mat mat_in = new Mat (src_w, src_h, CvType.CV_8UC3);
              
        Utils.bitmapToMat(bmp, mat_in);
              
        //   perspective transform
        Mat trans_mat = pPerspective_form(mat_in, src_w, src_h, out_width,out_height);
              
        //  copy back to mat_in
        Rect roi = new Rect(0, 0, out_width, out_height);
        trans_mat.copyTo(mat_in.submat(roi)); 
       
        /*
        int  block_h = src_h/4;
        for (int i = 1; i < 4; i++) {
        	
        	// draw horizontal
            Core.line(mat_in, new Point(0, i * block_h ), new Point(src_w-1, i * block_h), new Scalar(0, 255, 0, 255), 3);
            //  draw vertical
            //	Core.line(rgba, new Point(i * block_w, 0), new Point(i*block_w, rows-1), new Scalar(0, 255, 0, 255), 3);
        }
        */
        
        Bitmap.Config conf = bmp.getConfig(); // see other conf types
        Bitmap bmp_out = Bitmap.createBitmap(src_w, src_h, conf); // this creates a MUTABLE bitmap 
        Utils.matToBitmap(mat_in, bmp_out);
               
        //savebmp(bmp);
            
        Bitmap bmp_read = readbmp("/data/data/com.ford.openxc.webcam/files/out.bmp");
        
        return bmp_read;
        
        //return mWebcam.getFrame();
    }
    
    public Mat pPerspective_form(Mat mat_in, int src_w, int src_h, int out_width, int out_height){
  	  Point p1 = new Point(0, 0); // (x,y) left top
        Point p2 = new Point(src_w, 0); // (x,y)  right top
        Point p3 = new Point(0, src_h); // (x,y)  left bottom
        Point p4 = new Point(src_w, src_h); // (x,y) right bottom
                 
        List<Point> source = new ArrayList<Point>();
        source.add(p1);
        source.add(p2);
        source.add(p3);
        source.add(p4);
        Mat startM = Converters.vector_Point2f_to_Mat(source);
           
        Point ocvPOut1 = new Point(0, 0);
        Point ocvPOut2 = new Point(out_width, 0);
        Point ocvPOut3 = new Point(0, out_height);
        Point ocvPOut4 = new Point(out_width, out_height);
        List<Point> dest = new ArrayList<Point>();
        dest.add(ocvPOut1);
        dest.add(ocvPOut2);
        dest.add(ocvPOut3);
        dest.add(ocvPOut4);
        Mat endM = Converters.vector_Point2f_to_Mat(dest);      
        Mat Matrix_trans = Imgproc.getPerspectiveTransform(startM, endM);
        
        Mat trans_mat = new Mat (out_width, out_height, CvType.CV_8UC3);
       
   //   pPerspective  transform
        Imgproc.warpPerspective(mat_in, trans_mat, Matrix_trans, new Size(out_width,out_height));
        
        return trans_mat;
  }
   
    public Bitmap readbmp(String filename){
    	
    	Bitmap bitmap = BitmapFactory.decodeFile(filename);
    	return bitmap;
    	
    }
    
    public void savebmp(Bitmap bmp){
       	
     String filename = "out.bmp";
      
   	 FileOutputStream out = null;
        try {
        	out = openFileOutput(filename, Context.MODE_WORLD_READABLE);
            //out = new FileOutputStream("out.bmp");
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
   	
   }
}
