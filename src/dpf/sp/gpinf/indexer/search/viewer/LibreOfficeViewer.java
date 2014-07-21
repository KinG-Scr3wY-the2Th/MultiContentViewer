/*
 * Copyright 2014, Luis Filipe Nassif
 * 
 * This file is part of MultiContentViewer.
 *
 * MultiContentViewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MultiContentViewer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MultiContentViewer.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search.viewer;

import ag.ion.bion.officelayer.NativeView;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.application.OfficeApplicationRuntime;
import ag.ion.bion.officelayer.desktop.IFrame;
import ag.ion.bion.officelayer.document.DocumentDescriptor;
import ag.ion.bion.officelayer.document.IDocument;
import ag.ion.bion.officelayer.presentation.IPresentationDocument;
import ag.ion.bion.officelayer.spreadsheet.ISpreadsheetDocument;
import ag.ion.bion.officelayer.text.ITextDocument;
import ag.ion.bion.officelayer.text.ITextRange;
import ag.ion.noa.search.ISearchResult;
import ag.ion.noa.search.SearchDescriptor;
import com.sun.star.awt.FontUnderline;
import com.sun.star.awt.FontWeight;
import com.sun.star.awt.XBitmap;
import com.sun.star.awt.XDevice;
import com.sun.star.awt.XDisplayBitmap;
import com.sun.star.awt.XGraphics;
import com.sun.star.awt.XWindow;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.drawing.XDrawPage;
import com.sun.star.drawing.XDrawPages;
import com.sun.star.drawing.XDrawPagesSupplier;
import com.sun.star.drawing.XDrawView;
import com.sun.star.lib.uno.Proxy;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetView;
import com.sun.star.sheet.XSpreadsheets;
import com.sun.star.table.XCell;
import com.sun.star.table.XCellRange;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.Any;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.XProtectable;
import com.sun.star.util.XSearchDescriptor;
import com.sun.star.util.XSearchable;
import com.sun.star.view.DocumentZoomType;
import com.sun.star.view.XSelectionSupplier;
import dpf.sp.gpinf.indexer.util.ProcessUtil;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * Viewer for Office file formats and many other formats supported by LibreOffice.
 */
public class LibreOfficeViewer extends AbstractViewer {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private static String userProfile = "$SYSUSERCONFIG/.indexador/libreoffice";
    private static String RESTART_MSG = "Restarting viewer...";
    private static int PORT = 8100;
    private IOfficeApplication officeApplication;
    private NativeView nat;
    private volatile IFrame officeFrame;
    private JPanel noaPanel;
    private String libPath, pathLO;
    private volatile boolean loading = false;
    private volatile boolean restartCalled = false;
    public volatile File lastFile = null;
    //private volatile Thread loadingThread;
    private Thread edtMonitor;
    private volatile Exception exception;
    private Object startLOLock = new Object();
    private IDocument document = null, prevDocument = null;
    private int delta = 1;
    private ArrayList<Object> hits;
    private Logger logger = Logger.getLogger(this.getClass().getName());

    public LibreOfficeViewer(String libPath, String pathLO) {
        super(new GridLayout());
        this.libPath = libPath;
        this.pathLO = pathLO;
        this.noaPanel = new JPanel();
        this.getPanel().add(noaPanel);
        addGlobalMouseListener();
    }

    @Override
    public boolean isSupportedType(String contentType) {

        return contentType.startsWith("application/msword")
                || contentType.equals("application/rtf")
                || contentType.startsWith("application/vnd.ms-word")
                || contentType.startsWith("application/vnd.ms-powerpoint")
                || contentType.startsWith("application/vnd.openxmlformats-officedocument")
                || contentType.startsWith("application/vnd.oasis.opendocument")
                || contentType.startsWith("application/vnd.sun.xml")
                || contentType.startsWith("application/vnd.stardivision")
                || contentType.startsWith("application/vnd.ms-works")
                || contentType.startsWith("application/vnd.wordperfect")
                || contentType.startsWith("application/x-msoffice")
                || contentType.startsWith("application/x-ooxml")
                || contentType.equals("application/vnd.visio")
                || contentType.equals("application/x-mspublisher")
                || contentType.equals("application/postscript")
                || contentType.equals("text/x-dbf")
                || contentType.equals("text/csv")
                || contentType.equals("application/vnd.oasis.opendocument.graphics")
                || contentType.equals("application/x-emf")
                || contentType.equals("application/x-msmetafile")
                || contentType.equals("image/vnd.adobe.photoshop")
                || contentType.equals("image/x-portable-bitmap")
                || contentType.equals("image/svg+xml")
                || contentType.equals("image/x-pcx")
                || contentType.equals("image/x-cmx")
                || contentType.equals("image/x-pict")
                || contentType.equals("image/vnd.dxf")
                || contentType.equals("image/x-cdr")
                || isSpreadSheet(contentType);

    }

