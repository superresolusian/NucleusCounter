# NucleusCounter
This is a plugin for counting and measuring 'nuclei' within 'cells' in images.

## Installation
Windows: Navigate to the Fiji.app folder where you installed Fiji, and put the `nucleuscounter_.jar` file from this repository into the plugins folder.
Mac: In Applications, right-click on Fiji and click 'Show package contents'. Then put the `nucleuscounter_.jar` file from this repository into the plugins folder.
Once installed, the menu option 'NucleusCounterGUI' should become available in the Plugins menu (if Fiji was already open when you installed the .jar file, you'll have to restart Fiji to see it).

## Input
The expected input data is a **two-channel binary thresholded image** where one channel is for the cells (or, more generally, the containing objects) and the other channel is for the nuclei (or the objects contained within the other channel). The plugin will run on the current active image, or will prompt you to open an image if none are currently open.
Multiple time-points/z-positions are not currently supported, nor are label images where each object has a different label number (i.e. not binary).
![Example input data](/imgs/input_data.png "Example input data as displayed in Fiji. Cells are in red, nuclei are in green")


## Measurement options
The first dialog box will bring up options for what measurements you want to extract and what you want to save. The open image will also be displayed with separately channels, rather than as a composite image, at this stage.
![ImageJ dialog box](/imgs/dialog1.png "Measurement options dialog box")
* "Cells are in frame..." = select which channel/frame the cells are in
* "Nuclei are in frame..." = select which channel/frame the nuclei are in
* Measurement options = tick which of the measurements you want to make for objects in the nucleus channel. Nucleus count per cell is always performed.
* Save options:
	- "Save results per cell?" = for each cell, a .csv file containing the individual measurements of each contained nucleus will be saved
	- "Save individual cell crops?" = for each cell, a .tif file of the original image cropped to the cell boundary will be saved.
	- "Save Roi sets?" = for each cell, a .zip file containing the cell and contained nuclei Rois will be saved. This will be relative to the *cropped cells*, not the original image - therefore, it doesn't really make sense to select this option unless you selected the previous 'save cropped' option.
	
## Analysis options
![ImageJ pared down Analyze Particles dialog box](/imgs/dialog2.png "Analyze particles dialog box")
For each channel a dialog box will pop up asking for the settings to be used for that channel in the ImageJ 'Analyze particles' function. Different values can be set for each channel.
The dialog box won't stop you from interacting with the image, so you can still do things like draw Rois on the image to estimate correct values for these parameters.

## Save location
If you selected any of the save options in the first dialog box, then a window will appear asking you to select a folder where you want to save the plugin output.
![Screenshots from Windows Explorer](/imgs/save_structure.png "Master save folder created by plugin (right) and the subfolders for different save options (left)")
If all the save options are saved, then a new folder will be created in the chosen save location, which has the name '<Image name> - results'.
If you selected "Save Roi sets?", a folder will be created called called 'local rois'; "Save results per cell?" will create a folder called 'tables'; "Save individual crops?" will create a folder called "crops".

## Output
Regardless of whether you select any save options or not, the plugin will always create an ImageJ results table containing cell area (calibrated), nucleus count per cell, and a summary of the mean +- standard deviation nucleus measurements per cell.
![ImageJ Results Table](/imgs/summary_table.png "Summary output results table")
Note, 'cell name' in this summary table won't _exactly_ match the names in the Roi manager that is opened after analysis (this Roi manager will also contain the nucleus Rois). But it should be fairly straightforward to work out which is which... However, 'cell name' *will* match the cell names in any saved output.
Here are the other outputs, if you selected any of the save options:
* "Save results per cell" - this allows you to inspect the measurements for each nucleus in each cell separately. The name of the .csv file will match the 'cell name' in the summary results table. This is probably useful for if you need to weed out any badly segmented nuclei etc.
![File structure showing .csv files and one of the files open alongside](/imgs/results_cell.png "Results .csv files saved within the automatically created 'tables' folder and an example of the contents of one of these files.")
* "Save individual cell crops" - this will save an individual .tif file per image, with the cell channel in red, nuclei channel in green, and an overlay containing the cell outline (white) and nuclei outlines (blue). The file name will match the 'cell name' in the summary table.
![Folder containing .tif files and an opened example of a crop](/imgs/crop.png "Individual saved crops in the created 'crops' folder and one of the opened images")
Additionally, a file called 'Crops-RoiSet.zip' will be created. This will contain the rectangular Rois used for making the crops, and would be useful if you wanted to go back and crop the cells back out of the original non-thresholded image, for example.
![Rectangular crops displayed around cells in ImageJ](/imgs/rectangle_crops.png "Location and contents of the Crops-RoiSet.zip file shown on the thresholded image")
* "Save Roi sets" - this will save a Roi set for each cropped cell, with the Rois re-positioned to be relative to the crop rather than the original image. These live in the 'local rois' folder and the file name will match the 'cell name' in the summary table. The first Roi in the file is the cell outline, then the others are the nuclei. The nuclei Roi names will match the nucleus names in the individual results tables, if these are exported.
![Individual cropped cell with ImageJ Rois](/imgs/local_rois.png "Contents of 'local rois' folder with one example shown alongside a saved cropped cell")

## Known issues
On my test data, I keep getting a lot of error messages as shown below. No idea what these are, some weird Java thing - it's not affecting the analysis, so can be ignored if they pop up for you!
![Screenshot of error messages in ImageJ console](/imgs/errors.png "Mysterious error messages in ImageJ console").
One issue I've had before when working with thresholded images between Windows (my system that I have tested on) and Mac is that the two systems recognise inverted images differently. Let me know if the plugin seems to be segmenting the opposite of what you want, as this is probably the reason, and I can add a patch to fix it.
**If you discover bugs, or want me to add/change functionality, either raise it as a Github issue or email me at sian[dot]culley[at]kcl.ac.uk**

## Updates to plugin
I probably won't make any major updates to the plugin unless requested. If something big changes, I'll let you know!