package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.QDir;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QCloseEvent;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QFont;
import com.trolltech.qt.gui.QImage;
import com.trolltech.qt.gui.QMainWindow;
import com.trolltech.qt.gui.QMenu;
import com.trolltech.qt.gui.QMenuBar;
import com.trolltech.qt.gui.QPalette;
import com.trolltech.qt.gui.QStatusBar;
import com.trolltech.qt.gui.QFileDialog;
import com.trolltech.qt.gui.QMessageBox;
import com.trolltech.qt.gui.QMessageBox.StandardButton;

import edu.ucar.nidas.apps.cockpit.model.CockpitConfig;
import edu.ucar.nidas.apps.cockpit.model.MinMaxer;
import edu.ucar.nidas.core.UdpConnection;
import edu.ucar.nidas.core.UdpConnInfo;
import edu.ucar.nidas.core.UdpDataReaderThread;
import edu.ucar.nidas.model.Site;
import edu.ucar.nidas.model.Dsm;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.model.DataProcessor;
import edu.ucar.nidas.model.DataSource;
import edu.ucar.nidas.model.DataClient;
import edu.ucar.nidas.model.QProxyDataClient;
import edu.ucar.nidas.model.NotifyClient;
import edu.ucar.nidas.ui.StatusBar;
import edu.ucar.nidas.ui.LogDisplay;
import edu.ucar.nidas.model.StatusDisplay;
import edu.ucar.nidas.model.Log;
import edu.ucar.nidas.util.DOMUtils;

import org.w3c.dom.Document;
import javax.xml.parsers.ParserConfigurationException;

/**
 * this is the main program of cockpit.
 * It contains a mainWindow ui instance,
 * an array of gauges.
 * and a timer
 * 
 * @author dongl
 *
 */

public class Cockpit extends QMainWindow {

    /**
     * This class is the cockpit main class. 
     * It controls the UI, 
     *    data connection, 
     *    and cent-tab-wdiget
     */

    /**
     * global defaults
     */
    static QImage gnodataImg = null;  //nodataImg-rip

    public static QColor defTraceColor = new QColor(255,255,0);
    public static QColor defHistoryColor = new QColor(170,170,255);

    public static ArrayList<QColor> defBGColors = new ArrayList<QColor>();

    public static final int gwdef = 120;
    public static final int ghdef = 80;

    /**
     * menu etc
     */
    private QMenu _file;

    // private QMenu _tabsetup;

    private QMenu _add;

    public QMenu gsetup;

    private StatusBar _statusbar;

    private LogDialog _logDialog;

    private Log _log;

    /**
     * Config file, specifed in runstring, or in dialog.
     */
    private String _configFileName = null;

    private String defaultConfigName = QDir.current().filePath("cockpit.conf");
    
    /**
     * UDP data server address.
     */
    private String _connAddress = "localhost"; 
    private int _connPort = 30005;

    private ConnectionDialog _connDialog = null;

    /**
     * Parameters associated with the current data connection.
     */
    private UdpConnInfo _udpConnInfo = null;

    /**
     * UdpConnection
     */
    private UdpConnection _udpConnection = null;

    /*
     * Variables by their name.
     */
    private HashMap<String, Var> _varsByName = new HashMap<String, Var>();

    /**
     * list of data clients by variable and by
     * index of data value in the variable's data array.
     */
    private HashMap<String, DataProcessor> _dataProcessorByVarName =
        new HashMap<String, DataProcessor>();

    /**
     * cent widget for all tabs
     */
    private CentTabWidget _centWidget = null;

    private UdpDataReaderThread _dataThread = null;

    private Reconnector _reconnector = null;

    /**
     * Data reduction period, in milliseconds. Points will
     * be plotted at this interval, typically 1000 msec for
     * 1 second data points.
     */
    private int _reductionPeriod = 1000;