    public boolean isSpreadSheet(String contentType) {
        return contentType.startsWith("application/vnd.ms-excel")
                || contentType.startsWith("application/x-msworks-spreadsheet")
                || contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml")
                || contentType.startsWith("application/vnd.oasis.opendocument.spreadsheet");

    }

    @Override
    public String getName() {
        return "Office";
    }


    /*
     * Workaround to focus problems when embedding LibreOffice with java > 1.6
     */
    private void addGlobalMouseListener() {
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    MouseEvent evt = (MouseEvent) event;
                    if (evt.getID() == MouseEvent.MOUSE_RELEASED && !evt.getSource().getClass().equals(JButton.class)) {
                        releaseFocus();
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    public void releaseFocus() {
        if (officeFrame != null) {
            try {
                officeFrame.getXFrame().getContainerWindow().setVisible(false);
                officeFrame.getXFrame().getContainerWindow().setVisible(true);
                noaPanel.setSize(noaPanel.getWidth() + delta, noaPanel.getHeight());
                delta *= -1;
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void init() {
        ProcessUtil.killProcess(PORT);
        startLO();
        edtMonitor = monitorEventThreadBlocking();

    }

    /*
     * Start LibreOffice in server mode.
     */
    private void startLO() {

        try {
            HashMap configuration = new HashMap();
            //if(System.getProperty("os.name").startsWith("Windows"))
            configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, pathLO);
            /*else{
             IApplicationAssistant ass = new ApplicationAssistant(libPath + "/lib");
             ILazyApplicationInfo[] ila = ass.getLocalApplications();
             if(ila.length > 0)
             configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, ila[0].getHome());
             }*/
            configuration.put(IOfficeApplication.APPLICATION_TYPE_KEY, IOfficeApplication.LOCAL_APPLICATION);

            ArrayList<String> options = new ArrayList<String>();
            options.add("-env:UserInstallation=" + userProfile);
            String prefix = "";
            if (pathLO.toLowerCase().contains("libre")) {
                prefix = "-";
            }
            options.add(prefix + "-invisible");
            options.add(prefix + "-nologo");
            options.add(prefix + "-nodefault");
            options.add(prefix + "-norestore");
            options.add(prefix + "-nocrashreport");
            options.add(prefix + "-nolockcheck");
            configuration.put(IOfficeApplication.APPLICATION_ARGUMENTS_KEY, options.toArray(new String[0]));

            officeApplication = OfficeApplicationRuntime.getApplication(configuration);
            officeApplication.activate();
            officeApplication.getDesktopService().activateTerminationPrevention();

            logger.log(Level.INFO, "LibreOffice running with pid " + ProcessUtil.getPid(PORT));

        } catch (Exception e1) {
            e1.printStackTrace();
        }

    }


    /*
     * Construct a new LibreOffice frame embedded into this viewer panel.
     */
    private void constructLOFrame() {
        try {

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    //System.out.println("Constructing LibreOffice frame...");
                    noaPanel.removeAll();
                    nat = new NativeView(libPath);
                    noaPanel.add(nat);
                    noaPanel.addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentResized(ComponentEvent e) {
                            nat.setPreferredSize(new Dimension(noaPanel.getWidth(), noaPanel.getHeight() - 5));
                            noaPanel.getLayout().layoutContainer(noaPanel);
                            super.componentResized(e);
                        }
                    });
                    nat.setPreferredSize(new Dimension(noaPanel.getWidth(), noaPanel.getHeight() - 5));
                    noaPanel.validate();
                    noaPanel.setVisible(false);
                    //noaPanel.getLayout().layoutContainer(noaPanel);

                    try {
                        officeFrame = officeApplication.getDesktopService().constructNewOfficeFrame(nat);
                        //System.out.println("LibreOffice frame ok");

                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            });
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void loadFile(final File file, Set<String> highlightTerms) {
        loadFile(file, "", highlightTerms);
    }

    @Override
    public void loadFile(final File file, final String contentType, final Set<String> highlightTerms) {

        lastFile = file;

        new Thread() {
            @Override
            public void run() {

                /*
                 * restart LO if still loading previous file, because it may
                 * take too long or may never end (corrupted doc or LO bug)
                 */
                synchronized (startLOLock) {
                    if (loading && (lastFile == file)) {
                        loading = false;
                        restartLO();
                    }
                }

                if (file != lastFile) {
                    return;
                }

                try {
                    //loadingThread = this;
                    restartCalled = false;

                    if (file != null) {
                        loading = true;
                        constructLOFrame();
                        setNoaPanelVisible(true);
                        DocumentDescriptor descriptor = DocumentDescriptor.DEFAULT;
                        descriptor.setReadOnly(true);

                        // Spreadsheets must have write permission to be highlighted
                        // no problem because we work with a temp file
                        if (isSpreadSheet(contentType)) {
                            descriptor.setReadOnly(false);
                        }

                        prevDocument = document;
                        document = officeApplication.getDocumentService().loadDocument(officeFrame, lastFile.getAbsolutePath(), descriptor);
                        ajustLayout();

                        if (prevDocument != null) {
                            prevDocument.close();
                        }

                    } else {
                        setNoaPanelVisible(false);
                    }

                    loading = false;

                    if (file != null && highlightTerms != null) {
                        highlightText(highlightTerms);
                    }

                    exception = null;

                } catch (Exception e) {
                    loading = false;

                    logger.log(Level.WARNING, e.toString());

                    //e.printStackTrace();

                    if (e.toString().contains("Unsupported URL")) {
                        //setNoaPanelVisible(false);
                    } else if (!restartCalled) {
                        synchronized (startLOLock) {
                            restartLO();
                        }
                        if (exception == null) {
                            loadFile(lastFile, contentType, highlightTerms);
                        }

                        exception = e;

                    }
                }

            }
        }.start();

    }

    /*
     * Some (corrupted) files might cause LibreOffice to block java EDT thread
     * for a log time or forever. So we monitor if the EDT is not responsive
     * for some period and, if blocked, LO is restarted.
     */
    private Thread monitorEventThreadBlocking() {
        edtMonitor = new Thread() {
            volatile boolean blocked;

            public void run() {
                while (!this.isInterrupted()) {
                    blocked = true;
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            blocked = false;
                        }
                    });
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        this.interrupt();
                    }

                    if (blocked && lastFile != null && !this.isInterrupted()) {
                        logger.log(Level.WARNING, "GUI blocked while rendering in LibreOffice!");
                        System.out.println();
                        synchronized (startLOLock) {
                            restartLO();
                        }
                        //loadFile(lastFile, lastContentType, lastHighlightTerms);
                    }
                }
            }
        };

