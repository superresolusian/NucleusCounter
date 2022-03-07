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
    String[] excludeEdgeKeys = new String[]{"exclude1", "exclude2"};
    String[] includeHolesKeys = new String[]{"include1", "include2"};

    double[] minSizes = new double[2];
    double[] maxSizes = new double[2];
    double[] minCircs = new double[2];
    double[] maxCircs = new double[2];

    boolean[] excludeEdge = new boolean[2];
    boolean[] includeHoles = new boolean[2];

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
        gd.addCheckbox("Save results per cell?", getPrefs("saveResults", saveResults));
        gd.addCheckbox("Save individual cell crops?", getPrefs("saveImages", saveImages));
        gd.addCheckbox("Save Roi sets?", getPrefs("saveRoiSets", saveRoiSets));
        //TODO: debug inverted image maybe
    }

    public void setupAnalyzeDialog(int c){
        gdAnalyze = new NonBlockingGenericDialog("Set up analyze particles for channel "+(c+1));
        gdAnalyze.addMessage("Please check settings for analysing particles in channel "+(c+1));
        gdAnalyze.addNumericField("Minimum size (in pixels)", getPrefs(minSizeKeys[c], 0), 0);
        gdAnalyze.addNumericField("Maximum size (in pixels)", getPrefs(maxSizeKeys[c], Double.POSITIVE_INFINITY), 0);
        gdAnalyze.addMessage("  ");
        gdAnalyze.addNumericField("Minimum circularity", getPrefs(minCircKeys[c], 0), 2);
        gdAnalyze.addNumericField("Maximum circularity", getPrefs(maxCircKeys[c], 1), 2);
        gdAnalyze.addMessage("  ");
        gdAnalyze.addCheckbox("Exclude edge particles?", getPrefs(excludeEdgeKeys[c], excludeEdge[c]));
        gdAnalyze.addCheckbox("Include holes?", getPrefs(includeHolesKeys[c], includeHoles[c]));
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

        saveResults = gd.getNextBoolean();
        saveImages = gd.getNextBoolean();
        saveRoiSets = gd.getNextBoolean();

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

        setPrefs("saveResults", saveResults);
        setPrefs("saveImages", saveImages);
        setPrefs("saveRoiSets", saveRoiSets);
        return true;
    }

    public boolean loadAnalysisSettings(int c){
        minSizes[c] = gdAnalyze.getNextNumber();
        maxSizes[c] = gdAnalyze.getNextNumber();

        minCircs[c] = gdAnalyze.getNextNumber();
        maxCircs[c] = gdAnalyze.getNextNumber();

        excludeEdge[c] = gdAnalyze.getNextBoolean();
        includeHoles[c] = gdAnalyze.getNextBoolean();

        setPrefs(minSizeKeys[c], minSizes[c]);
        setPrefs(maxSizeKeys[c], maxSizes[c]);

        setPrefs(minCircKeys[c], minCircs[c]);
        setPrefs(maxCircKeys[c], maxCircs[c]);

        setPrefs(excludeEdgeKeys[c], excludeEdge[c]);
        setPrefs(includeHolesKeys[c], includeHoles[c]);
        return true;
    }


    public void execute() throws IOException {
        if(saveRoiSets || saveResults || saveImages){
            DirectoryChooser directoryChooser = new DirectoryChooser("Choose save directory");
            String dir = directoryChooser.getDirectory();

            if(dir==null){
                IJ.error("Oops! No valid save directory selected :(");
                throw new IOException("No valid save directory selected...");
            }

            saveDir = makeDirectory(dir+File.separator+imageTitle+" - results");

            if(saveRoiSets) roisDir = makeDirectory(saveDir+File.separator+"local rois");
            if(saveResults) resultsDir = makeDirectory(saveDir+File.separator+"tables");
            if(saveImages) imagesDir = makeDirectory(saveDir+File.separator+"crops");
        }

        // actual processing
        ImageStack ims = imp.getImageStack();
        ImageProcessor ipCell = ims.getProcessor(cellChannel);
        ImageProcessor ipNucleus = ims.getProcessor(nucleusChannel);

        NucleusCounter nucleusCounter = new NucleusCounter(imp, cellChannel, nucleusChannel);

        nucleusCounter.setSavePaths(saveDir, roisDir, resultsDir, imagesDir);
        nucleusCounter.setMeasurements(getArea, getCentroid, getPerimeter, getEllipse, getCirc, getAR, getRound, getSolidity);

        nucleusCounter.getCellRois(minSizes[0], maxSizes[0], minCircs[0], maxCircs[0], excludeEdge[0], includeHoles[0]);
        nucleusCounter.getNucleusRois(minSizes[1], maxSizes[1], minCircs[1], maxCircs[1], excludeEdge[1], includeHoles[1]);

        nucleusCounter.matchNucleiToCells_v2();
        nucleusCounter.analyseAllRois_v2();
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