    /**
     * Cockpit constructor.
     */
    public Cockpit(String[] args)
    {
        if (args != null && args.length > 0) parseArg(args);

        // Just in case we have more than one cockpits :-)
        if (defBGColors.isEmpty()) {
            defBGColors.add(new QColor(0,85,127));
            defBGColors.add(new QColor(82,85,79));
            defBGColors.add(new QColor(85,85,127));
            defBGColors.add(new QColor(65,94,84));
        }

        connectSlotsByName();
        setHidden(true);

        _statusbar = new StatusBar(this);
        _logDialog = new LogDialog(this);
        _log = new LogDisplay(_logDialog.getTextWidget());
        setGeometry(PageGeometry.x,  PageGeometry.y,
                PageGeometry.w, PageGeometry.h);  
        _centWidget = new CentTabWidget(this);
        setCentralWidget(_centWidget); 
        createUIs();
        openImage();

	_udpConnection = new UdpConnection();

	_reconnector = this.new Reconnector();

        connect(true);

        String cname = getConfigFileName();
        if (cname != null) {
            try {
                Document document = DOMUtils.parseXML(
                        new FileInputStream(cname), true);
                CockpitConfig config = new CockpitConfig(document);
                _centWidget.apply(config);
            }
            catch(Exception e) {
                status(e.getMessage());
                logError(e.getMessage());
            }
        }
    }

    public Log getLog()
    {
        return _log;
    }

    public Var getVar(String name) 
    {
        return _varsByName.get(name);
    }

    public Set<String> getVarNames() 
    {
        return _varsByName.keySet();
    }

    public Collection<Var> getVars() 
    {
        return _varsByName.values();
    }

    public DataSource getDataSource(Var var) 
    {
        return _dataProcessorByVarName.get(var.getNameWithStn());
    }

    @Override
    public void closeEvent(QCloseEvent event)
    {
        System.out.println("close event");
        shutdown();
        event.accept();
    }

    public void setConnAddress(String val)
    {
        _connAddress = val;
    }

    public String getConnAddress() { return _connAddress; }

    public void setConnPort(int val) {_connPort = val;}

    public int getConnPort() { return _connPort; }
    
    public CentTabWidget getCentWidget() { return _centWidget; }

    public StatusDisplay getStatusDisplay() { return _statusbar; }

    public void status(String msg, int ms)
    {
        _statusbar.show(msg,ms);
    }
      
    public void status(String msg)
    {
        _statusbar.show(msg);
    }
      
    public void logError(String msg)
    {
        _log.error(msg);
    }
      
    public void logInfo(String msg)
    {
        _log.info(msg);
    }
      
    public void logDebug(String msg)
    {
        _log.debug(msg);
    }
      
    private void createUIs()
    {
        // status
        status("Start ...", 10000);

        //create menu items
        createMenuItems(); 
        show();
    }

