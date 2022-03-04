import ij.*;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.DirectoryChooser;
import ij.io.RoiEncoder;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import sun.awt.image.ImageWatched;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.lang.StrictMath.sqrt;
import static java.lang.StrictMath.toDegrees;
import static org.apache.commons.math3.stat.StatUtils.mean;
import static org.apache.commons.math3.stat.StatUtils.variance;

public class NucleusCounter {

    private ImageProcessor ipCell, ipNuclei;
    private ImagePlus imp;
    private ImageStack ims;
    private RoiManager rm;
    private ArrayList<String> columns;
    private int nMeasurements;
    private Roi[] cellRois, nucleusRois;
    private int nCellRois, nNucleusRois;
    private LinkedHashMap<String, double[]> nucleusMeasurements, cellMeasurements;
    private LinkedHashMap<Roi, Point> nucleusRoisAndCentres;
    private LinkedHashMap<Roi, Roi[]> cellNucleusMap;
    private LinkedHashMap<Integer, int[]> cellNucleusMap_v2;
    private LinkedHashMap<String, double[]> summaryMeanMap, summaryStdMap;
    private String saveDir = null, roiDir = null, resultsDir = null, cropsDir = null;
    private boolean saveRois = false, saveResults = false, saveCrops = false;
    private double[] minSize = new double[2], maxSize = new double[2], minCirc = new double[2], maxCirc = new double[2];
    private boolean[] excludeEdges = new boolean[2], includeHoles = new boolean[2];

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

    public void setSavePaths(String saveDir, String roiDir, String resultsDir, String cropsDir){
        this.saveDir = saveDir;
        this.resultsDir = resultsDir;
        this.cropsDir = cropsDir;
        this.roiDir = roiDir;

        if(this.roiDir!=null) this.saveRois = true;
        if(this.resultsDir!=null) this.saveResults = true;
        if(this.cropsDir!=null) this.saveCrops = true;
    }

    public void getNucleusRois() {
        nucleusRois = getRois(ipNuclei);
        nucleusRoisAndCentres = new LinkedHashMap<>();
        for(Roi r:nucleusRois){
            nucleusRoisAndCentres.put(r, getRoiCentre(r));
        }
    }

    public void getCellRois() {
        cellRois = getRois(ipCell);
        nNucleiPerCell = new double[cellRois.length];
        nucleusAreaPerCell = new double[cellRois.length];
        nucleusAreaStdPerCell = new double[cellRois.length];
    }

