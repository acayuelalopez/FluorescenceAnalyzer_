import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.WaitForUserDialog;
import ij.measure.Measurements;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import loci.plugins.in.DisplayHandler;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

public class FluorescenceAnalyzer_ implements PlugIn, Measurements {
	JButton okButton, cancelButton, buttonOkSeries, buttonCancelSeries, buttonSelectAll, buttonDeselectAll, refButton,
			targetButton, exportButton;
	JPanel panelOkCancel, panelImages, panelSave;
	TextField textImages, textSave;
	String FLUORESCENCEANALYZER_IMAGES_DEFAULT_PATH, FLUORESCENCEANALYZER_SAVE_DEFAULT_PATH, pathImages, pathSave;
	Preferences prefImages, prefSave;
	JLabel directLabelImages;
	JFrame frameMain, frameSeries, frameDef;
	JCheckBox checkSeries;
	JComboBox<String> comboCh;
	String[] imageTitles;
	ImagePlus imp, impAnalysis;
	ImagePlus[] channels, impsSelected;
	JTable tableImages, tableDef;
	DefaultTableModel modelImages, modelDef;
	JScrollPane jScrollPaneImages, jScrollPaneDef;
	Object[] columnNames = new Object[] { "", "", "" }, columnNamesDef = new Object[] { "Title", "Serie", "Ref.Image",
			"Target.Image", "Ref.Area", "Target.Area", "Ref.Mean", "Target.Mean", "Target.Mean.Prop" };
	Roi refRoi, targetRoi;
	UserDialogFluorescence refDialog, targetDialog;
	List<String> imageTitleList = new ArrayList<String>(), serieTitleList = new ArrayList<String>(),
			refArea = new ArrayList<String>(), targetArea = new ArrayList<String>(), refMean = new ArrayList<String>(),
			targetMean = new ArrayList<String>(), targetMeanProp = new ArrayList<String>();
	List<ImageIcon> refImage = new ArrayList<ImageIcon>(), targetImage = new ArrayList<ImageIcon>();

	@Override
	public void run(String arg0) {

		refDialog = new UserDialogFluorescence("Action Warning", "Draw reference area, then click OK.");
		targetDialog = new UserDialogFluorescence("Action Warning", "Draw target area, then click OK.");
		refButton = refDialog.getButton();
		targetButton = targetDialog.getButton();

		FLUORESCENCEANALYZER_IMAGES_DEFAULT_PATH = "images_path";
		FLUORESCENCEANALYZER_SAVE_DEFAULT_PATH = "save_path";
		prefImages = Preferences.userRoot();
		prefSave = Preferences.userRoot();

		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			try {

				JFrame.setDefaultLookAndFeelDecorated(true);
				JDialog.setDefaultLookAndFeelDecorated(true);
				UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
			} catch (Exception e) {
				e.printStackTrace();
			}
			createAndShowGUI();
		}
		if (imp != null) {
			IJ.error("You should not have images opened, Fluorescence Analyzer is asking for a path.");
			return;
		}
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				pathImages = textImages.getText();
				prefImages.put(FLUORESCENCEANALYZER_IMAGES_DEFAULT_PATH, textImages.getText());
				pathSave = textSave.getText();
				prefSave.put(FLUORESCENCEANALYZER_SAVE_DEFAULT_PATH, textSave.getText());
				frameMain.dispatchEvent(new WindowEvent(frameMain, WindowEvent.WINDOW_CLOSING));

