# Fluorescence Analyzer Plugin for ImageJ

**Fluorescence Analyzer** is a plugin for ImageJ designed to facilitate the analysis of fluorescence images. It provides a graphical interface for selecting, processing, and analyzing regions of interest (ROIs), focusing on the quantification of fluorescence intensity within specified areas.

## Main Features

- Load and preview multiple images from a directory.
- Select and process images to identify and quantify fluorescence intensity.
- Manual ROI selection and editing.
- Automatic thresholding and morphological operations.
- Export results in CSV format.
- Save processed images as TIFF files.

## Requirements

- Java 8 or higher.
- ImageJ (preferably the Fiji distribution).
- Apache POI library for Excel export.

## Installation

1. Compile or download the JAR file of the plugin.
2. Place it in the `plugins` folder of ImageJ/Fiji.
3. Restart ImageJ.
4. Access the plugin via `Plugins > Fluorescence Analyzer`.

## Workflow

1. Select a directory of images.
2. Define a region of interest (ROI) with the freehand tool.
3. Process the image to identify and quantify fluorescence intensity.
4. View and adjust the ROIs if necessary.
5. Export the results and save the processed images.

## Outputs

- CSV file with quantitative data per image.
- TIFF images of the masks and generated montages.
- Individual and total results tables.

## Application

This plugin is useful for researchers in cell biology and fluorescence microscopy, enabling semi-automated quantification of fluorescence intensity in microscopy images.

## License

Distributed under the MIT License.

