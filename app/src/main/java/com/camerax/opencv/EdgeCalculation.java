package com.camerax.opencv;

import android.os.Handler;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.Locale;

/**
 * author: Denny.CM
 * date:   2021/10/21
 */
class EdgeCalculation {
    private static Handler mHandler;

    private static final int ROI_ROWS = 3;
    private static final int ROI_COLS = 3;

    EdgeCalculation(Handler handler){
        mHandler = handler;
    }

    /*
    If matSourcePath is NULL, it will not save the image of Laplacian.
    If divideToROI is true, it will split the image to 3X3 ROI and calculate CV of each ROI.
     */
    public static void calculateCoffVar(File matSourcePath, Mat matToCalculate, boolean divideToROI) {
        //Split image to 3X3 ROI
        if(divideToROI) {
            Mat matDst = new Mat();
            Core.convertScaleAbs(calLaplacian(matToCalculate), matDst);

            for (int i = 0; i < ROI_ROWS; i++) {
                for (int j = 0; j < ROI_COLS; j++) {
                    //Split to Region Of Interests
                    Point pointROI = new Point((i * 1088), (j * 816));
                    Rect rectROI = new Rect(pointROI, new Size(1088, 816));
                    Mat matROI = new Mat(matToCalculate, rectROI);

                    //Calculate CV of ROI
                    Mat matROIDst = new Mat();
                    Core.convertScaleAbs(calLaplacian(matROI), matROIDst);
                    String strROICV = String.format(Locale.US, "Blur Index = %3.2f %%", calBlurIndex(matROIDst));

                    //Draw a rectangle of ROI and put CV on it.
                    Point position = new Point((pointROI.x + 10), (pointROI.y + rectROI.height - 25));
                    Imgproc.putText(matToCalculate, strROICV, position, Imgproc.FONT_HERSHEY_SIMPLEX, 2,
                            new Scalar(0, 0, 255), 3);
                    Imgproc.rectangle(matDst, rectROI, new Scalar(255, 255, 255), 3);
                    Imgproc.rectangle(matToCalculate, rectROI, new Scalar(0, 0, 255), 3);

                    if((matSourcePath != null) && matSourcePath.exists()){
                        File photoROICV = new File(matSourcePath.getParent(),
                                matSourcePath.getName().substring(
                                        0, matSourcePath.getName().lastIndexOf(".")) + "_ROI_CV.jpg");
                        Imgcodecs.imwrite(photoROICV.getAbsolutePath(), matToCalculate);

                        File photoROILaplacian = new File(matSourcePath.getParent(),
                                matSourcePath.getName().substring(
                                        0, matSourcePath.getName().lastIndexOf(".")) + "_ROI_Blur.jpg");
                        Imgcodecs.imwrite(photoROILaplacian.getAbsolutePath(), matDst);
                    }

                    matROIDst.release();
                    matROI.release();
                }
            }
            matDst.release();
        } else {
            //Calculate the CV of whole image
            Mat matDst = new Mat();
            Core.convertScaleAbs(calLaplacian(matToCalculate), matDst);
            String strCalculateCV = String.format(Locale.US, "Blur Index = %3.2f %%", calBlurIndex(matDst));
            mHandler.obtainMessage(MainActivity.MESSAGE_UPDATE_UI, strCalculateCV).sendToTarget();
            if((matSourcePath != null) && matSourcePath.exists()){
                File photoLaplacian = new File(matSourcePath.getParent(),
                        matSourcePath.getName().substring(
                                0, matSourcePath.getName().lastIndexOf(".")) + "_Blur.jpg");

                Point position = new Point(10, matDst.height()-100);
                Imgproc.putText(matDst, strCalculateCV, position, Imgproc.FONT_HERSHEY_SIMPLEX, 4,
                        new Scalar(255, 255, 255), 3);
                Imgcodecs.imwrite(photoLaplacian.getAbsolutePath(), matDst);
            }
            matDst.release();
        }

        matToCalculate.release();
    }

    private static Mat calLaplacian(Mat calLaplacian) {
        Mat matGrey = new Mat();
        // Reduce noise by blurring with a Gaussian filter ( kernel size = 3 )
        Imgproc.GaussianBlur(calLaplacian, matGrey,
                new Size(3, 3), 0, 0, Core.BORDER_DEFAULT );
        // Convert the image to grayscale
        Imgproc.cvtColor(matGrey, matGrey, Imgproc.COLOR_BGR2GRAY);

        //Laplacian operator
        Mat matLaplacian = new Mat();
        Imgproc.Laplacian(matGrey, matLaplacian, CvType.CV_16S, 3, 1.0, 0.0, Core.BORDER_DEFAULT);
        matGrey.release();

        return matLaplacian;
    }

    private static double calBlurIndex(Mat calIndex) {
        //Calculate mean, std
        MatOfDouble median = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        Core.meanStdDev(calIndex, median , std);

        //Calculate Coefficient of Variation as Blur index
        return ((std.get(0,0)[0]) / (median.get(0,0)[0]) * 100.0);
    }
}