    private Roi[] getRois(ImageProcessor ip){
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

    public void getNucleusRois(double minSize, double maxSize, double minCirc, double maxCirc, boolean excludeEdge, boolean includeHoles){
        TableScraper ts = new TableScraper(ipNuclei);
        ts.setOptions(true, excludeEdge, includeHoles);
        ts.setConstraints(minSize, maxSize, minCirc, maxCirc);
        Object[] nucleiOutput = ts.getRois();

        nucleusRois = (Roi[]) nucleiOutput[0];
        nNucleusRois = nucleusRois.length;
        nucleusRoisAndCentres = new LinkedHashMap<>();
        for(Roi r:nucleusRois){
            nucleusRoisAndCentres.put(r, getRoiCentre(r));
        }

        ResultsTable rt = (ResultsTable) nucleiOutput[1];

        nucleusMeasurements = getArraysFromRt(columns, rt);
    }

    public void getCellRois(double minSize, double maxSize, double minCirc, double maxCirc, boolean excludeEdge, boolean includeHoles){
        TableScraper ts = new TableScraper(ipCell);
        ts.setOptions(true, excludeEdge, includeHoles);
        ts.setConstraints(minSize, maxSize, minCirc, maxCirc);
        Object[] cellOutput = ts.getRois();

        cellRois = (Roi[]) cellOutput[0];
        ResultsTable rt = (ResultsTable) cellOutput[1];
        nCellRois = cellRois.length;
        nNucleiPerCell = new double[nCellRois];
        cellMeasurements = getArraysFromRt(columns, rt);
    }

    private LinkedHashMap<String, double[]> getArraysFromRt(ArrayList<String> headers, ResultsTable rt){
        LinkedHashMap<String, double[]> out = new LinkedHashMap<>();
        for(String h:headers){
            int i = rt.getColumnIndex(h);
            if (i==-1) IJ.log("WARN: could not find column "+h+" in results table...");
            double[] results = rt.getColumnAsDoubles(i);
            out.put(h, results);
        }
        return out;
    }

    public void setMeasurements(boolean getArea, boolean getCentroid, boolean getPerimeter, boolean getEllipse,
                                boolean getCirc, boolean getAR, boolean getRound, boolean getSolidity){
        columns = new ArrayList<>();
        if(getArea) columns.add("Area");
        if(getCentroid){
            columns.add("X");
            columns.add("Y");
        }
        if(getPerimeter) columns.add("Perim.");
        if(getEllipse){
            columns.add("Major");
            columns.add("Minor");
            columns.add("Angle");
        }
        if(getCirc) columns.add("Circ.");
        if(getAR) columns.add("AR");
        if(getRound) columns.add("Round");
        if(getSolidity) columns.add("Solidity");

        nMeasurements = columns.size();

        summaryMeanMap = new LinkedHashMap<>();
        summaryStdMap = new LinkedHashMap<>();
    }

    public void setOptions(boolean isCell,
                           double minSize, double maxSize, double minCirc, double maxCirc,
                           boolean excludeEdges, boolean includeHoles){
        int i = isCell ? 0 : 1;

        this.minSize[i] = minSize;
        this.maxSize[i] = maxSize;
        this.minCirc[i] = minCirc;
        this.maxCirc[i] = maxCirc;
        this.excludeEdges[i] = excludeEdges;
        this.includeHoles[i] = includeHoles;
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

    public void matchNucleiToCells_v2(){
        cellNucleusMap_v2 = new LinkedHashMap<>();

        for(int i=0; i<nCellRois; i++){
            Roi c = cellRois[i];
            ArrayList<Integer> contained = new ArrayList<>();

            for(int j=0; j<nNucleusRois; j++){
                Roi n = nucleusRois[j];
                Point nC = nucleusRoisAndCentres.get(n);
                if(c.contains(nC.x, nC.y)){
                    contained.add(j);
                }
            }

            int[] containedNucleiIndices = contained.stream().mapToInt(x->x).toArray();
            cellNucleusMap_v2.put(i, containedNucleiIndices);
        }
    }

    private void analyseCrop_v2(int n, boolean exportResults, boolean exportCrops) throws IOException {
        Roi cellRoi = cellRois[n];
        int[] containedNucleiIndices = cellNucleusMap_v2.get(n);
        Rectangle rect = cellRoi.getBounds();

        Overlay overlay = new Overlay();

        ResultsTable rt = new ResultsTable();

        int nNuclei = containedNucleiIndices.length;
        LinkedHashMap<String, double[]> containedNucleiMeasurements = new LinkedHashMap<>();
        for(String c:columns) containedNucleiMeasurements.put(c, new double[nNuclei]);

        for(int i=0; i<nNuclei; i++){
            rt.incrementCounter();

            int ci = containedNucleiIndices[i];
            Roi r = nucleusRois[ci];

            for(String h:columns){
                double[] measurement = containedNucleiMeasurements.get(h);
                double thisMeasurement = nucleusMeasurements.get(h)[ci];
                measurement[i] = thisMeasurement;
                containedNucleiMeasurements.put(h, measurement);
                if(exportResults){
                    rt.addValue("Nucleus name", r.getName());
                    if(h=="X") rt.addValue(h, thisMeasurement-rect.x);
                    else if(h=="Y") rt.addValue(h, thisMeasurement-rect.y);
                    else rt.addValue(h, thisMeasurement);
                }
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

        for(String h:columns){
            if(h=="X" || h=="Y") continue;

            double[] summaryMeanMeasurements, summaryStdMeasurements;
            if(!summaryMeanMap.containsKey(h)){
                summaryMeanMeasurements = new double[nCellRois];
                summaryStdMeasurements = new double[nCellRois];
            }
            else{
                summaryMeanMeasurements = summaryMeanMap.get(h);
                summaryStdMeasurements = summaryStdMap.get(h);
            }

            summaryMeanMeasurements[n] = mean(containedNucleiMeasurements.get(h));
            summaryStdMeasurements[n] = sqrt(variance(containedNucleiMeasurements.get(h)));

            summaryMeanMap.put(h, summaryMeanMeasurements);
            summaryStdMap.put(h, summaryStdMeasurements);
        }

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

        if(saveRois){
            String path = roiDir+File.separator+cellRoi.getName()+"-RoiSet.zip";
            Roi[] allRois = new Roi[nNuclei+1];
            allRois[0] = cellRoi;
            for(int i=0; i<nNuclei; i++) allRois[i+1] = nucleusRois[containedNucleiIndices[i]];
            roiSaver(allRois, path);
        }
    }

    private void analyseCrop(int n, boolean exportResults, boolean exportCrops) throws IOException {
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
                rt.addValue("Centre x", centre.x-rect.x);
                rt.addValue("Centre y", centre.y-rect.y);
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

        if(saveRois){
            String path = roiDir+File.separator+cellRoi.getName()+"-RoiSet.zip";
            Roi[] allRois = new Roi[nNuclei+1];
            allRois[0] = cellRoi;
            for(int i=0; i<nNuclei; i++) allRois[i+1] = containedNuclei[i];
            roiSaver(allRois, path);
        }
    }

    public void analyseAllRois() throws IOException {
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

    public void analyseAllRois_v2() throws IOException {
        ResultsTable rt = new ResultsTable();

        for(int i=0; i<cellRois.length; i++){
            IJ.showProgress(i+1, nCellRois);
            IJ.showStatus("Working on cell "+(i+1)+" of "+nCellRois);
            analyseCrop_v2(i, saveResults, saveCrops);

            rt.incrementCounter();
            rt.addValue("Cell name", cellRois[i].getName());
            rt.addValue("N nuclei in cell", nNucleiPerCell[i]);
            for(String h:columns){
                if(!summaryMeanMap.containsKey(h)) continue;
                rt.addValue(h+" mean", summaryMeanMap.get(h)[i]);
                rt.addValue(h+" std", summaryStdMap.get(h)[i]);
            }
        }

        rt.show("Summary Results");

        RoiManager thisManager = RoiManager.getInstance();
        if(thisManager!=null){
            rm = thisManager;
            rm.close();
        }
    }


    public static void main(String[] args) throws IOException {
        new ImageJ();

        NucleusCounter nc = new NucleusCounter();
        nc.loadTestData();

        DirectoryChooser directoryChooser = new DirectoryChooser("Choose save directory");
        String dir = directoryChooser.getDirectory();

        String save = makeDirectory(dir+ File.separator+"image");
        String rois = makeDirectory(save+File.separator+"local rois");
        String results = makeDirectory(save+File.separator+"tables");
        String crops = makeDirectory(save+File.separator+"crops");

        nc.setSavePaths(save, rois, results, crops);

        nc.setMeasurements(true, true, true, true, true, true, true, true);

        //set cell options
        nc.getCellRois(200, Double.POSITIVE_INFINITY, 0, 1, true, true);
        //set nuclei options
        nc.getNucleusRois(50, Double.POSITIVE_INFINITY, 0, 1, true, true);

        nc.matchNucleiToCells_v2();

        nc.analyseAllRois_v2();
    }

    public static String makeDirectory(String target){
        File dir = new File(target);
        if (!dir.exists()){
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    private void roiSaver(Roi[] rois, String path) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
        RoiEncoder re = new RoiEncoder(out);

        for(int i = 0; i < rois.length; i++) {
            Roi roi = rois[i];
            String label = roi.getName();

            if (!label.endsWith(".roi")) {
                label = label + ".roi";
            }

            zos.putNextEntry(new ZipEntry(label));
            re.write(roi);
            out.flush();

        }

        out.close();
    }
}
