import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import loci.common.Location;
import loci.common.Region;
import loci.common.StatusEvent;
import loci.common.StatusListener;
import loci.common.StatusReporter;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FilePattern;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.cache.CacheException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.Slicer;
import loci.plugins.in.Calibrator;
import loci.plugins.in.Colorizer;
import loci.plugins.in.Concatenator;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.BFVirtualStack;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LuraWave;
import loci.plugins.util.VirtualImagePlus;

/**
 * A high-level reader for {@link ij.ImagePlus} objects.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/loci-plugins/src/loci/plugins/in/ImagePlusReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/in/ImagePlusReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class ImagePlusReaderModified implements StatusReporter {

  // -- Constants --

  /** Special property for storing series number associated with the image. */
  public static final String PROP_SERIES = "Series";

  /** Special property prefix for storing planar LUTs. */
  public static final String PROP_LUT = "LUT-";

  // -- Fields --

  /**
   * Import preparation process managing Bio-Formats readers and other state.
   */
  public ImportProcess process;

  public List<StatusListener> listeners = new Vector<StatusListener>();

  // -- Constructors --
  /**
   * Constructs an ImagePlusReader with the
   * given complete import preparation process.
   */
  public ImagePlusReaderModified(ImportProcess process) {
    this.process = process;
  }

  // -- ImagePlusReader methods --

  /**
   * Opens one or more {@link ImagePlus} objects
   * corresponding to the reader's associated options.
   */
  public ImagePlus[] openImagePlus()  {
    List<ImagePlus> imps = readImages();
    return imps.toArray(new ImagePlus[0]);
  }

  public ImagePlus[] openThumbImagePlus()  {
    List<ImagePlus> imps = readThumbImages();
    return imps.toArray(new ImagePlus[imps.size()]);
  }

  // -- StatusReporter methods --

  public void addStatusListener(StatusListener l) {
    listeners.add(l);
  }

  public void removeStatusListener(StatusListener l) {
    listeners.remove(l);
  }

  public void notifyListeners(StatusEvent e) {
    for (StatusListener l : listeners) l.statusUpdated(e);
  }

  // -- Utility methods --

  /**
   * Creates an {@link ImagePlus} from the given image processors.
   *
   * @param title The title for the image.
   * @param procs List of image processors to compile into an image.
   */
  public static ImagePlus createImage(String title, List<ImageProcessor> procs) {
    final List<LUT> luts = new ArrayList<LUT>();
    final ImageStack stack = createStack(procs, null, luts);
    return createImage(title, stack, luts);
  }

  /**
   * Creates an {@link ImagePlus} from the given image stack.
   *
   * @param title The title for the image.
   * @param stack The image stack containing the image planes.
   * @param luts Optional list of plane-specific LUTs
   *   to store as image properties, for later use.
   */
  public static ImagePlus createImage(String title,
    ImageStack stack, List<LUT> luts)
  {
    final ImagePlus imp = new ImagePlus(title, stack);

    saveLUTs(imp, luts);
    return imp;
  }

  /**
   * Creates an image stack from the given image processors.
   *
   * @param procs List of image processors to compile into a stack.
   * @param labels Optional list of labels, one per plane.
   * @param luts Optional list for storing plane-specific LUTs, for later use.
   */
  public static ImageStack createStack(List<ImageProcessor> procs,
    List<String> labels, List<LUT> luts)
  {
    if (procs == null || procs.size() == 0) return null;

    final ImageProcessor ip0 = procs.get(0);
    final ImageStack stack = new ImageStack(ip0.getWidth(), ip0.getHeight());

    // construct image stack from list of image processors
    for (int i=0; i<procs.size(); i++) {
      final ImageProcessor ip = procs.get(i);
      final String label = labels == null ? null : labels.get(i);

      // HACK: ImageProcessorReader always assigns an ij.process.LUT object
      // as the color model. If we don't get one, we know ImageJ created a
      // default color model instead, which we can discard.
      if (luts != null) {
        final ColorModel cm = ip.getColorModel();
        if (cm instanceof LUT) {
          // plane has custom LUT attached; save it to the list
          final LUT lut = (LUT) cm;
          luts.add(lut);
          // discard custom LUT from ImageProcessor
          ip.setColorModel(ip.getDefaultColorModel());
        }
        else {
          // no LUT attached; save a placeholder
          luts.add(null);
        }
      }

      // add plane to image stack
      stack.addSlice(label, ip);
    }

    return stack;
  }

  // -- Helper methods - image reading --

  public List<ImagePlus> readImages()  {
    return readImages(false); 
  } 

  public List<ImagePlus> readThumbImages() 
  {
    return readImages(true);
  }

  public List<ImagePlus> readImages(boolean thumbnail)
    
  {
    final ImporterOptions options = process.getOptions();
    final ImageProcessorReader reader = process.getReader();

    List<ImagePlus> imps = new ArrayList<ImagePlus>();

    // beginning timing
    startTiming();

    // read in each image series
    for (int s=0; s<reader.getSeriesCount(); s++) {
      if (!options.isSeriesOn(s)) continue;
      final ImagePlus imp = readImage(s, thumbnail);
      imps.add(imp);
    }
    // concatenate compatible images
    imps = concatenate(imps);

    // colorize images, as appropriate
    imps = applyColors(imps);

    // split dimensions, as appropriate
    imps = splitDims(imps);

    // set virtual stack's reference count to match # of image windows
    // in this case, these is one window per enabled image series
    // when all image windows are closed, the Bio-Formats reader is closed
    if (options.isVirtual()) {
      process.getVirtualReader().setRefCount(imps.size());
    }

    // end timing
    finishTiming();

    return imps;
  }

  public ImagePlus readImage(int s, boolean thumbnail)
  {
    final ImporterOptions options = process.getOptions();
    final int zCount = process.getZCount(s);
    final int cCount = process.getCCount(s);
    final int tCount = process.getTCount(s);
    

    final List<LUT> luts = new ArrayList<LUT>();

    // create image stack
    final ImageStack stack;
    if (options.isVirtual()) stack = createVirtualStack(process, s, luts);
    else stack = readPlanes(process, s, luts, thumbnail);

    notifyListeners(new StatusEvent(1, 1, "Creating image"));

    // create title
    final String seriesName = process.getOMEMetadata().getImageName(s);
    final String file = process.getCurrentFile();
    final IFormatReader reader = process.getReader();
    final String title = constructImageTitle(reader,
      file, seriesName, options.isGroupFiles());

    // create image
    final ImagePlus imp;
    if (stack.isVirtual()) {
      VirtualImagePlus vip = new VirtualImagePlus(title, stack);
      vip.setReader(reader);
      imp = vip;
      saveLUTs(imp, luts);
    }
    else imp = createImage(title, stack, luts);

    // configure image

    // place metadata key/value pairs in ImageJ's info field
    final String metadata = process.getOriginalMetadata().toString();
    imp.setProperty("Info", metadata);
    imp.setProperty(PROP_SERIES, s);

    // retrieve the spatial calibration information, if available
    final FileInfo fi = createFileInfo();
    new Calibrator(process).applyCalibration(imp);
    imp.setFileInfo(fi);
    imp.setDimensions(cCount, zCount, tCount);

    // open as a hyperstack, as appropriate
    final boolean hyper = !options.isViewStandard();
    imp.setOpenAsHyperStack(hyper);

    return imp;
  }

  public ImageStack createVirtualStack(ImportProcess process, int s,
    List<LUT> luts) 
  {
    final ImporterOptions options = process.getOptions();
    final ImageProcessorReader reader = process.getReader();
    reader.setSeries(s);
    final int zCount = process.getZCount(s);
    final int cCount = process.getCCount(s);
    final int tCount = process.getTCount(s);
    final IMetadata meta = process.getOMEMetadata();
    final int imageCount = reader.getImageCount();

    // CTR FIXME: Make virtual stack work with different color modes?
    BFVirtualStack virtualStack = null;
	try {
		virtualStack = new BFVirtualStack(options.getId(),
		  reader, false, false, false);
	} catch (CacheException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (FormatException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    for (int i=0; i<imageCount; i++) {
      final String label = constructSliceLabel(i,
        reader, meta, s, zCount, cCount, tCount);
      virtualStack.addSlice(label);
    }

    if (luts != null) {
      for (int c=0; c<cCount; c++) {
        int index = reader.getIndex(0, c, 0);
        ImageProcessor ip = null;
		try {
			ip = reader.openProcessors(index)[0];
		} catch (FormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        final ColorModel cm = ip.getColorModel();
        final LUT lut = cm instanceof LUT ? (LUT) cm : null;
        luts.add(lut);
      }
    }

    return virtualStack;
  }

  public ImageStack readPlanes(ImportProcess process, int s, List<LUT> luts,
    boolean thumbnail)
    
  {
    final ImageProcessorReader reader = process.getReader();
    reader.setSeries(s);
    final int zCount = process.getZCount(s);
    final int cCount = process.getCCount(s);
    final int tCount = process.getTCount(s);
    final IMetadata meta = process.getOMEMetadata();

    // get list of planes to load
    final boolean[] load = getPlanesToLoad(s);
    int current = 0, total = 0;
    for (int j=0; j<load.length; j++) if (load[j]) total++;

    final List<ImageProcessor> procs = new ArrayList<ImageProcessor>();
    final List<String> labels = new ArrayList<String>();

    // read applicable image planes
    final Region region = process.getCropRegion(s);
    for (int i=0; i<load.length; i++) {
      if (!load[i]) continue;

      // limit message update rate
      updateTiming(s, current, current++, total);

      // get image processor for ith plane
      final ImageProcessor[] p = readProcessors(process, i, region, thumbnail);
  
      // generate a label for ith plane
      final String label = constructSliceLabel(i,
        reader, meta, s, zCount, cCount, tCount);

      for (ImageProcessor ip : p) {
        procs.add(ip);
        labels.add(label);
      }
    }

    return createStack(procs, labels, luts);
  }

  /**
   * HACK: This method mainly exists to prompt the user for a missing
   * LuraWave license code, in the case of LWF-compressed Flex.
   *
   * @see ImportProcess#setId()
   */
  public ImageProcessor[] readProcessors(ImportProcess process,
    int no, Region r, boolean thumbnail) 
  {
    final ImageProcessorReader reader = process.getReader();
    final ImporterOptions options = process.getOptions();

    boolean first = true;
    for (int i=0; i<LuraWave.MAX_TRIES; i++) {
      String code = LuraWave.initLicenseCode();
      try {
        if (thumbnail) {
          try {
			return reader.openThumbProcessors(no);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        }
        try {
			return reader.openProcessors(no, r.x, r.y, r.width, r.height);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
      }
      catch (FormatException exc) {

        // prompt user for LuraWave license code
        code = LuraWave.promptLicenseCode(code, first);
        if (first) first = false;
      }
    }
	return null;
   
  }

  // -- Helper methods - image post processing --

  public List<ImagePlus> concatenate(List<ImagePlus> imps) {
    final ImporterOptions options = process.getOptions();
    if (options.isConcatenate()) imps = new Concatenator().concatenate(imps);
    return imps;
  }

  public List<ImagePlus> applyColors(List<ImagePlus> imps) {
    return new Colorizer(process).applyColors(imps);
  }

  public List<ImagePlus> splitDims(List<ImagePlus> imps) {
    final ImporterOptions options = process.getOptions();

    final boolean sliceC = options.isSplitChannels();
    final boolean sliceZ = options.isSplitFocalPlanes();
    final boolean sliceT = options.isSplitTimepoints();
    if (sliceC || sliceZ || sliceT) {
      final String stackOrder = process.getStackOrder();
      final List<ImagePlus> slicedImps = new ArrayList<ImagePlus>();
      final Slicer slicer = new Slicer();
      for (ImagePlus imp : imps) {
        final ImagePlus[] results = slicer.reslice(imp,
          sliceC, sliceZ, sliceT, stackOrder);
        for (ImagePlus result : results) slicedImps.add(result);
      }
      imps = slicedImps;
    }
    return imps;
  }

  // -- Helper methods - timing --

  public long startTime, time;

  public void startTiming() {
    startTime = time = System.currentTimeMillis();
  }

  public void updateTiming(int s, int i, int current, int total) {
    final ImageProcessorReader reader = process.getReader();

    long clock = System.currentTimeMillis();
    if (clock - time >= 100) {
      String sLabel = reader.getSeriesCount() > 1 ?
        ("series " + (s + 1) + ", ") : "";
      String pLabel = "plane " + (i + 1) + "/" + total;
      notifyListeners(new StatusEvent("Reading " + sLabel + pLabel));
      time = clock;
    }
    notifyListeners(new StatusEvent(current, total, null));
  }

  public void finishTiming() {
    final ImageProcessorReader reader = process.getReader();

    long endTime = System.currentTimeMillis();
    double elapsed = (endTime - startTime) / 1000.0;
    if (reader.getImageCount() == 1) {
      notifyListeners(new StatusEvent("Bio-Formats: " + elapsed + " seconds"));
    }
    else {
      long average = (endTime - startTime) / reader.getImageCount();
      notifyListeners(new StatusEvent("Bio-Formats: " +
        elapsed + " seconds (" + average + " ms per plane)"));
    }
  }

  // -- Helper methods -- miscellaneous --

  public FileInfo createFileInfo() {
    final FileInfo fi = new FileInfo();

    // populate common FileInfo fields
    String idDir = process.getIdLocation() == null ?
      null : process.getIdLocation().getParent();
    if (idDir != null && !idDir.endsWith(File.separator)) {
      idDir += File.separator;
    }
    fi.fileName = process.getIdName();
    fi.directory = idDir;

    // dump OME-XML to ImageJ's description field, if available
    try {
      ServiceFactory factory = new ServiceFactory();
      OMEXMLService service = factory.getInstance(OMEXMLService.class);
      fi.description = service.getOMEXML(process.getOMEMetadata());
    }
    catch (DependencyException de) { }
    catch (ServiceException se) { }

    return fi;
  }

  public boolean[] getPlanesToLoad(int s) {
    final ImageProcessorReader reader = process.getReader();
    final boolean[] load = new boolean[reader.getImageCount()];
    final int cBegin = process.getCBegin(s);
    final int cEnd = process.getCEnd(s);
    final int cStep = process.getCStep(s);
    final int zBegin = process.getZBegin(s);
    final int zEnd = process.getZEnd(s);
    final int zStep = process.getZStep(s);
    final int tBegin = process.getTBegin(s);
    final int tEnd = process.getTEnd(s);
    final int tStep = process.getTStep(s);
    for (int c=cBegin; c<=cEnd; c+=cStep) {
      for (int z=zBegin; z<=zEnd; z+=zStep) {
        for (int t=tBegin; t<=tEnd; t+=tStep) {
          int index = reader.getIndex(z, c, t);
          load[index] = true;
        }
      }
    }
    return load;
  }

  public String constructImageTitle(IFormatReader r,
    String file, String seriesName, boolean groupFiles)
  {
    String[] used = r.getUsedFiles();
    String title = file.substring(file.lastIndexOf(File.separator) + 1);
    if (used.length > 1 && groupFiles) {
      FilePattern fp = new FilePattern(new Location(file));
      title = fp.getPattern();
      if (title == null) {
        title = file;
        if (title.indexOf(".") != -1) {
          title = title.substring(0, title.lastIndexOf("."));
        }
      }
      title = title.substring(title.lastIndexOf(File.separator) + 1);
    }
    if (seriesName != null && !file.endsWith(seriesName) &&
      r.getSeriesCount() > 1)
    {
      title += " - " + seriesName;
    }
    if (title.length() > 128) {
      String a = title.substring(0, 62);
      String b = title.substring(title.length() - 62);
      title = a + "..." + b;
    }
    return title;
  }

  public String constructSliceLabel(int ndx, ImageProcessorReader r,
    IMetadata meta, int series, int zCount, int cCount, int tCount)
  {
    r.setSeries(series);

    final int[] zct = r.getZCTCoords(ndx);
    //final int[] subC = r.getChannelDimLengths();
  //  final String[] subCTypes = r.getChannelDimTypes();
    final StringBuffer sb = new StringBuffer();

    boolean first = true;
		/*
		 * if (cCount > 1) { if (first) first = false; else sb.append("; "); int[]
		 * subCPos = FormatTools.rasterToPosition(subC, zct[1]); for (int i=0;
		 * i<subC.length; i++) { boolean ch = subCTypes[i].equals(FormatTools.CHANNEL);
		 * sb.append(ch ? "c" : subCTypes[i]); sb.append(":"); sb.append(subCPos[i] +
		 * 1); sb.append("/"); sb.append(subC[i]); if (i < subC.length - 1)
		 * sb.append(", "); } }
		 */
    if (zCount > 1) {
      if (first) first = false;
      else sb.append("; ");
      sb.append("z:");
      sb.append(zct[0] + 1);
      sb.append("/");
      sb.append(r.getSizeZ());
    }
    if (tCount > 1) {
      if (first) first = false;
      else sb.append("; ");
      sb.append("t:");
      sb.append(zct[2] + 1);
      sb.append("/");
      sb.append(r.getSizeT());
    }
    // put image name at the end, in case it is long
    String imageName = meta.getImageName(series);
    if (imageName != null && !imageName.trim().equals("")) {
      sb.append(" - ");
      sb.append(imageName);
    }
    return sb.toString();
  }

  public static void saveLUTs(ImagePlus imp, List<LUT> luts) {
    // NB: Save individual planar LUTs as properties, for later access.
    // This step is necessary because ImageStack.addSlice only extracts the
    // pixels from the ImageProcessor, and does not preserve the ColorModel.
    // Later, Colorizer can use the LUTs when wrapping into a CompositeImage.
    for (int i=0; i<luts.size(); i++) {
      final LUT lut = luts.get(i);
      if (lut != null) {
        imp.setProperty(PROP_LUT + i, lut);
      }
    }
  }

}