    private void createMenuItems()
    {
        //menuBar
        QMenuBar menuBar = new QMenuBar(this);
        QPalette pl = new QPalette(Qt.GlobalColor.lightGray);
        menuBar.setPalette(pl);

        menuBar.setObjectName("menuBar");
        menuBar.setGeometry(new QRect(0, 0, 1290, 25));
        setMenuBar(menuBar);

        //file and items
        _file = menuBar.addMenu("&File");
        menuBar.setFont(new QFont("Ariel", 12));
        _file.addAction("&Connect", this, "connect()");
        _file.addAction("Show &Log", this, "showLog()");
        _file.addAction("&Save_Config", this, "saveConfig()");
        _file.addAction("&Open_Config", this, "openConfig()");
        _file.addAction("&Exit", this, "close()");

        /*

        //current-tab setup
        _tabsetup = menuBar.addMenu("&Page Setup");
        _tabsetup.addAction("&UnFreeze Layout", this, "unfreezePage()");
        _tabsetup.addAction("&AutoScale_Plots", _centWidget, "gautoScalePlots()");
        _tabsetup.addAction("&ManualScale_Plots", _centWidget, "rescaleGaugesInTab()");
        QMenu clr = _tabsetup.addMenu("Color");
        clr.addAction("Co&lor_Current", _centWidget, "colorCurrent()");
        clr.addAction("Color_&History", _centWidget, "colorHistory()");
        clr.addAction("Color_&BackGround", _centWidget, "colorBackGround()");
        clr.addAction("&CleanUp_History", _centWidget, "clearHistory()");
        QMenu sort = _tabsetup.addMenu("SortBy");
        // sort.addAction("Variable", _centWidget, "sortVariable()");
        // sort.addAction("Height", _centWidget, "sortHeight()");
        // _tabsetup.addAction("DeletePage", _centWidget, "closeTab()");
        QMenu tmsetup = _tabsetup.addMenu("TimeSetup");
        tmsetup.addAction("&PlotTimeWidth", _centWidget, "changeSinglePlotWidthMsec()");
        tmsetup.addAction("Data&Timeout", _centWidget, "setSingleDataTimeout()");
        _tabsetup.setEnabled(false);
        */

        //add 
        _add = menuBar.addMenu("Add");
        _add.addAction("NewPageByVar", this, "addPageByVar()");
        _add.addAction("NewPageByHt", this, "addPageByHt()");
        //_add.addAction("SortPageByVariable", _centWidget, "addVariablePage()");
        //_add.addAction("SortPageByHeight", _centWidget, "addHeightPage()");
        _add.setEnabled(false);

        //global setup
        gsetup = menuBar.addMenu("&Options");
        gsetup.addAction("&Freeze All Plot Sizes", _centWidget, "freezePlotSizes()");
        gsetup.addAction("&UnFreeze All Plot Sizes", _centWidget, "unfreezePlotSizes()");
        gsetup.addAction("&Freeze All Grids", _centWidget, "freezeGrids()");
        gsetup.addAction("&UnFreeze All Grids", _centWidget, "unfreezeGrids()");
        gsetup.addAction("&AutoScalePlots", _centWidget, "ggautoScalePlots()");
        gsetup.addAction("AutoCycleTabs", _centWidget, "autoCycleTabs()");
        QMenu cr = gsetup.addMenu("Color");
        cr.addAction("Co&lor_Current", _centWidget, "gcolorCurrent()");
        cr.addAction("Color_&History", _centWidget, "gcolorHistory()");
        cr.addAction("Color_&BackGround", _centWidget, "gcolorBackGround()");
        cr.addAction("&Clear History", _centWidget, "clearHistory()");
        QMenu srt = gsetup.addMenu("SortBy");
        // srt.addAction("Variable", _centWidget, "gsortVariable()");
        // srt.addAction("Height", _centWidget, "gsortHeight()");
        QMenu tmset = gsetup.addMenu("TimeSetup");
        tmset.addAction("&PlotTimeRange", _centWidget, "changePlotWidthMsec()");
        tmset.addAction("&NodataTimeout", _centWidget, "setDataTimeout()");
        gsetup.setEnabled(false);

    }

    public void showLog()
    {
        _logDialog.raise();
        _logDialog.show();
    }

    public void shutdown()
    {
        if (_dataThread != null) _dataThread.interrupt();
        _statusbar.close();
        try {
            _udpConnection.close();
        }
        catch(IOException e) {}
        // System.exit(0);
    }

    public void setConfigFileName(String val)
    {
        _configFileName = val;
    }
        
    public String getConfigFileName()
    {
        return _configFileName;
    }
    
    /**
     * Unfreeze the current GaugePage.
     */
    /*
    private void unfreezePage()
    {
        _centWidget.unfreezePage();
    }
    */
    
    private void addPageByVar()
    {
        new VarLookup(this);
    }
    
    private void addPageByHt()
    {
        new HtLookup(this);
    }
    
