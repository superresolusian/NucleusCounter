import ij.*;
import ij.gui.NonBlockingGenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;

public class NucleusCounterGUI_ implements PlugIn {

    public String prefsHeader = null;
    protected Prefs prefs = new Prefs();

    NonBlockingGenericDialog gd, gdAnalyze;
    ImagePlus imp;
    String imageTitle;
    int nImages;
    String[] channelChoice = new String[]{"1", "2"};
    boolean getArea, getCentroid, getPerimeter, getEllipse, getCirc, getAR, getRound, getSolidity;
    boolean saveResults, saveImages, saveRoiSets;
    int nucleusChannel, cellChannel;

    String[] minSizeKeys = new String[]{"minSize1", "minSize2"};
    String[] maxSizeKeys = new String[]{"maxSize1", "maxSize2"};
    String[] minCircKeys = new String[]{"minCirc1", "minCirc2"};
    String[] maxCircKeys = new String[]{"maxCirc1", "maxCirc2"};

    double[] minSizes = new double[2];
    double[] maxSizes = new double[2];
    double[] minCircs = new double[2];
    double[] maxCircs = new double[2];

    boolean excludeEdge, includeHoles;

    String saveDir, roisDir, resultsDir, imagesDir;

    public void beforeSetupDialog() {
        imp = WindowManager.getCurrentImage();

        if(imp==null){
            String impPath = IJ.getFilePath("Choose image to load...");
            if (impPath == null || impPath.equals("")) return;
            imp = IJ.openImage(impPath);
        }

        if(imp.isComposite()){
            imp.hide();
            CompositeImage comp = new CompositeImage(imp, CompositeImage.COLOR);
            imp = comp;
            imp.show();
        }

        ImageStack ims = imp.getImageStack();
        nImages = ims.size();
        imageTitle = imp.getTitle();

        if(nImages!=2){
            IJ.error("Expected an image with two channels, this image only has "+nImages);
            imp = null;
        }
    }

