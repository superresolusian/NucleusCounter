import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class TableScraper implements Measurements{

    RoiManager rm;
    ResultsTable rt;
    private ImageProcessor ip;
    private Calibration calibration = null;
    double minSize, maxSize, minCirc, maxCirc;
    private int measurements = ALL_STATS, options;

    public TableScraper(){
        ImagePlus img1 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Nuclei.tif");
        ImageProcessor ipCell = img1.getProcessor();
        this.ip = ipCell;
    }

    public TableScraper(ImageProcessor ip, Calibration calibration){
        this.ip = ip;
        this.calibration = calibration;
    }

    public void setMeasurements(boolean measureArea, boolean measureCoM, boolean measureCircularity,
                                    boolean measureEllipse, boolean measurePerimeter, boolean measureDescriptors){
        int area = measureArea ? AREA : 0;
        int com = measureCoM ? CENTER_OF_MASS : 0;
        int circularity = measureCircularity ? CIRCULARITY : 0;
        int ellipse = measureEllipse ? ELLIPSE : 0;
        int perimeter = measurePerimeter ? PERIMETER : 0;
        int descriptors = measureDescriptors ? SHAPE_DESCRIPTORS: 0;

        this.measurements = area + com + circularity + ellipse + perimeter + descriptors;
    }

    public void setOptions(boolean addManager, boolean excludeEdge, boolean includeHoles){
        ParticleAnalyzer pa = new ParticleAnalyzer();
        int add = addManager ? pa.ADD_TO_MANAGER : 0;
        int exclude = excludeEdge ? pa.EXCLUDE_EDGE_PARTICLES : 0;
        int include = includeHoles ? pa.INCLUDE_HOLES : 0;

        this.options = add + exclude + include + pa.CLEAR_WORKSHEET + pa.SHOW_NONE;
    }

    public void setConstraints(double minSize, double maxSize, double minCirc, double maxCirc){
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.minCirc = minCirc;
        this.maxCirc = maxCirc;
    }

    public Object[] getRois(){
        return getRois_v2(ip, options, ALL_STATS, minSize, maxSize, minCirc, maxCirc);
    }

    private Object[] getRois_v2(ImageProcessor ip,
                             int options, int measurements,
                             double minSize, double maxSize,
                             double minCirc, double maxCirc){
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


        ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt, minSize, maxSize, minCirc, maxCirc);
        pa.setRoiManager(rm);

        if(!ip.isInvertedLut()) ip.invertLut();

        ImagePlus analysisImp = new ImagePlus("", ip);
        analysisImp.setCalibration(calibration);
        pa.analyze(analysisImp);

        Roi[] rois = rm.getRoisAsArray();

        rm.reset();

        return new Object[]{rois, rt};

    }

    public static void main (String[] args){
        new ImageJ();

        TableScraper ts = new TableScraper();
        ts.setMeasurements(true,true,true,true,true,true);
        ts.setOptions(true, true, true);
        ts.setConstraints(50, Double.POSITIVE_INFINITY, 0, 1);
        Object[] out = ts.getRois();
        Roi[] rois = (Roi[]) out[0];
        ResultsTable rt = (ResultsTable) out[1];

        rt.show("results");

    }

}
