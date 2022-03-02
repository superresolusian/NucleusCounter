import ij.*;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.LutLoader;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.math3.stat.StatUtils.mean;
import static org.apache.commons.math3.stat.StatUtils.variance;

public class NucleusCounter_ {

    private static ImageProcessor ipCell, ipNuclei;
    private static ImagePlus imp;
    private static ImageStack ims;
    private static RoiManager rm;
    private static Roi[] cellRois, nucleusRois;
    private static LinkedHashMap<Roi, Point> nucleusRoisAndCentres;
    private static LinkedHashMap<Roi, Roi[]> cellNucleusMap;
    private static String savePath;

    private static double[] nNucleiPerCell;
    private static double[] nucleusAreaPerCell;


    private static void loadTestData(){
        ImagePlus img1 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Cell.tif");
        ipCell = img1.getProcessor();
        ImagePlus img2 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Nuclei.tif");
        ipNuclei = img2.getProcessor();

        ims = new ImageStack(ipCell.getWidth(), ipCell.getHeight());
        ims.addSlice(ipCell);
        ims.addSlice(ipNuclei);

        imp = new ImagePlus("test data", ims);
    }

    private static void setSavePath(){
        savePath = "C:/Users/sianc/Code/NucleusCounter/src/main/output";
    }

    private static void getNucleusRois(){
        imp.setSlice(2);
        nucleusRois = getRois(ipNuclei);
        nucleusRoisAndCentres = new LinkedHashMap<>();
        for(Roi r:nucleusRois){
            nucleusRoisAndCentres.put(r, getRoiCentre(r));
        }
    }

    private static void getCellRois(){
        imp.setSlice(1);
        cellRois = getRois(ipCell);
        nNucleiPerCell = new double[cellRois.length];
        nucleusAreaPerCell = new double[cellRois.length];
    }

    private static Roi[] getRois(ImageProcessor ip){
        RoiManager thisManager = RoiManager.getInstance();
        if(thisManager!=null){
            rm = thisManager;
            rm.close();
        }
        rm = new RoiManager();

        ParticleAnalyzer pa = new ParticleAnalyzer();
        pa.setRoiManager(rm);
        pa.showDialog();

        if(!ip.isInvertedLut()) ip.invertLut();

        pa.analyze(new ImagePlus("", ip));

        Roi[] rois = rm.getRoisAsArray();

        rm.reset();
        ResultsTable thisResults = ResultsTable.getResultsTable();
        thisResults.reset();
        return rois;
    }

    private static Point getRoiCentre(Roi r){

        Point[] containedPoints = r.getContainedPoints();
        int nContainedPoints = containedPoints.length;
        double xc = 0, yc = 0;
        for(Point pt:containedPoints){
            xc += (double) pt.x/nContainedPoints;
            yc += (double) pt.y/nContainedPoints;
        }
        Point centroid = new Point((int) xc, (int) yc);
        return centroid;
    }

    private static void matchNucleiToCells(){
        cellNucleusMap = new LinkedHashMap<>();

        for(Roi c:cellRois){
            ArrayList<Roi> contained = new ArrayList<>();

            for(Roi n:nucleusRois){
                Point nC = nucleusRoisAndCentres.get(n);
                if(c.contains(nC.x, nC.y)){
                    contained.add(n);
                }
            }
            Roi[] object = new Roi[0];
            Roi[] containedNuclei = contained.toArray(object);
            cellNucleusMap.put(c, containedNuclei);
        }

    }

    private static void analyseCrop(int n, boolean exportResults, boolean exportImage){
        Roi cellRoi = cellRois[n];
        Roi[] containedNuclei = cellNucleusMap.get(cellRoi);
        Rectangle rect = cellRoi.getBounds();

        Overlay overlay = new Overlay();

        ResultsTable rt = new ResultsTable();

        int nNuclei = containedNuclei.length;
        double averageArea = 0;

        for(Roi r:containedNuclei){
            double area = r.getContainedPoints().length; // NOTE in pixels //TODO: calibrate
            averageArea += area/nNuclei;
            if(exportResults){
                Point centre = nucleusRoisAndCentres.get(r);
                rt.incrementCounter();
                rt.addValue("Centre x", centre.x);
                rt.addValue("Centre y", centre.y);
                rt.addValue("Area", area);
            }
            if(exportImage){
                r.setLocation(r.getBounds().x-rect.x, r.getBounds().y-rect.y);
                r.setPosition(2);
                r.setStrokeColor(Color.blue);
                r.setStrokeWidth(1);
                overlay.add(r);
            }
        }
        if(exportResults) rt.save(savePath+"/"+cellRoi.getName()+".csv");

        nNucleiPerCell[n] = nNuclei;
        nucleusAreaPerCell[n] = averageArea;

        if(exportImage){
            ipCell.setRoi(rect);
            ImageProcessor ipCellCrop = ipCell.crop();
            ipNuclei.setRoi(rect);
            ImageProcessor ipNucleiCrop = ipNuclei.crop();

            cellRoi.setLocation(cellRoi.getBounds().x- rect.x, cellRoi.getBounds().y -rect.y);
            cellRoi.setPosition(1);
            cellRoi.setStrokeColor(Color.white);
            cellRoi.setStrokeWidth(1);
            overlay.add(cellRoi);

            ImageStack imsCrop = new ImageStack(rect.width, rect.height);
            imsCrop.addSlice(ipCellCrop);
            imsCrop.addSlice(ipNucleiCrop);
            ImagePlus impCrop = new ImagePlus("crop", imsCrop);
            impCrop.setOverlay(overlay);
            CompositeImage compositeImage = new CompositeImage(impCrop, CompositeImage.COMPOSITE);
            IJ.saveAsTiff(compositeImage, savePath+"/"+cellRoi.getName());

        }
    }

    public static void main(String[] args){
        new ImageJ();

        loadTestData();
        imp.show();

        getCellRois();
        getNucleusRois();

        matchNucleiToCells();

        setSavePath();

        ResultsTable rt = new ResultsTable();

        for(int i=0; i<cellRois.length; i++){
            IJ.showProgress(i+1, cellRois.length);
            IJ.showStatus("Working on cell "+(i+1)+" of "+cellRois.length);
            analyseCrop(i, true, true);

            rt.incrementCounter();
            rt.addValue("Cell name", cellRois[i].getName());
            rt.addValue("Cell area", cellRois[i].getContainedPoints().length);
            rt.addValue("N nuclei in cell", nNucleiPerCell[i]);
            rt.addValue("Mean nucleus area", nucleusAreaPerCell[i]);
        }

        rt.show("Results");


    }


}