				if (imp == null) {
					File imageFolder = new File(pathImages);
					File[] listOfFiles = imageFolder.listFiles();
					imageTitles = new String[listOfFiles.length];

					for (int i = 0; i < listOfFiles.length; i++)
						imageTitles[i] = listOfFiles[i].getName();

					String[] paths = new String[listOfFiles.length];
					for (int i = 0; i < paths.length; i++)
						paths[i] = pathImages + File.separator + imageTitles[i];
					Thread process0 = new Thread(new Runnable() {

						public void run() {
							for (int i = 0; i < listOfFiles.length; i++) {
								if (listOfFiles[i].getName().contains(".lif") == false) {
									IJ.error("It is need .lif format to analyze.");
									return;
								}
								if (listOfFiles[i].getName().contains(".lif") == true) {
									IJ.setTool("freehand");
									ImagePlus[] imps = openBF((pathImages + File.separator + imageTitles[i]), false,
											false, false, false, false, true);

									showSeriesDialog(imps);
									buttonOkSeries.addActionListener(new ActionListener() {
										public void actionPerformed(ActionEvent ae) {
											frameSeries.dispatchEvent(
													new WindowEvent(frameSeries, WindowEvent.WINDOW_CLOSING));
											synchronized (buttonOkSeries) {
												buttonOkSeries.notify();
												List<Integer> seriesSelectedList = new ArrayList<Integer>();
												for (int j = 0; j < tableImages.getRowCount(); j++)
													if (((Boolean) tableImages.getModel().getValueAt(
															tableImages.convertRowIndexToModel(j),
															tableImages.convertColumnIndexToModel(0))) == true)
														seriesSelectedList.add(j);

												impsSelected = new ImagePlus[seriesSelectedList.size()];
												for (int j = 0; j < seriesSelectedList.size(); j++) {
													impsSelected[j] = imps[seriesSelectedList.get(j)];
													serieTitleList.add(String.valueOf(seriesSelectedList.get(j) + 1));
													imageTitleList.add(imps[seriesSelectedList.get(j)].getTitle());

												}
											}

										}
									});
									synchronized (buttonOkSeries) {

										try {
											buttonOkSeries.wait();
										} catch (InterruptedException ex) {
											ex.printStackTrace();
										}
									}

									// Thread process1 = new Thread(new Runnable() {

									// public void run() {
									for (int j = 0; j < impsSelected.length; j++) {

										channels = ChannelSplitter.split(impsSelected[j]);
										impAnalysis = channels[comboCh.getSelectedIndex()].duplicate();
										IJ.resetMinAndMax(impAnalysis);
										IJ.run(impAnalysis, "8-bit", "");

										impAnalysis.show();
										refDialog.show();
										refRoi = impAnalysis.getRoi();
										ImagePlus refCropDup = impAnalysis.duplicate();
										ImagePlus refCropDup1 = refCropDup.duplicate();
										refCropDup1.setRoi(refRoi);
										refImage.add(
												new ImageIcon(getScaledImage(refCropDup1.crop().getImage(), 90, 60)));
										IJ.run(refCropDup, "8-bit", "");
										IJ.run(refCropDup, "Auto Threshold", "method=Huang2 ignore_black white");
										IJ.run(refCropDup, "Create Selection", "");
										Roi roiRef = refCropDup.getRoi();
										Roi refAndRoi = null;
										if (checkSeries.isSelected() == Boolean.FALSE)
											refAndRoi = (new ShapeRoi(refRoi).and(new ShapeRoi(roiRef))).shapeToRoi();
										if (checkSeries.isSelected() == Boolean.TRUE)
											refAndRoi = refRoi;
										impAnalysis.setRoi(refAndRoi);
										refMean.add(String.format("%.2f", refAndRoi.getStatistics().mean));
										refArea.add(String.format("%.2f", refAndRoi.getStatistics().area));
										IJ.setTool("freehand");
										targetDialog.show();
										targetRoi = impAnalysis.getRoi();
										ImagePlus targetCropDup = impAnalysis.duplicate();
										ImagePlus targetCropDup1 = targetCropDup.duplicate();
										targetCropDup1.setRoi(targetRoi);
										targetImage.add(new ImageIcon(
												getScaledImage(targetCropDup1.crop().getImage(), 90, 60)));
										IJ.run(targetCropDup, "8-bit", "");
										IJ.run(targetCropDup, "Auto Threshold", "method=Mean ignore_black white");
										IJ.run(targetCropDup, "Create Selection", "");
										Roi roiTarget = targetCropDup.getRoi();
										Roi targetAndRoi = null;
										if (checkSeries.isSelected() == Boolean.FALSE)
											targetAndRoi = (new ShapeRoi(targetRoi).and(new ShapeRoi(roiTarget)))
													.shapeToRoi();
										if (checkSeries.isSelected() == Boolean.TRUE)
											targetAndRoi = targetRoi;

										impAnalysis.setRoi(targetAndRoi);
										targetMean.add(String.format("%.2f", targetAndRoi.getStatistics().mean));
										targetArea.add(String.format("%.2f", targetAndRoi.getStatistics().area));
										targetMeanProp
												.add(String.format("%.2f", (targetAndRoi.getStatistics().mean * 100.0)
														/ refAndRoi.getStatistics().mean));
										imp = WindowManager.getCurrentImage();
										if (imp != null)
											imp.hide();

									}

									// }
									// });
									// process1.start();

								}
							}

							processTable(imageTitleList, serieTitleList, refImage, targetImage, refArea, targetArea,
									refMean, targetMean, targetMeanProp);
						}
					});
					process0.start();

				}

			}
		});
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				frameMain.dispatchEvent(new WindowEvent(frameMain, WindowEvent.WINDOW_CLOSING));

			}
		});
	}

	public void csvExport() throws IOException {

		/*
		 * try {
		 * 
		 * TableModel modelDef = tableDef.getModel(); FileWriter csv = new
		 * FileWriter(new File(pathSave + File.separator + "Flourescence_data.csv"));
		 * 
		 * for (int i = 0; i < modelDef.getColumnCount(); i++) {
		 * csv.write(modelDef.getColumnName(i) + ","); }
		 * 
		 * csv.write("\n");
		 * 
		 * for (int i = 0; i < tableDef.getRowCount(); i++) { for (int j = 0; j <
		 * tableDef.getColumnCount(); j++) {
		 * 
		 * csv.write(tableDef.getModel().getValueAt(tableDef.convertRowIndexToModel(i),
		 * j).toString() + ","); } csv.write("\n"); } csv.write("\n"); csv.write("\n");
		 * csv.write("\n");
		 * 
		 * csv.close();
		 * 
		 * } catch (IOException e) { e.printStackTrace();
		 * 
		 * }
		 */
		HSSFWorkbook fWorkbook = new HSSFWorkbook();
		HSSFSheet fSheet = fWorkbook.createSheet("new Sheet");
		HSSFFont sheetTitleFont = fWorkbook.createFont();
		HSSFCellStyle cellStyle = fWorkbook.createCellStyle();
		sheetTitleFont.setBold(true);
		// sheetTitleFont.setColor();
		TableModel model = tableDef.getModel();

		// Get Header
		TableColumnModel tcm = tableDef.getColumnModel();
		HSSFRow hRow = fSheet.createRow((short) 0);
		for (int j = 0; j < tcm.getColumnCount(); j++) {
			HSSFCell cell = hRow.createCell((short) j);
			cell.setCellValue(tcm.getColumn(j).getHeaderValue().toString());
			cell.setCellStyle(cellStyle);
		}

		// Get Other details
		for (int i = 0; i < model.getRowCount(); i++) {
			HSSFRow fRow = fSheet.createRow((short) i + 1);
			for (int j = 0; j < model.getColumnCount(); j++) {
				HSSFCell cell = fRow.createCell((short) j);
				cell.setCellValue(model.getValueAt(i, j).toString());
				cell.setCellStyle(cellStyle);
			}
		}
		FileOutputStream fileOutputStream;
		fileOutputStream = new FileOutputStream(pathSave + File.separator + "Flourescence_data.xlsx");
		try (BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream)) {
			fWorkbook.write(bos);
		}
		fileOutputStream.close();

		JOptionPane.showMessageDialog(null, "Flourescence_data.xlsx exported to " + pathSave);

	}

	public void processTable(List<String> imageTitleList, List<String> serieTitleList, List<ImageIcon> refImage,
			List<ImageIcon> targetImage, List<String> refArea, List<String> targetArea, List<String> refMean,
			List<String> targetMean, List<String> targetMeanProp) {

		exportButton = new JButton("");
		ImageIcon iconExport = createImageIcon("images/export.png");
		Icon iconExportCell = new ImageIcon(iconExport.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH));
		exportButton.setIcon(iconExportCell);

		tableDef = new JTable();
		modelDef = new DefaultTableModel();
		modelDef.setColumnIdentifiers(columnNamesDef);
		jScrollPaneDef = new JScrollPane(tableDef);
		jScrollPaneDef.setPreferredSize(new Dimension(650, 300));
		Object[][] dataTImages = new Object[serieTitleList.size()][columnNamesDef.length];
		for (int i = 0; i < dataTImages.length; i++)
			for (int j = 0; j < dataTImages[i].length; j++)
				dataTImages[i][j] = "";
		modelDef = new DefaultTableModel(dataTImages, columnNamesDef) {

			@Override
			public Class<?> getColumnClass(int column) {
				if (getRowCount() >= 0) {
					Object value = getValueAt(0, column);
					if (value != null) {
						return getValueAt(0, column).getClass();
					}
				}

				return super.getColumnClass(column);
			}

			public boolean isCellEditable(int row, int col) {
				return false;
			}

		};
		tableDef.setModel(modelDef);
		for (int i = 0; i < modelDef.getRowCount(); i++) {

			modelDef.setValueAt(imageTitleList.get(i), i, tableDef.convertColumnIndexToModel(0));
			modelDef.setValueAt("Serie: " + serieTitleList.get(i), i, tableDef.convertColumnIndexToModel(1));
			modelDef.setValueAt(refImage.get(i), i, tableDef.convertColumnIndexToModel(2));
			modelDef.setValueAt(targetImage.get(i), i, tableDef.convertColumnIndexToModel(3));
			modelDef.setValueAt(refArea.get(i), i, tableDef.convertColumnIndexToModel(4));
			modelDef.setValueAt(targetArea.get(i), i, tableDef.convertColumnIndexToModel(5));
			modelDef.setValueAt(refMean.get(i), i, tableDef.convertColumnIndexToModel(6));
			modelDef.setValueAt(targetMean.get(i), i, tableDef.convertColumnIndexToModel(7));
			modelDef.setValueAt(targetMeanProp.get(i), i, tableDef.convertColumnIndexToModel(8));

		}
		tableDef.setModel(modelDef);
		tableDef.setSelectionBackground(new Color(229, 255, 204));
		tableDef.setSelectionForeground(new Color(0, 102, 0));
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		tableDef.setDefaultRenderer(String.class, centerRenderer);
		tableDef.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tableDef.setRowHeight(60);
		tableDef.setAutoCreateRowSorter(true);
		tableDef.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());
		for (int u = 0; u < tableDef.getColumnCount(); u++)
			tableDef.getColumnModel().getColumn(u).setPreferredWidth(170);
		tableDef.getColumnModel().getColumn(0).setPreferredWidth(250);

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		imagePanel.add(jScrollPaneDef);
		JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		exportPanel.add(exportButton);
		mainPanel.add(imagePanel);
		mainPanel.add(exportPanel);
		frameDef = new JFrame();
		frameDef.setTitle("Results");
		frameDef.setResizable(false);
		frameDef.add(mainPanel);
		frameDef.pack();
		frameDef.setSize(660, 400);
		frameDef.setLocationRelativeTo(null);
		frameDef.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frameDef.setVisible(true);
		exportButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				try {
					csvExport();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

			}
		});

	}

	public void createAndShowGUI() {

		okButton = new JButton("");
		okButton.setBounds(50, 100, 95, 30);
		ImageIcon iconOk = createImageIcon("images/ok.png");
		Icon iconOKCell = new ImageIcon(iconOk.getImage().getScaledInstance(17, 15, Image.SCALE_SMOOTH));
		okButton.setIcon(iconOKCell);
		okButton.setToolTipText("Click this button to import your file to table.");
		cancelButton = new JButton("");
		cancelButton.setBounds(50, 100, 95, 30);
		ImageIcon iconCancel = createImageIcon("images/cancel.png");
		Icon iconCancelCell = new ImageIcon(iconCancel.getImage().getScaledInstance(17, 15, Image.SCALE_SMOOTH));
		cancelButton.setIcon(iconCancelCell);
		cancelButton.setToolTipText("Click this button to cancel.");
		panelOkCancel = new JPanel();
		panelOkCancel.setLayout(new FlowLayout());
		panelOkCancel.add(okButton);
		panelOkCancel.add(cancelButton);

		ImageIcon iconBrowse = createImageIcon("images/browse.png");
		Icon iconBrowseCell = new ImageIcon(iconBrowse.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));

		JButton buttonImages = new JButton("");
		buttonImages.setIcon(iconBrowseCell);
		textImages = new TextField(20);
		textImages.setText(prefImages.get(FLUORESCENCEANALYZER_IMAGES_DEFAULT_PATH, ""));
		DirectoryListener listenerImages = new DirectoryListener("Browse for directory to collect images ", textImages,
				JFileChooser.FILES_AND_DIRECTORIES);
		directLabelImages = new JLabel("   .lif Directory : ");
		directLabelImages.setFont(new Font("Helvetica", Font.BOLD, 12));
		buttonImages.addActionListener(listenerImages);
		panelImages = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelImages.add(directLabelImages);
		panelImages.add(textImages);
		panelImages.add(buttonImages);

		JButton buttonSave = new JButton("");
		buttonSave.setIcon(iconBrowseCell);
		panelSave = new JPanel(new FlowLayout(FlowLayout.LEFT));
		textSave = new TextField(20);
		textSave.setText(prefSave.get(FLUORESCENCEANALYZER_SAVE_DEFAULT_PATH, ""));
		DirectoryListener listenerSave = new DirectoryListener("Browse for directory to save files ", textSave,
				JFileChooser.FILES_AND_DIRECTORIES);
		JLabel directLabelSave = new JLabel("   Results Directory : ");
		directLabelSave.setFont(new Font("Helvetica", Font.BOLD, 12));
		buttonSave.addActionListener(listenerSave);
		panelSave.add(directLabelSave);
		panelSave.add(textSave);
		panelSave.add(buttonSave);
		JSeparator separator1 = new JSeparator(SwingConstants.VERTICAL);
		JSeparator separator2 = new JSeparator(SwingConstants.VERTICAL);
		Dimension dime = separator1.getPreferredSize();
		// dime.height = panelDisp.getPreferredSize().height;
		separator1.setPreferredSize(dime);
		separator2.setPreferredSize(dime);
		checkSeries = new JCheckBox("   Hand Drawing Roi");
		checkSeries.setSelected(false);
		JPanel checkSeriesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checkSeriesPanel.add(checkSeries);
		JLabel labelCh = new JLabel("  Channel for Analysis: ");
		JPanel panelCh = new JPanel(new FlowLayout(FlowLayout.LEFT));
		comboCh = new JComboBox();
		comboCh.addItem("Ch-1");
		comboCh.addItem("Ch-2");
		panelCh.add(labelCh);
		panelCh.add(comboCh);
		JPanel generalPanel = new JPanel();
		generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
		generalPanel.add(panelImages);
		generalPanel.add(panelSave);
		generalPanel.add(panelCh);
		generalPanel.add(checkSeriesPanel);
		generalPanel.add(panelOkCancel);

		frameMain = new JFrame("Fluorescence Analyzer");
		frameMain.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frameMain.add(generalPanel);
		frameMain.setSize(730, 430);
		frameMain.pack();

		frameMain.setVisible(true);

	}

	public static ImageIcon createImageIcon(String path) {
		java.net.URL imgURL = FluorescenceAnalyzer_.class.getResource(path);
		if (imgURL != null) {
			return new ImageIcon(imgURL);
		} else {
			System.err.println("Couldn't find file: " + path);
			return null;
		}
	}

	public static ImagePlus[] openBF(String multiSeriesFileName, boolean splitC, boolean splitT, boolean splitZ,
			boolean autoScale, boolean crop, boolean allSeries) {
		ImporterOptions options;
		ImagePlus[] imps = null;
		try {
			options = new ImporterOptions();
			options.setId(multiSeriesFileName);
			options.setSplitChannels(splitC);
			options.setSplitTimepoints(splitT);
			options.setSplitFocalPlanes(splitZ);
			options.setAutoscale(autoScale);
			options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
			options.setStackOrder(ImporterOptions.ORDER_XYCZT);
			options.setCrop(crop);
			options.setOpenAllSeries(allSeries);

			ImportProcess process = new ImportProcess(options);
			if (!process.execute())
				return null;
			DisplayHandler displayHandler = new DisplayHandler(process);
			if (options != null && options.isShowOMEXML()) {
				displayHandler.displayOMEXML();
			}
			List<ImagePlus> impsList = new ImagePlusReaderModified(process).readImages(false);
			imps = impsList.toArray(new ImagePlus[0]);
			if (options != null && options.showROIs()) {
				displayHandler.displayROIs(imps);
			}
			if (!options.isVirtual()) {
				process.getReader().close();
			}

		} catch (Exception e) {

			return null;
		}
		return imps;
	}

	public void showSeriesDialog(ImagePlus[] imps) {

		buttonCancelSeries = new JButton("");
		ImageIcon iconCancel = createImageIcon("images/cancel.png");
		Icon iconCancelCell = new ImageIcon(iconCancel.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		buttonCancelSeries.setIcon(iconCancelCell);
		buttonOkSeries = new JButton("");
		ImageIcon iconOk = createImageIcon("images/ok.png");
		Icon iconOkCell = new ImageIcon(iconOk.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		buttonOkSeries.setIcon(iconOkCell);
		buttonSelectAll = new JButton("Select All");
		buttonDeselectAll = new JButton("Deselect All");
		tableImages = new JTable();
		modelImages = new DefaultTableModel();
		modelImages.setColumnIdentifiers(columnNames);
		jScrollPaneImages = new JScrollPane(tableImages);
		jScrollPaneImages.setPreferredSize(new Dimension(650, 200));
		Object[][] dataTImages = new Object[imps.length][columnNames.length];
		for (int i = 0; i < dataTImages.length; i++)
			for (int j = 0; j < dataTImages[i].length; j++)
				dataTImages[i][j] = "";
		modelImages = new DefaultTableModel(dataTImages, columnNames) {

			@Override
			public Class<?> getColumnClass(int column) {
				if (getRowCount() >= 0) {
					Object value = getValueAt(0, column);
					if (value != null) {
						return getValueAt(0, column).getClass();
					}
				}

				return super.getColumnClass(column);
			}

			public boolean isCellEditable(int row, int col) {
				switch (col) {
				case 0:
					return true;
				default:
					return false;
				}
			}

		};

		tableImages.setModel(modelImages);
		ImagePlus[] lifs = imps;
		List<ImageIcon> iconsLif = new ArrayList<ImageIcon>();
		for (int i = 0; i < imps.length; i++) {
			ImagePlus lif = ChannelSplitter.split(lifs[i])[comboCh.getSelectedIndex()];
			IJ.resetMinAndMax(lif);
			iconsLif.add(new ImageIcon(getScaledImage(lif.getImage(), 90, 60)));
		}
		for (int i = 0; i < modelImages.getRowCount(); i++) {
			modelImages.setValueAt(Boolean.TRUE, i, tableImages.convertColumnIndexToModel(0));
			modelImages.setValueAt(
					"Serie: " + (i + 1) + " Title: " + imps[i].getShortTitle() + " " + imps[i].getWidth() + " x "
							+ imps[i].getHeight() + " : " + imps[i].getNChannels() + "C" + " x " + imps[i].getNFrames(),
					i, tableImages.convertColumnIndexToModel(1));

			modelImages.setValueAt(iconsLif.get(i), i, tableImages.convertColumnIndexToModel(2));

		}
		tableImages.setModel(modelImages);
		tableImages.setSelectionBackground(new Color(229, 255, 204));
		tableImages.setSelectionForeground(new Color(0, 102, 0));
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		tableImages.setDefaultRenderer(String.class, centerRenderer);
		tableImages.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tableImages.setRowHeight(60);
		tableImages.setAutoCreateRowSorter(true);
		tableImages.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());
		tableImages.getColumnModel().getColumn(0).setPreferredWidth(100);
		tableImages.getColumnModel().getColumn(1).setPreferredWidth(450);
		tableImages.getColumnModel().getColumn(2).setPreferredWidth(100);
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		imagePanel.add(jScrollPaneImages);
		JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		selectPanel.add(buttonDeselectAll);
		selectPanel.add(buttonSelectAll);
		JPanel okCancelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		okCancelPanel.add(buttonOkSeries);
		okCancelPanel.add(buttonCancelSeries);
		JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.CENTER));
		panelTotal.add(selectPanel);
		panelTotal.add(Box.createHorizontalStrut(20));
		panelTotal.add(okCancelPanel);
		mainPanel.add(imagePanel);
		mainPanel.add(panelTotal);
		frameSeries = new JFrame();
		frameSeries.setTitle("Series Option");
		frameSeries.setResizable(false);
		frameSeries.add(mainPanel);
		frameSeries.pack();
		frameSeries.setSize(660, 300);
		frameSeries.setLocationRelativeTo(null);
		frameSeries.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frameSeries.setVisible(true);
		buttonDeselectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				for (int i = 0; i < modelImages.getRowCount(); i++)
					modelImages.setValueAt(Boolean.FALSE, i, tableImages.convertColumnIndexToModel(0));

			}
		});
		buttonSelectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				for (int i = 0; i < modelImages.getRowCount(); i++)
					modelImages.setValueAt(Boolean.TRUE, i, tableImages.convertColumnIndexToModel(0));

			}
		});
		buttonCancelSeries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				frameSeries.dispatchEvent(new WindowEvent(frameSeries, WindowEvent.WINDOW_CLOSING));

			}
		});

	}

	public static Image getScaledImage(Image srcImg, int w, int h) {
		BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = resizedImg.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(srcImg, 0, 0, w, h, null);
		g2.dispose();
		return resizedImg;
	}
}