    public void setupDialog(){
        gd = new NonBlockingGenericDialog("Measure nuclei per cell");
        gd.addMessage("Expected input is a two-channel thresholded image with cells in one slice and nuclei in the other");
        gd.addChoice("Cells are in frame...", channelChoice, getPrefs("cellChannel", channelChoice[0]));
        gd.addChoice("Nuclei are in frame...", channelChoice, getPrefs("nucleiChannel", channelChoice[1]));
        gd.addMessage("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        gd.addMessage("Select which measurements you want");
        gd.addCheckbox("Area", getPrefs("getArea", true));
        gd.addCheckbox("Central (x,y) coordinate in crop", getPrefs("getCentroid", false));
        gd.addCheckbox("Perimeter", getPrefs("getPerimeter", false));
        gd.addCheckbox("Fitted ellipse (major, minor axes and angle)", getPrefs("getEllipse", true));
        gd.addCheckbox("Circularity", getPrefs("getCirc", true));
        gd.addCheckbox("Aspect ratio", getPrefs("getAR", true));
        gd.addCheckbox("Roundness", getPrefs("getRound", false));
        gd.addCheckbox("Solidity", getPrefs("getSolidity", false));
        gd.addMessage("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        gd.addCheckbox("Save Roi sets?", getPrefs("saveRoiSets", saveRoiSets));
        gd.addCheckbox("Save results per cell?", getPrefs("saveResults", saveResults));
        gd.addCheckbox("Save individual cell crops?", getPrefs("saveImages", saveImages));
        //TODO: debug inverted image maybe
    }

    public void setupAnalyzeDialog(int c){
        gdAnalyze = new NonBlockingGenericDialog("Set up analyze particles for channel "+(c+1));
        gdAnalyze.addNumericField("Minimum size", getPrefs(minSizeKeys[c], 0), 0);
        gdAnalyze.addNumericField("Maximum size", getPrefs(maxSizeKeys[c], Double.POSITIVE_INFINITY), 0);
        gdAnalyze.addMessage("  ");
        gdAnalyze.addNumericField("Minimum circularity", getPrefs(minCircKeys[c], 0), 2);
        gdAnalyze.addNumericField("Maximum circularity", getPrefs(maxCircKeys[c], 1), 2);
        gdAnalyze.addMessage("  ");
        gdAnalyze.addCheckbox("Exclude edge particles?", getPrefs("excludeEdge", excludeEdge));
        gdAnalyze.addCheckbox("Include holes?", getPrefs("includeHoles", includeHoles));
    }

    public boolean loadSettings() {
        cellChannel = gd.getNextChoiceIndex() + 1;
        nucleusChannel = gd.getNextChoiceIndex() + 1;

        getArea = gd.getNextBoolean();
        getCentroid = gd.getNextBoolean();
        getPerimeter = gd.getNextBoolean();
        getEllipse = gd.getNextBoolean();
        getCirc = gd.getNextBoolean();
        getAR = gd.getNextBoolean();
        getRound = gd.getNextBoolean();
        getSolidity = gd.getNextBoolean();

        saveRoiSets = gd.getNextBoolean();
        saveResults = gd.getNextBoolean();
        saveImages = gd.getNextBoolean();

        setPrefs("cellChannel", channelChoice[cellChannel-1]);
        setPrefs("nucleusChannel", channelChoice[nucleusChannel-1]);

        setPrefs("getArea", getArea);
        setPrefs("getCentroid", getCentroid);
        setPrefs("getPerimeter", getPerimeter);
        setPrefs("getEllipse", getEllipse);
        setPrefs("getCirc", getCirc);
        setPrefs("getAR", getAR);
        setPrefs("getRound", getRound);
        setPrefs("getSolidity", getSolidity);

        setPrefs("saveRoiSets", saveRoiSets);
        setPrefs("saveResults", saveResults);
        setPrefs("saveImages", saveImages);
        return true;
    }

    public boolean loadAnalysisSettings(int c){
        minSizes[c] = gdAnalyze.getNextNumber();
        maxSizes[c] = gdAnalyze.getNextNumber();

        minCircs[c] = gdAnalyze.getNextNumber();
        maxCircs[c] = gdAnalyze.getNextNumber();

        excludeEdge = gdAnalyze.getNextBoolean();
        includeHoles = gdAnalyze.getNextBoolean();

        setPrefs(minSizeKeys[c], minSizes[c]);
        setPrefs(maxSizeKeys[c], maxSizes[c]);

        setPrefs(minCircKeys[c], minCircs[c]);
        setPrefs(maxCircKeys[c], maxCircs[c]);

        setPrefs("excludeEdge", excludeEdge);
        setPrefs("includeHoles", includeHoles);
        return true;
    }


    public void execute() throws IOException {
        if(saveRoiSets || saveResults || saveImages){
            DirectoryChooser directoryChooser = new DirectoryChooser("Choose save directory");
            String dir = directoryChooser.getDirectory();

            saveDir = makeDirectory(dir+File.separator+imageTitle+" - results");

            if(saveRoiSets) roisDir = makeDirectory(saveDir+File.separator+"local rois");
            if(saveResults) resultsDir = makeDirectory(saveDir+File.separator+"tables");
            if(saveImages) imagesDir = makeDirectory(saveDir+File.separator+"crops");
        }

        // actual processing
        ImageStack ims = imp.getImageStack();
        ImageProcessor ipCell = ims.getProcessor(cellChannel);
        ImageProcessor ipNucleus = ims.getProcessor(nucleusChannel);

        NucleusCounter nucleusCounter = new NucleusCounter(ipCell, ipNucleus);

        nucleusCounter.setSavePaths(saveDir, roisDir, resultsDir, imagesDir);

        imp.setSlice(cellChannel);
        IJ.showMessage("Set analyze particles options for cell channel");
        nucleusCounter.getCellRois();
        imp.setSlice(nucleusChannel);
        IJ.showMessage("Set analyze particles options for nucleus channel");
        nucleusCounter.getNucleusRois();

        nucleusCounter.matchNucleiToCells();
        nucleusCounter.analyseAllRois();
    }

    public void run() {run("");}

    @Override
    public void run(String s) {
        beforeSetupDialog();
        if(imp == null) return;

        setupDialog();

        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        }

        loadSettings();

        imp.setSlice(cellChannel);
        setupAnalyzeDialog(cellChannel-1);
        gdAnalyze.showDialog();
        if (gdAnalyze.wasCanceled()) {
            return;
        }
        loadAnalysisSettings(cellChannel-1);

        imp.setSlice(nucleusChannel);
        setupAnalyzeDialog(nucleusChannel-1);
        gdAnalyze.showDialog();
        if (gdAnalyze.wasCanceled()) {
            return;
        }
        loadAnalysisSettings(nucleusChannel-1);

        try {
            execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        prefs.savePreferences();
    }

    public static void main(String args[]){
        Class<?> clazz = NucleusCounterGUI_.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
        System.setProperty("plugins.dir", pluginsDir);

        new ij.ImageJ();
        //ImagePlus img1 = IJ.openImage("C:/Users/sianc/Code/NucleusCounter/src/main/resources/Composite.tif");
        //img1.show();

        IJ.runPlugIn(clazz.getName(),"");

    }

    public String makeDirectory(String target){
        File dir = new File(target);
        if (!dir.exists()){
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    // prefs handling, dull!

    public String getPrefs(String key, String defaultValue) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        return prefs.get(prefsHeader + "." + key, defaultValue);
    }

    public boolean getPrefs(String key, boolean defaultValue) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        return prefs.get(prefsHeader + "." + key, defaultValue);
    }

    public double getPrefs(String key, double defaultValue) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        return prefs.get(prefsHeader + "." + key, defaultValue);
    }

    public void setPrefs(String key, String value) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        prefs.set(prefsHeader + "." + key, value);
    }

    public void setPrefs(String key, boolean value) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        prefs.set(prefsHeader + "." + key, value);
    }

    public void setPrefs(String key, double value) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        prefs.set(prefsHeader + "." + key, value);
    }
}
