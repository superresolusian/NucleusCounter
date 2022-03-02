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

    NonBlockingGenericDialog gd;
    ImagePlus imp;
    String imageTitle;
    int nImages;
    String[] channelChoice = new String[]{"1", "2"};
    boolean saveResults, saveImages, saveRoiSets;
    int nucleusChannel, cellChannel;

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
        gd.addCheckbox("Save Roi sets?", getPrefs("saveRoiSets", saveRoiSets));
        gd.addCheckbox("Save results per cell?", getPrefs("saveResults", saveResults));
        gd.addCheckbox("Save individual cell crops?", getPrefs("saveImages", saveImages));
        //TODO: debug inverted image maybe
    }

    public boolean loadSettings() {
        cellChannel = gd.getNextChoiceIndex() + 1;
        nucleusChannel = gd.getNextChoiceIndex() + 1;

        saveRoiSets = gd.getNextBoolean();
        saveResults = gd.getNextBoolean();
        saveImages = gd.getNextBoolean();

        setPrefs("cellChannel", channelChoice[cellChannel-1]);
        setPrefs("nucleusChannel", channelChoice[nucleusChannel-1]);
        setPrefs("saveRoiSets", saveRoiSets);
        setPrefs("saveResults", saveResults);
        setPrefs("saveImages", saveImages);
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

    public void setPrefs(String key, String value) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        prefs.set(prefsHeader + "." + key, value);
    }

    public void setPrefs(String key, boolean value) {
        if (prefsHeader == null) prefsHeader = this.getClass().getName();
        prefs.set(prefsHeader + "." + key, value);
    }

}
