package com.camerax.opencv;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * author: Denny.CM
 * date:   2021/10/21
 */
class EdgeCalculation {

    public static void transEdgeImage(Mat imageMat) {
        Mat matGrey = new Mat();
        Imgproc.cvtColor(imageMat, matGrey, Imgproc.COLOR_BGR2GRAY);
        saveImage("Gray", matGrey);

        Mat matLaplacian = new Mat();
        //Imgproc.Laplacian(matGrey, matLaplacian,  10);
        Imgproc.Laplacian(matGrey, matLaplacian,  imageMat.depth(), 5, 1.0, 0.0);

        Mat absLaplacianMat = new Mat();
        Core.convertScaleAbs(matLaplacian, absLaplacianMat);
        saveImage("Laplacian", absLaplacianMat);

        matGrey.release();
        matLaplacian.release();
        absLaplacianMat.release();
    }

    private static void saveImage(String transType, Mat imageMatToSave) {
        File photoFile = new File(MainActivity.outputDirectory,
                new SimpleDateFormat(MainActivity.Configuration.FILENAME_FORMAT, Locale.US)
                        .format(MainActivity.photoTime) + "_"+transType+".jpg");

        Imgcodecs.imwrite(photoFile.getAbsolutePath(), imageMatToSave);
    }
}
