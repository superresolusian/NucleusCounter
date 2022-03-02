import ij.*;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.DirectoryChooser;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static java.lang.StrictMath.sqrt;
import static org.apache.commons.math3.stat.StatUtils.mean;
import static org.apache.commons.math3.stat.StatUtils.variance;

public class NucleusCounter {

    private ImageProcessor ipCell, ipNuclei;
    private ImagePlus imp;
    private ImageStack ims;
    private RoiManager rm;
    private Roi[] cellRois, nucleusRois;
    private LinkedHashMap<Roi, Point> nucleusRoisAndCentres;
    private LinkedHashMap<Roi, Roi[]> cellNucleusMap;
    private String saveDir = null, resultsDir = null, cropsDir = null;
    private boolean saveRois = false, saveResults = false, saveCrops = false;

    private double[] nNucleiPerCell;
    private double[] nucleusAreaPerCell;
    private double[] nucleusAreaStdPerCell;

    public NucleusCounter(){
        loadTestData();
    }

    public NucleusCounter(ImageProcessor ipCell, ImageProcessor ipNuclei){
        this.ipCell = ipCell;
        this.ipNuclei = ipNuclei;
    }

    private void loadTestData(){
        ImagePlus img1 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Cell.tif");
        ipCell = img1.getProcessor();
        ImagePlus img2 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Nuclei.tif");
        ipNuclei = img2.getProcessor();

        ims = new ImageStack(ipCell.getWidth(), ipCell.getHeight());
        ims.addSlice(ipCell);
        ims.addSlice(ipNuclei);

        imp = new ImagePlus("test data", ims);
        imp.show();
    }

    public void setSavePaths(String saveDir, String resultsDir, String cropsDir){
        this.saveDir = saveDir;
        this.resultsDir = resultsDir;
        this.cropsDir = cropsDir;

        if(this.saveDir!=null) this.saveRois = true;
        if(this.resultsDir!=null) this.saveResults = true;
        if(this.cropsDir!=null) this.saveCrops = true;
    }

    public void getNucleusRois(){
        nucleusRois = getRois(ipNuclei, false);
        nucleusRoisAndCentres = new LinkedHashMap<>();
        for(Roi r:nucleusRois){
            nucleusRoisAndCentres.put(r, getRoiCentre(r));
        }
    }

    public void getCellRois(){
        cellRois = getRois(ipCell, saveRois);
        nNucleiPerCell = new double[cellRois.length];
        nucleusAreaPerCell = new double[cellRois.length];
        nucleusAreaStdPerCell = new double[cellRois.length];
    }

    private Roi[] getRois(ImageProcessor ip, boolean saveRois){
        RoiManager thisManager = RoiManager.getInstance();
        if(thisManager!=null){
            rm = thisManager;
            rm.close();
        }
        rm = new RoiManager();

        ResultsTable rt = ResultsTable.getResultsTable();
        if(rt!=null){
            rt.reset();
        }
        rt = new ResultsTable();

        ParticleAnalyzer pa = new ParticleAnalyzer();
        pa.setRoiManager(rm);
        pa.setResultsTable(rt);

        pa.showDialog();

        if(!ip.isInvertedLut()) ip.invertLut();

        pa.analyze(new ImagePlus("", ip));

        Roi[] rois = rm.getRoisAsArray();

        rm.reset();

        return rois;
    }

    private Point getRoiCentre(Roi r){

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

    public void matchNucleiToCells(){
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

    private void analyseCrop(int n, boolean exportResults, boolean exportCrops){
        Roi cellRoi = cellRois[n];
        Roi[] containedNuclei = cellNucleusMap.get(cellRoi);
        Rectangle rect = cellRoi.getBounds();

        Overlay overlay = new Overlay();

        ResultsTable rt = new ResultsTable();

        int nNuclei = containedNuclei.length;
        double[] areas = new double[nNuclei];

        for(int i=0; i<nNuclei; i++){
            Roi r = containedNuclei[i];
            double area = r.getContainedPoints().length; // NOTE in pixels //TODO: calibrate
            areas[i] = area;
            if(exportResults){
                Point centre = nucleusRoisAndCentres.get(r);
                rt.incrementCounter();
                rt.addValue("Centre x", centre.x);
                rt.addValue("Centre y", centre.y);
                rt.addValue("Area", area);
            }
            if(exportCrops){
                r.setLocation(r.getBounds().x-rect.x, r.getBounds().y-rect.y);
                r.setPosition(2);
                r.setStrokeColor(Color.blue);
                r.setStrokeWidth(1);
                overlay.add(r);
            }
        }
        if(exportResults) rt.save(resultsDir+File.separator+cellRoi.getName()+".csv");

        nNucleiPerCell[n] = nNuclei;
        nucleusAreaPerCell[n] = mean(areas);
        nucleusAreaStdPerCell[n] = sqrt(variance(areas));

        if(exportCrops){
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
            IJ.saveAsTiff(compositeImage, cropsDir+File.separator+cellRoi.getName());

        }
    }

    public void analyseAllRois(){
        ResultsTable rt = new ResultsTable();

        for(int i=0; i<cellRois.length; i++){
            IJ.showProgress(i+1, cellRois.length);
            IJ.showStatus("Working on cell "+(i+1)+" of "+cellRois.length);
            analyseCrop(i, saveResults, saveCrops);

            rt.incrementCounter();
            rt.addValue("Cell name", cellRois[i].getName());
            rt.addValue("Cell area", cellRois[i].getContainedPoints().length);
            rt.addValue("N nuclei in cell", nNucleiPerCell[i]);
            rt.addValue("Mean nucleus area", nucleusAreaPerCell[i]);
            rt.addValue("Std nucleus area", nucleusAreaStdPerCell[i]);
        }

        rt.show("Results");

        RoiManager thisManager = RoiManager.getInstance();
        if(thisManager!=null){
            rm = thisManager;
            rm.close();
        }
    }


    public static void main(String[] args){
        new ImageJ();

        NucleusCounter nc = new NucleusCounter();
        nc.loadTestData();

        DirectoryChooser directoryChooser = new DirectoryChooser("Choose save directory");
        String dir = directoryChooser.getDirectory();

        String save = makeDirectory(dir+ File.separator+"image");
        String results = makeDirectory(save+File.separator+"tables");
        String crops = makeDirectory(save+File.separator+"crops");

        nc.setSavePaths(save, results, crops);

        nc.getCellRois();
        nc.getNucleusRois();

        nc.matchNucleiToCells();

        nc.analyseAllRois();
    }

    public static String makeDirectory(String target){
        File dir = new File(target);
        if (!dir.exists()){
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }
}