        edtMonitor.setDaemon(true);
        edtMonitor.start();
        return edtMonitor;

    }


    /*
     * Restart LibreOffice process
     */
    public void restartLO() {
        logger.log(Level.INFO, RESTART_MSG);
        restartCalled = true;

        //loadingThread.interrupt();
        ProcessUtil.killProcess(PORT);
        //process.destroy();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    noaPanel.removeAll();
                    noaPanel.add(new JLabel(RESTART_MSG));
                    noaPanel.validate();
                }
            });
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        startLO();
        document = null;
        constructLOFrame();

        logger.log(Level.INFO, "LibreOffice restarted.");
    }

    /*
     * Hide menu bars, fit to width and finish presentations
     * TODO: block presentation play before displaying it.
     */
    private void ajustLayout() {
        if (document != null) {
            try {
                officeFrame.getLayoutManager().hideAll();

                if (document instanceof ITextDocument) {
                    ((ITextDocument) document).zoom(DocumentZoomType.PAGE_WIDTH, (short) 100);
                }

                if (document instanceof IPresentationDocument) {
                    ((IPresentationDocument) document).getPresentationSupplier().getPresentation().end();
                }

            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
    }

    /*
     * Change viewer visibility.
     */
    public void setNoaPanelVisible(final boolean visible) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                noaPanel.setVisible(visible);
                noaPanel.setSize(noaPanel.getWidth() + delta, noaPanel.getHeight());
                delta *= -1;
            }
        });
    }

    @Override
    public void dispose() {
        edtMonitor.interrupt();

        if (officeApplication != null) {
            try {
                officeApplication.deactivate();
                officeApplication.dispose();
                ProcessUtil.killProcess(PORT);

            } catch (OfficeApplicationException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void copyScreen() {
        XWindow xWindow = officeFrame.getXFrame().getContainerWindow();

        XDevice xDevice = UnoRuntime.queryInterface(XDevice.class, xWindow);
        XBitmap xBitmap = xDevice.createBitmap(0, 0, this.getPanel().getWidth(), this.getPanel().getHeight());

        XGraphics xGraphics = xDevice.createGraphics();

        if (xBitmap != null) {
            XDisplayBitmap xDisplayBitmap = xDevice.createDisplayBitmap(xBitmap);

            com.sun.star.awt.Size aSize = xBitmap.getSize();

            xGraphics.draw(xDisplayBitmap, 0, 0, aSize.Width, aSize.Height, 0, 0, this.getPanel().getWidth(), this.getPanel().getHeight());

            byte array[] = xBitmap.getDIB();

            InputStream in = new ByteArrayInputStream(array);
            BufferedImage image = null;
            try {
                image = ImageIO.read(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
            TransferableImage trans = new TransferableImage(image);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(trans, trans);
        }
    }

    private void highlightText(Set<String> terms) {
        currentHit = -1;
        totalHits = 0;
        hits = new ArrayList<Object>();
        if (terms.size() == 0) {
            return;
        }

        try {
            if (document instanceof ITextDocument) {
                ITextDocument textDocument = (ITextDocument) document;

                for (String term : terms) {
                    SearchDescriptor searchDescriptor = new SearchDescriptor(term);
                    searchDescriptor.setIsCaseSensitive(false);
                    searchDescriptor.setUseCompleteWords(false);
                    ISearchResult searchResult = ((ITextDocument) document).getSearchService().findAll(searchDescriptor);
                    ITextRange[] textRanges = searchResult.getTextRanges();
                    for (ITextRange range : textRanges) {
                        if (range != null) {
                            XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, range.getXTextRange());
                            xPropertySet.setPropertyValue("CharBackColor", 0xFFFF00);
                            xPropertySet.setPropertyValue("CharColor", 0x000000);
                            hits.add(range);
                            totalHits++;
                            if (totalHits == 1) {
                                textDocument.getViewCursorService().getViewCursor().goToRange(range, false);
                                currentHit = 0;
                            }
                        }
                    }
                }

            } else if (document instanceof ISpreadsheetDocument) {
                for (String term : terms) {
                    ISpreadsheetDocument spreadsheetDocument = (ISpreadsheetDocument) document;
                    XSpreadsheets spreadsheets = spreadsheetDocument.getSpreadsheetDocument().getSheets();
                    for (String sheetName : spreadsheets.getElementNames()) {
                        XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, spreadsheets.getByName(sheetName));
                        XProtectable protectable = UnoRuntime.queryInterface(XProtectable.class, sheet);
                        if (protectable.isProtected()) {
                            System.out.println("Protected sheet:" + sheetName);
                        }
                        //protectable.unprotect("");
                        XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, sheet);
                        XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
                        xSearchDesc.setSearchString(term);
                        xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE);
                        xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE);

                        XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);
                        if (xIndexAccess != null) {
                            for (int i = 0; i < xIndexAccess.getCount(); i++) {

                                Any any = (Any) xIndexAccess.getByIndex(i);
                                XCellRange xCellRange = UnoRuntime.queryInterface(XCellRange.class, any);
                                XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xCellRange);
                                xPropertySet.setPropertyValue("CellBackColor", 0xFFFF00);

                                for (int ri = 0; true; ri++) {
                                    boolean riOutBound = false;
                                    for (int rj = 0; true; rj++) {
                                        XCell xCell;
                                        try {
                                            xCell = xCellRange.getCellByPosition(ri, rj);
                                        } catch (com.sun.star.lang.IndexOutOfBoundsException e) {
                                            if (rj == 0) {
                                                riOutBound = true;
                                            }
                                            break;
                                        }

                                        //CellProtection cellProtection = (CellProtection)xPropertySet.getPropertyValue("CellProtection");
                                        //cellProtection.IsLocked = false;
                                        //xPropertySet.setPropertyValue("CellProtection", cellProtection);

                                        XTextRange textRange = UnoRuntime.queryInterface(XTextRange.class, xCell);
                                        XTextCursor xTextCursor = textRange.getText().createTextCursor();
                                        String cellText = textRange.getString().toLowerCase();
                                        short start = -1, off = 0;
                                        do {
                                            off = (short) (start + 1);
                                            start = (short) cellText.indexOf(term, off);
                                            if (start != -1) {
                                                xTextCursor.gotoRange(textRange.getStart(), false);
                                                xTextCursor.goRight(start, false);
                                                xTextCursor.goRight((short) term.length(), true);

                                                xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
                                                if (xPropertySet != null) {
                                                    //for(Property prop : xPropertySet.getPropertySetInfo().getProperties())
                                                    //System.out.println(prop.Name + " " + prop.toString());
                                                    xPropertySet.setPropertyValue("CharColor", 0xFF0000);
                                                    xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD);
                                                    xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD);
                                                }
                                            }

                                        } while (start != -1);

                                        Object[] sheetHit = new Object[2];
                                        sheetHit[0] = sheet;
                                        sheetHit[1] = xCell;
                                        hits.add(sheetHit);
                                        totalHits++;
                                        if (totalHits == 1) {
                                            XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
                                            spreadsheetView.setActiveSheet(sheet);
                                            XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
                                            xSel.select(xCell);
                                            currentHit = 0;
                                        }

                                    }
                                    if (riOutBound) {
                                        break;
                                    }
                                }


                            }
                        }
                    }
                }


            } else if (document instanceof IPresentationDocument) {
                for (String term : terms) {
                    XDrawPagesSupplier supplier = UnoRuntime.queryInterface(XDrawPagesSupplier.class, document.getXComponent());
                    XDrawPages xDrawPages = supplier.getDrawPages();
                    int numPages = xDrawPages.getCount();
                    for (int k = 0; k < numPages; k++) {

                        XDrawPage xDrawPage = UnoRuntime.queryInterface(XDrawPage.class, xDrawPages.getByIndex(k));
                        boolean addedPage = false;

                        XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, xDrawPage);
                        if (xSearchable == null) {
                            continue;
                        }
                        XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
                        xSearchDesc.setSearchString(term);
                        xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE);
                        xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE);
                        xSearchDesc.setPropertyValue("SearchBackwards", Boolean.FALSE);

                        XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);

                        String preText = "";
                        if (xIndexAccess != null) {
                            for (int i = 0; i < xIndexAccess.getCount(); i++) {
                                Proxy any = (Proxy) xIndexAccess.getByIndex(i);

                                XTextRange textRange = UnoRuntime.queryInterface(XTextRange.class, any);
                                String text = textRange.getText().getString().toLowerCase();
                                if (text.equals(preText)) {
                                    continue;
                                }

                                XTextCursor xTextCursor = textRange.getText().createTextCursor();
                                short start = -1, off = 0;
                                do {
                                    off = (short) (start + 1);
                                    start = (short) text.indexOf(term, off);
                                    if (start != -1) {
                                        xTextCursor.gotoRange(textRange.getText().getStart(), false);
                                        xTextCursor.goRight(start, false);
                                        xTextCursor.goRight((short) term.length(), true);

                                        XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
                                        if (xPropertySet != null) {
                                            xPropertySet.setPropertyValue("CharColor", 0xFF0000);
                                            xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD);
                                            xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD);
                                        }

                                    }
                                } while (start != -1);

                                if (!addedPage) {
                                    hits.add(xDrawPage);
                                    totalHits++;
                                    addedPage = true;
                                }

                                if (totalHits == 1) {
                                    XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
                                    drawView.setCurrentPage(xDrawPage);
                                    currentHit = 0;
                                }

                                preText = text;
                            }
                        }
                    }

                }
            }

        } catch (Exception e) {
            System.out.println("Error while highlighting");
            //e.printStackTrace();
        }
    }

    @Override
    public void scrollToNextHit(final boolean forward) {

        new Thread() {
            @Override
            public void run() {

                try {

                    if (forward) {
                        if (currentHit < totalHits - 1) {
                            if (document instanceof ITextDocument) {
                                ITextDocument textDocument = (ITextDocument) document;
                                textDocument.getViewCursorService().getViewCursor().goToRange((ITextRange) hits.get(++currentHit), false);

                            } else if (document instanceof ISpreadsheetDocument) {
                                Object[] sheetHit = (Object[]) hits.get(++currentHit);
                                XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
                                spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
                                XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
                                xSel.select(sheetHit[1]);

                            } else if (document instanceof IPresentationDocument) {

                                XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
                                drawView.setCurrentPage((XDrawPage) hits.get(++currentHit));

                            }
                        }

                    } else {
                        if (currentHit > 0) {
                            if (document instanceof ITextDocument) {
                                ITextDocument textDocument = (ITextDocument) document;
                                textDocument.getViewCursorService().getViewCursor().goToRange((ITextRange) hits.get(--currentHit), false);

                            } else if (document instanceof ISpreadsheetDocument) {
                                Object[] sheetHit = (Object[]) hits.get(--currentHit);
                                XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
                                spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
                                XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
                                xSel.select(sheetHit[1]);

                            } else if (document instanceof IPresentationDocument) {
                                XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
                                drawView.setCurrentPage((XDrawPage) hits.get(--currentHit));

                            }
                        }

                    }

                } catch (Exception e) {
                    //e.printStackTrace();
                    System.out.println("Error while scrolling to hit");
                }

            }
        }.start();



    }
}
