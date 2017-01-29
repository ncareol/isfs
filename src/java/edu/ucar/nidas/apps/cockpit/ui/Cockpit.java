// -*- mode: java; indent-tabs-mode: nil; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
 ** ISFS: NCAR Integrated Surface Flux System software
 **
 ** 2016, Copyright University Corporation for Atmospheric Research
 **
 ** This program is free software; you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation; either version 2 of the License, or
 ** (at your option) any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** The LICENSE.txt file accompanying this software contains
 ** a copy of the GNU General Public License. If it is not found,
 ** write to the Free Software Foundation, Inc.,
 ** 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **
 ********************************************************************
*/

package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import com.trolltech.qt.core.QObject;
import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.QDir;
import com.trolltech.qt.core.QTranslator;
import com.trolltech.qt.core.QLocale;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QCloseEvent;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QToolTip;
import com.trolltech.qt.gui.QAction;
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
import com.trolltech.qt.gui.QDesktopWidget;

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

    private StatusBar _statusbar;

    private QAction _connectAction;

    private QMenu _addPage;

    private QAction _freezeAllPlotAction;

    private QAction _unfreezeAllPlotAction;

    private QAction _freezeAllGridAction;

    private QAction _unfreezeAllGridAction;

    private QAction _autoCycleTabsAction;

    private LogDialog _logDialog;

    private Log _log;

    /**
     * Config file, specifed in runstring, or in dialog.
     */
    private String _configFileName = null;

    private String defaultConfigName = QDir.current().filePath("cockpit.xml");
    
    /**
     * UDP data server address.
     */
    private String _connAddress = "localhost"; 

    private int _connPort = 30005;

    /**
     * Local UDP port used to send connection packet.
     * If the request is for unicast data, then the serverr
     * will also send the data to this local port.
     * If that port is in use, ports up to _unicastPort + 10
     * will be tried.
     * If a multicast request is made, then the UDP multicast port
     * number is provided by the server in its response.
     */
    private int _unicastPort = 30050;

    public int getUnicastPort()
    {
        return _unicastPort;
    }

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

    private final Object _dataThreadLock = new Object();

    private Reconnector _reconnector = null;

    /**
     * Data reduction period, in milliseconds. Points will
     * be plotted at this interval, typically 1000 msec for
     * 1 second data points.
     */
    private int _statisticsPeriod = 1000;

    /**
     * Cockpit constructor.
     */
    public Cockpit(String[] args)
    {
        if (args != null && args.length > 0) parseArgs(args);

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

	_udpConnection = new UdpConnection(_unicastPort);

	_reconnector = this.new Reconnector();

        connect(true);

        String cname = getConfigFileName();
        if (cname != null) {
            try {
                Document document = DOMUtils.parseXML(
                    new FileInputStream(cname), false);
                CockpitConfig config = new CockpitConfig(document);
                _centWidget.apply(config);
            }
            catch(Exception e) {
                status(e.getMessage());
                logError(e.toString());
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

    public DataSource getDataSource(String varName) 
    {
        return _dataProcessorByVarName.get(varName);
    }

    @Override
    public void closeEvent(QCloseEvent event)
    {
        // System.out.println("close event");
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
        status(tr("Starting"), 10000);

        //create menu items
        createMenuItems(); 
        show();
    }

    /**
     * Tooltips on menu items are not supported directly in Qt 4.
     * This was cobbled together from web postings.
     */
    public static class QMenuActionWithToolTip extends QAction
    {
        private QMenu _menu;

        public QMenuActionWithToolTip(String text, String tooltip, QMenu parent)
        {
            super(text, parent);
            _menu = parent;
            setToolTip(tooltip);
            hovered.connect(this, "showToolTip()");
        }
        public void showToolTip()
        {
            // System.err.println("showToolTip: " + toolTip());
            // int val = (Integer)data();
            // System.err.println("showToolTip, data=: " + val);
            QRect rect = _menu.actionGeometry(this);
            // System.err.println(rect.toString());
            int pos_x = _menu.pos().x() + rect.x();
            int pos_y = _menu.pos().y() + rect.y() - rect.height() / 2;
            QPoint pos = new QPoint(pos_x, pos_y);
            /*
            System.err.printf("pos= %d, %d\n",pos.x(), pos.y());
            QPoint gpos = _menu.mapToGlobal(pos);
            System.err.printf("gpos= %d, %d\n",gpos.x(), gpos.y());
            */
            QRect screen = QApplication.desktop().availableGeometry(_menu);
            /*
            System.err.printf("screen= %d x %d\n",screen.width(), screen.height());
            */
            if (pos.x() > screen.width() / 2) {
                /* If LHS of menu is more than half way across screen, put
                 * tooltip below menu item. */
                pos.setY(pos_y + rect.height());
            }
            else {
                /* If LHS of menu is less than half way across screen, put
                 * tooltip to right of menu item. */
                pos.setX(pos_x + rect.width());
            }
            QToolTip.showText(pos, toolTip());
        }
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
        QMenu file = menuBar.addMenu(tr("&File"));
        menuBar.setFont(new QFont("Ariel", 12));

        QAction action = new QMenuActionWithToolTip(tr("&Connect"),
                tr("Connect to data server"), file);
        action.triggered.connect(this, "connect()");
        file.addAction(action);
        _connectAction = action;

        action = new QMenuActionWithToolTip(tr("&Show Log"),
                tr("Show log message window"), file);
        action.triggered.connect(this, "showLog()");
        file.addAction(action);

        file.addAction(tr("&Save Config"), this, "saveConfig()");
        file.addAction(tr("&Open Config"), this, "openConfig()");
        file.addAction(tr("&Exit"), this, "close()");

        _addPage = menuBar.addMenu(tr("Add"));
        _addPage.addAction(tr("New Page"), this, "addGaugePage()");
        //_addPage.addAction("SortPageByVariable", _centWidget, "addVariablePage()");
        //_addPage.addAction("SortPageByHeight", _centWidget, "addHeightPage()");
        _addPage.setEnabled(false);

        // Top menu of global options
        QMenu topMenu = menuBar.addMenu(tr("&Global Options"));

        action = new QMenuActionWithToolTip(tr("&Clear All History"),
                tr("Clear history shadows on all plots"), topMenu);
        action.triggered.connect(_centWidget, "globalClearHistory()");
        topMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Freeze Plot Sizes"),
                tr("Fix plot sizes on all plot pages"), topMenu);
        action.triggered.connect(_centWidget, "freezePlotSizes()");
        topMenu.addAction(action);
        _freezeAllPlotAction = action;

        action = new QMenuActionWithToolTip(tr("Unfreeze Plot Sizes"), 
            tr("Allow plot sizes to vary, losing history shadow"),
            topMenu);
        action.triggered.connect(_centWidget, "unfreezePlotSizes()");
        topMenu.addAction(action);
        _unfreezeAllPlotAction = action;
        disableUnfreezePlotSizeMenu();

        action = new QMenuActionWithToolTip(tr("Freeze Grid Layouts"), 
            tr("Fix grid layout of plots"),
            topMenu);
        action.triggered.connect(_centWidget, "freezeGridLayouts()");
        topMenu.addAction(action);
        _freezeAllGridAction = action;

        action = new QMenuActionWithToolTip(tr("Unfreeze Grid Layouts"), 
            tr("Allow grid layout to change"),
            topMenu);
        action.triggered.connect(_centWidget, "unfreezeGridLayouts()");
        topMenu.addAction(action);
        _unfreezeAllGridAction = action;
        disableUnfreezeGridLayoutMenu();

        action = new QMenuActionWithToolTip(tr("Auto Cycle &Tabs"), 
            tr("Cycle through plot pages"), topMenu);
        action.triggered.connect(_centWidget, "toggleTabCycle()");
        topMenu.addAction(action);
        _autoCycleTabsAction = action;

        action = new QMenuActionWithToolTip(tr("Change Plot Time &Width"), 
            tr("Change time scale on all plots, losing history"),
            topMenu);
        action.triggered.connect(_centWidget, "changeAllPlotTimeWidth()");
        topMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Set Data &Timeout"), 
            tr("Set data timeout value for all plots, in seconds"),
            topMenu);
        action.triggered.connect(_centWidget, "setDataTimeoutSec()");
        topMenu.addAction(action);

        topMenu.addAction(tr("Auto Scale Plots"), _centWidget,
                tr("globalAutoScalePlots()"));

        // Top menu of page options
        topMenu = menuBar.addMenu(tr("&Page Options"));
        action = new QMenuActionWithToolTip(tr("&Clear History"),
                tr("Clear history shadow on plots in current page"), topMenu);
        action.triggered.connect(_centWidget, "pageClearHistory()");
        topMenu.addAction(action);

        QMenu subMenu = topMenu.addMenu(tr("Color"));
        action = new QMenuActionWithToolTip(tr("Change &Trace Color"),
                tr("Change trace color on plots in current page"), subMenu);
        action.triggered.connect(_centWidget, "pageTraceColor()");
        subMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Change &History Color"),
                tr("Change color of history shadows in current page"), subMenu);
        action.triggered.connect(_centWidget, "pageHistoryColor()");
        subMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Change &Background Color"),
                tr("Change background color in current page, losing history"), subMenu);
        action.triggered.connect(_centWidget, "pageBackgroundColor()");
        subMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Change Plot Time &Width"), 
            tr("Change time scale of plots on current page, losing history"),
            topMenu);
        action.triggered.connect(_centWidget, "changePagePlotTimeWidth()");
        topMenu.addAction(action);

        action = new QMenuActionWithToolTip(tr("Set Data &Timeout"), 
            tr("Set data timeout value for plots on current page, in seconds"),
            topMenu);
        action.triggered.connect(_centWidget, "setSingleDataTimeout()");
        topMenu.addAction(action);

    }

    public void showToolTip()
    {
        System.err.println("showToolTip");
    }

    public void disableFreezePlotSizeMenu()
    {
        _freezeAllPlotAction.setEnabled(false);
    }

    public void enableFreezePlotSizeMenu()
    {
        _freezeAllPlotAction.setEnabled(true);
    }

    public void disableUnfreezePlotSizeMenu()
    {
        _unfreezeAllPlotAction.setEnabled(false);
    }

    public void enableUnfreezePlotSizeMenu()
    {
        _unfreezeAllPlotAction.setEnabled(true);
    }

    public void disableFreezeGridLayoutMenu()
    {
        _freezeAllGridAction.setEnabled(false);
    }

    public void enableFreezeGridLayoutMenu()
    {
        _freezeAllGridAction.setEnabled(true);
    }

    public void disableUnfreezeGridLayoutMenu()
    {
        _unfreezeAllGridAction.setEnabled(false);
    }

    public void enableUnfreezeGridLayoutMenu()
    {
        _unfreezeAllGridAction.setEnabled(true);
    }

    /**
     * Tell Cockpit that tabs are cycling, so
     * change tab cycle menu action to "Stop".
     */
    public void startCycleTabs()
    {
        _autoCycleTabsAction.setText(tr("Stop Cycle Tabs"));
    }

    /**
     * Tell Cockpit that tabs are no cycling, so
     * change tab cycle menu action.
     */
    public void stopCycleTabs()
    {
        _autoCycleTabsAction.setText(tr("Auto Cycle Tabs"));
    }

    public void showLog()
    {
        _logDialog.raise();
        _logDialog.show();
    }

    public void shutdown()
    {
        synchronized(_dataThreadLock) {
            try {
                if (_dataThread != null) {
                    _dataThread.interrupt();
                    _dataThread.join();
                }
            }
            catch (InterruptedException e) {}
        }

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
    
    private void addGaugePage()
    {
        NewGaugePageDialog ngp = new NewGaugePageDialog(this);

        List<Var> vars = ngp.getSelectedVariables();
        String name = ngp.getName();
        /*
        System.err.printf("# vars from dialog=%d\n",
                vars.size());
        */
        if (!vars.isEmpty())
            _centWidget.addGaugePage(name, vars);
    }
    
    private void saveConfig()
    {
        String cname = getConfigFileName();
        if (cname == null)
            cname = defaultConfigName;

	cname = QFileDialog.getSaveFileName(this, tr("Save File"), cname);
        if (cname == null || cname.isEmpty()){
            statusBar().showMessage(tr("No configuration file."), 10000); //10 sec
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
	cname = QFileDialog.getOpenFileName(this, tr("Open File"), cname);
        setConfigFileName(cname);
        try {
            Document document = DOMUtils.parseXML(new FileInputStream(cname), false);
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
            _udpConnection.connect(_udpConnInfo,
                    _log, _connDialog.getDebug());
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

        synchronized(_dataThreadLock) {
            if (_dataThread != null) _dataThread.interrupt();
            _dataThread = new UdpDataReaderThread(
                    _udpConnection.getUdpSocket(), _statusbar, _log, _reconnector);
        }
        _dataProcessorByVarName.clear();
        _varsByName.clear();

        /*
         * Create DataProcessors for all variables.
         */
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
                            dc = new MinMaxer(_statisticsPeriod);
                            _dataProcessorByVarName.put(var.getNameWithStn(),dc);
                        }
                        _dataThread.addClient(samp,var,dc);

                    }
                }
            }
        }

        _centWidget.setName(projectname);

        _centWidget.connect(sites);

        status(tr("No data yet..."), 10000);
        
        _connectAction.setEnabled(false);
        _addPage.setEnabled(true);

        _dataThread.start();

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));

        return true;
    }

    public boolean connect()
    {
        return connect(true);
    }

    private  void openImage() {
        if (gnodataImg != null) return;
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
    private void parseArgs(String[] args)
    {
        for (int i = 0; i < args.length; i++)
        {
            if ("-s".equals(args[i]) && i + 1 < args.length){
                String opt = args[i+1].trim();
                String[] ss = opt.split(":");
                if (ss.length >= 1)
                    _connAddress = ss[0];
                if (ss.length > 1)
                    _connPort = Integer.valueOf(ss[1]);
            }
            else if ("-c".equals(args[i]) && i + 1 < args.length ) {
                String cname = args[++i];
                if (cname.length() > 0) {
                    if (QDir.isRelativePath(cname))
                        cname = QDir.current().filePath(cname);
                    setConfigFileName(cname);
                }
            }
            else if ("-arg".equals(args[i]) || "-open".equals(args[i]) &&
                i + 1 < args.length) {
                //args from jnlp
                String op = args[++i];
                String[] trs = op.split(" ");
                if (trs.length != 2) {
                    status(tr("Invalid argument: ") + op);
                    logError(tr("Invalid argument: ") + op);
                }
                else parseArgs(trs);
            }
        }
    }

    /**
     * Construct a cockpit mainframe, and only one mainframe
     * options: -s to pass address:port example:
     *  -s   porter.eol.ucar.edu:30000
     */
    public static void main(String[] args)
    {
        QApplication.initialize(args);

	QTranslator qtTranslator = new QTranslator();
	qtTranslator.load("classpath:/translations_directory/qt_" + QLocale.system().name() + ".qm");
        QApplication.installTranslator(qtTranslator);

        if (false) {
	    QTranslator myappTranslator = new QTranslator();
	    myappTranslator.load("classpath:/translations_directory/myapp_" + QLocale.system().name() + ".qm");
	    QApplication.installTranslator(myappTranslator);
	}

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
            status(tr("Attempting reconnect"));
            while (!connect(false)) {
                status(tr("Attempting reconnect"));
                QApplication.processEvents();
            }
            status(tr("Reconnected"),5*1000);
	}
    }

    public int confirmMessageBox(String s, String title) {
        int ret =
            QMessageBox.warning(this, title, s,
                    StandardButton.Ok, StandardButton.Abort);
        return ret;
    }
}