    private void saveConfig()
    {
        String cname = getConfigFileName();
        if (cname == null)
            cname = defaultConfigName;

	cname = QFileDialog.getSaveFileName(this, "Save File", cname);
        if (cname == null || cname.isEmpty()){
            statusBar().showMessage("configuration file is NOT selected.", 10000); //10 sec
        }
        setConfigFileName(cname);

        // read current configuration from display
        CockpitConfig config = new CockpitConfig(_centWidget);
        try {
            Document document = DOMUtils.newDocument();
            config.toDOM(document);
            DOMUtils.writeXML(document, cname);
        }
        catch(Exception e) {
            status(e.getMessage());
            logError(e.getMessage());
        }
    }

    private void openConfig()
    {
        String cname = getConfigFileName();
        if (cname == null)
            cname = defaultConfigName;
	cname = QFileDialog.getOpenFileName(this, "Open File", cname);
        setConfigFileName(cname);
        try {
            Document document = DOMUtils.parseXML(new FileInputStream(cname), true);
            CockpitConfig config = new CockpitConfig(document);
            _centWidget.apply(config);
        }
        catch(Exception e) {
            status(e.getMessage());
            logError(e.getMessage());
        }
    }

    /** 
     * Establish a data connection.
     * @return
     */
    public boolean connect(boolean dialog)
    {

        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        if (dialog) {
            // create modal dialog to establish connection
            _connDialog = new ConnectionDialog(this,
                _udpConnection, getConnAddress(), getConnPort());

            _udpConnInfo = _connDialog.getSelectedConnection();
            if (_udpConnInfo == null) return false;
        }
        else {
            String addr = _connDialog.getAddress();
            int port = _connDialog.getPort();
            int ttl = _connDialog.getTTL();
            ArrayList<UdpConnInfo> connections = null;
            try {
                connections = _udpConnection.search(addr, port, ttl,
                        getLog(), _connDialog.getDebug());
            }
            catch (IOException e) {
                status(e.getMessage());
                logError(e.getMessage());
                return false;
            }
            UdpConnInfo matchConn = null;
            for (UdpConnInfo conn : connections) {
                if (_udpConnInfo.getServer().equals(conn.getServer()) &&
                    _udpConnInfo.getProjectName().equals(conn.getProjectName())) {
                    matchConn = conn;
                    break;
                }
            }
            if (matchConn == null) {
                String msg = "Data connection for server " +
                    _udpConnInfo.getServer() +
                    " and project " +
                    _udpConnInfo.getProjectName() + " not found";
                status(msg);
                logError(msg);
                return false;
            }
            _udpConnInfo = matchConn;
        }

        String projectname = _udpConnInfo.getProjectName();

        setWindowTitle(projectname + " COCKPIT");

        try {
            _udpConnection.connect(_udpConnInfo, _log, _connDialog.getDebug());
        }
        catch (IOException e) {
            status(e.getMessage());
            logError(e.getMessage());
            return false;
        }
        ArrayList<Site> sites = null;
        try {
            Document doc = _udpConnection.readDOM();
            sites = Site.parse(doc);
        }
        catch (Exception e) {
            status("Parsing XML: " + e.getMessage());
            logError("Parsing XML: " + e.getMessage());
            return false;
        }

        if (_dataThread != null) {
            _dataThread.interrupt();
        }
        _dataThread = new UdpDataReaderThread(
                _udpConnection.getUdpSocket(), _statusbar, _log, _reconnector);
        _dataProcessorByVarName.clear();
        _varsByName.clear();

        for (Site site : sites) {
            ArrayList<Dsm> dsms = site.getDsms();

            for (Dsm dsm : dsms) {
                ArrayList<Sample> samps = dsm.getSamples();

                for (Sample samp : samps) {
                    ArrayList<Var> vars = samp.getVars();
                    
                    for (Var var : vars) {
                        if (var.getLength() != 1) continue;
                        _varsByName.put(var.getNameWithStn(), var);
                        DataProcessor dc = _dataProcessorByVarName.get(var.getNameWithStn());
                        if (dc == null) {
                            dc = new MinMaxer(_reductionPeriod);
                            _dataProcessorByVarName.put(var.getNameWithStn(),dc);
                        }
                        _dataThread.addClient(samp,var,dc);

                    }
                }
            }
        }

        _centWidget.setName(projectname);
        _centWidget.addGaugePages(sites);

        Collection<GaugePage> pages = _centWidget.getGaugePages();

        for (GaugePage page: pages) {
            List<Gauge> gauges = page.getGauges();
            for (Gauge gauge: gauges) {
                // System.out.println("gauge name=" + gauge.getName());
                Var var = _varsByName.get(gauge.getName());
                DataProcessor dc = _dataProcessorByVarName.get(var.getNameWithStn());
                DataClient proxy = QProxyDataClient.getProxy(gauge);
                dc.addClient(proxy);
            }
        }

        status("   No sensor data yet...", 10000);
        
        // _tabsetup.setEnabled(true);
        _add.setEnabled(true);
        gsetup.setEnabled(true);

        _file.actions().get(0).setEnabled(false);

        _dataThread.start();

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));

        return true;
    }

    public boolean connect()
    {
        return connect(true);
    }

    private  void openImage() {
        if (gnodataImg !=null) return;
        String nodatapath = "classpath:image/nodata.png";
        gnodataImg = new QImage(nodatapath);
        gnodataImg.setColor(1,new QColor(Qt.GlobalColor.red).rgb());
    }

    static class PageGeometry {
        static public int x = 350, y = 250, w = 1000, h = 700;
    }
    
    /**
     * parse the parameters received from user's input
     * [-s server:port] [-c config.xml] 
     * Silently ignores unrecognized arguments.
     * @param args  -s serv:port -c config.xml
     * @return      void
     */
    private void parseArg(String[] args)
    {
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equals("-s") && i + 1 < args.length){
                String opt = args[i+1].trim();
                String[] ss = opt.split(":");
                if (ss.length >= 1)
                    _connAddress = ss[0];
                if (ss.length > 1)
                    _connPort = Integer.valueOf(ss[1]);
            }
            else if (args[i].equals("-c") && i + 1 < args.length ) {
                String cname = args[++i];
                if (cname.length() > 0) {
                    if (QDir.isRelativePath(cname))
                        cname = QDir.current().filePath(cname);
                    setConfigFileName(cname);
                }
            }
            else if (args[i].equals("-arg") || args[i].equals("-open") &&
                //args from jnlp
                i + 1 < args.length) {
                String op = args[++i];
                String[] trs = op.split(" ");
                if (trs.length != 2) {
                    status("Invalid argument: " + op);
                    logError("Invalid argument: " + op);
                }
                else parseArg(trs);
            }
        }
    }

    /**
     * Construct a cockpit mainframe, and only one mainframe
     * options: -s to pass address:port example: -s   "porter.atd.ucar.edu:30000"
     */
    public static void main(String[] args) {
        QApplication.initialize(args);
        //setNativeLookAndFeel();
        Cockpit cockpit = new Cockpit(args);
        QApplication.execStatic();
        QApplication.shutdown();
    }

    private class Reconnector implements NotifyClient, Runnable
    {
        @Override
	public void wake()
	{
            QApplication.invokeLater(this);
	}

	/**
	 * Do the reconnection.
	 */
        @Override
	public void run()
	{
            status("Attempting reconnect...");
            while (!connect(false)) {
                status("Attempting reconnect...");
                QApplication.processEvents();
            }
            status("Reconnected",5*1000);
	}
    }

    public int confirmMessageBox(String s, String title) {
	// try {
	    int ret =
                QMessageBox.warning(this, title, s,
                        StandardButton.Ok, StandardButton.Abort);
	    return ret;
	// } catch (Exception e) {
	    // System.err.println(e.toString());
	// }
        // return StandardButton.Abort.value();
    }
}
