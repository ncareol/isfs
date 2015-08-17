package edu.ucar.nidas.apps.cockpit.ui;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.Qt;
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
import com.trolltech.qt.gui.QSizePolicy.Policy;

import edu.ucar.nidas.apps.cockpit.core.CpConnection;
import edu.ucar.nidas.apps.cockpit.model.UserConfig;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

/**
 * this is the main program of cockpit.
 * It contains a mainWindow ui instance,
 * an array of gauges.
 * and a timer
 * 
 * @author dongl
 * @param <VarLoopup>
 *
 */


public class CockPit<VarLoopup> extends QMainWindow {
    /**
     * This class is the cockpit main class. 
     * It controls the UI, 
     *    data connection, 
     *    and cent-tab-wdiget
     */


    /**
     * global defaults
     */
    static QImage gnodataImg= null;  //nodataImg-rip
    public static boolean gfixedSize = false;
    public static boolean isFixedSize() { return gfixedSize; }
    public static QColor gdefCColor=new QColor(255,255,0),  gdefHColor= new QColor(170,170,255);//purple //(212, 140, 95)-lightbrown;
    public static QColor gdefBColor = new QColor(0,85,127), gdefBColor2 = new QColor(82,85,79), gdefBColor3 = new QColor(85,85,127), gdefBColor4 = new QColor(65,94,84); //new QColor(0,85,127);}
    public static final int gwdef=120, ghdef=80;
    public static int gtabcount = 0;
            
    /**
     * menu etc
     */
    QMenu _file, _tabsetup, _add, _gsetup, _config;
    public QStatusBar _statusbar; //String _statusbarMsg=""; int _statusbarTm=0;

    /**
     * map order to def-bg-color
     */
    public static HashMap<String, QColor> _orderToColor = new HashMap<String,QColor>();
    
    /**
     * User preferred config
     */
    //UserConfig 	       _cfXml =null;
    String                 _cfUser = null;
    
    /**
     * User input udp-data-server 
     */
    String 		       _servOpt =null; 

    /**
     * cent widget for all tabs
     */
    CentTabWidget _centWidget = null;

    /**
     * data-feeder status
     */
    CpConnection    _cpConn;

    /**
     * reconnection to data-feeder
     */
    // boolean _reconnection =false;

    /**
     * Data reduction period, in milliseconds. Points will
     * be plotted at this interval, typically 1000 msec for
     * 1 second data points.
     */
    int     _reductionPeriod = 1000;

    UIUtil _uU = new UIUtil();
    /**
     * CockPit constructor.
     */
    public  CockPit(String[] args) {
       
        if (args!=null && args.length>0)  parseArg(args);
        connectSlotsByName();
        setHidden(true);
        setGeometry(PageGeometry.x,  PageGeometry.y, PageGeometry.w, PageGeometry.h);  
        _orderToColor.put("gdefBColor", gdefBColor);
        _orderToColor.put("gdefBColor2", gdefBColor2);
        _orderToColor.put("gdefBColor3", gdefBColor3);
        _orderToColor.put("gdefBColor4", gdefBColor4);

        _centWidget = new CentTabWidget(this);
        setCentralWidget(_centWidget); 
        createUIs();
        connection();// && _cfUser != null && _centWidget != null) {
    }

    public void closeEvent(QCloseEvent  ce)  {
        disposed();
    }

    public void setServOpt(String servOpt) {_servOpt=servOpt;}
    public String getServOpt() { return _servOpt; }
    
    public CentTabWidget getCentWidget() {  return _centWidget; }

    public QStatusBar getStatusBar() { return _statusbar; }
    
      
    private void createUIs() {
        //menu bar
        _statusbar = new QStatusBar(this);
        setStatusBar(_statusbar);
        _statusbar.setObjectName("");
        _statusbar.showMessage("Start ...", 10000);

        //create menu items
        createMenuItems(); 
        show();
    }

    private void createMenuItems() {
        //menuBar
        QMenuBar menuBar = new QMenuBar(this);
        QPalette pl=new QPalette( QColor.lightGray);
        menuBar.setPalette(pl);

        menuBar.setObjectName("menuBar");
        menuBar.setGeometry(new QRect(0, 0, 1290, 25));
        setMenuBar(menuBar);

        //file and items
        _file =menuBar.addMenu(" &File");
        menuBar.setFont(new QFont("Ariel", 12));
        _file.addAction("&ConnectServer", this, "connection()");
        _file.addAction("&Exit", this, "exitApp()");

        //current-tab setup
        _tabsetup =menuBar.addMenu("&TabSetup");
        _tabsetup.addAction("ToFixedPlotSize", this, "toggleFixedGaugeSize()");
        _tabsetup.addAction("&AutoScale_Plots", _centWidget, "gautoScalePlots()");
        _tabsetup.addAction("&ManualScale_Plots", _centWidget, "rescaleGaugesInTab()");
        QMenu clr=_tabsetup.addMenu("Color");
        clr.addAction("Co&lor_Current", _centWidget, "colorCurrent()");
        clr.addAction("Color_&History", _centWidget, "colorHistory()");
        clr.addAction("Color_&BackGround", _centWidget, "colorBackGround()");
        clr.addAction("&CleanUp_History", _centWidget, "cleanupHistory()");
        QMenu sort=_tabsetup.addMenu("SortBy");
        sort.addAction("Variable", _centWidget, "sortVariable()");
        sort.addAction("Height", _centWidget, "sortHeight()");
        _tabsetup.addAction("DeletePage", _centWidget, "closeTab()");
        QMenu tmsetup=_tabsetup.addMenu("TimeSetup");
        tmsetup.addAction("&PlotTimeRange", _centWidget, "changeSinglePlotTimeMSec()");
        tmsetup.addAction("&NodataTimeout", _centWidget, "setSingleNodataTimeout()");
        _tabsetup.setEnabled(false);

        //add 
        _add=menuBar.addMenu("Add");
        _add.addAction("NewPageByVar", this, "addPageByVar()");
        _add.addAction("NewPageByHt", this, "addPageByHt()");
        //_add.addAction("SortPageByVariable", _centWidget, "addVariablePage()");
        //_add.addAction("SortPageByHeight", _centWidget, "addHeightPage()");
        _add.setEnabled(false);

        //global setup
        _gsetup =menuBar.addMenu("&GlobalSetup");
        _gsetup.addAction("ToFixedPlotSize", this, "gtoggleFixedGaugeSize()");
        _gsetup.addAction("&AutoScalePlots", _centWidget, "ggautoScalePlots()");
        _gsetup.addAction("AutoCycleTabs", _centWidget, "autoCycleTabs()");
        QMenu cr=_gsetup.addMenu("Color");
        cr.addAction("Co&lor_Current", _centWidget, "gcolorCurrent()");
        cr.addAction("Color_&History", _centWidget, "gcolorHistory()");
        cr.addAction("Color_&BackGround", _centWidget, "gcolorBackGround()");
        cr.addAction("&CleanUp_History", _centWidget, "gcleanupHistory()");
        QMenu srt=_gsetup.addMenu("SortBy");
        srt.addAction("Variable", _centWidget, "gsortVariable()");
        srt.addAction("Height", _centWidget, "gsortHeight()");
        QMenu tmset=_gsetup.addMenu("TimeSetup");
        tmset.addAction("&PlotTimeRange", _centWidget, "changePlotTimeMSec()");
        tmset.addAction("&NodataTimeout", _centWidget, "setNodataTimeout()");
        _gsetup.setEnabled(false);

        //_config and items
        _config =menuBar.addMenu("&Config");
        _config.addAction("&Save_Config", this, "saveUserConfig()");
        _config.addAction("&Open_Config", this, "openUserConfig()");
        _config.setEnabled(false);
    }


    public void exitApp() {
        disposed();        
    }

    public void disposed()
    {
        _cpConn.exitConnection();
        System.exit(0);
    }

    public void setUserConfig(String uConf){
        _cfUser = uConf;
    }
        
    public String getUserConfig(){
        return _cfUser;
    }
    
    /**
     * Change the plot-page status to resize or re-arrange the plots in the page
     */
    private void toggleFixedGaugeSize()
    {
        gfixedSize =!gfixedSize;
        syncSizePolicy(gfixedSize);
        Policy policy =Policy.Preferred;
        if (gfixedSize) policy =Policy.Fixed;
        _centWidget.toggleFixedGaugeSize(policy);
       
    }
    
    /*
     * Change the all tabs' status to resize the pages which contain plots
     */
    private void gtoggleFixedGaugeSize()
    {
        gfixedSize =!gfixedSize;
        syncSizePolicy(gfixedSize);
        Policy policy =Policy.Preferred;
        if (gfixedSize) policy =Policy.Fixed;
        _centWidget.gtoggleFixedGaugeSize(policy);
    }

    private void addPageByVar() {
        List<Var> vars=new ArrayList<Var>();
        int len = _centWidget._gaugePages.size();
        for (int i=0; i<len; i++) {
            List<Var> tmp = _centWidget._gaugePages.get(i).getVars();
            tmp = _uU.getSortedVars(tmp);
            for (int j= 0; j<tmp.size(); j++) {
                Var var = tmp.get(j);
                if (var!=null && !vars.contains(var)) vars.add(var);   
            }
        }
        
        new VarLookup(null,this, vars);
        // add new page will be doen in OK button     
    }
    
    private void addPageByHt() {
        List<Var> vars=new ArrayList<Var>();
        int len = _centWidget._gaugePages.size();
        for (int i=0; i<len; i++) {
            List<Var> tmp = _centWidget._gaugePages.get(i).getVars();
            for (int j= 0; j<tmp.size(); j++) {
                Var var = tmp.get(j);
                if (var!=null && !vars.contains(var)) vars.add(var);   
            }
        }
        
        new HtLookup(null,this, vars);
    }
    
    private void saveUserConfig() {
    	_centWidget.saveUserConfig();
    }

    private void openUserConfig() {
    	_centWidget.openUserConfig();
    }


    public void syncSizePolicy(boolean gfixed){
  
        gfixedSize=gfixed;
        if (gfixed){
            _tabsetup.actions().get(0).setText("ToVaryingPlotSize");
            _gsetup.actions().get(0).setText("ToVaryingPlotSize");
        } else {
            _tabsetup.actions().get(0).setText("ToFixedPlotSize");
            _gsetup.actions().get(0).setText("ToFixedPlotSize");
        }
    }

    /** 
     * A wrap to the connServ method, to handle UI cursor and status display during/after the searching...
     * @return
     */
    public boolean connection() {

        openImage();
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        _cpConn= new  CpConnection(this);
        boolean ret=_cpConn.connServ();
        if (ret){
            if (! _centWidget.isAnyPlot()) {
                _statusbar.showMessage("   No sensor data yet...", 10000);
            }
            _tabsetup.setEnabled(true);
            _add.setEnabled(true);
            _gsetup.setEnabled(true);
            _config.setEnabled(true);
            
            _file.actions().get(0).setEnabled(false);
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
        return ret;
    }

    public void reconnect() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        _cpConn.reconnect();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private  void openImage() {
        if (gnodataImg !=null) return;
        String nodatapath= "classpath:image/nodata.png";
        gnodataImg = new QImage(nodatapath);
        gnodataImg.setColor(1,QColor.red.rgb());
    }

    static class PageGeometry {
        static public int x= 350, y=250, w=1000, h=700;
    }

    
    /**
     * parse the parameters received from user's input
     * [-s server:port] [-debug true] [-c userConfig.xml] 
     * Note: -debug is for developers, this option is not documented in user's manual
     * Silently ignores unrecognized arguments.
     * @param args  -s serv:port -debug true/false serv:port
     * @return      void
     */
    private void parseArg( String[] args)
    {
        for (int i=0; i < args.length; i++)
        {
            
            if (args[i].equals("-s") && i+1 < args.length){
                setServOpt(args[++i]);
            }
            else if (args[i].equals("-debug") && i+1 < args.length && args[++i].equals("true")) {
                Util.setDebug(true);
            } else if (args[i].equals("-c") && i+1 < args.length ) {
                setUserConfig(args[++i]);
            }
            //args from jnlp
            if (args[i].equals("-arg") ||args[i].equals("-open") && i+1 < args.length){
                String op = args[++i];
                String[] trs = op.split(" ");
                if (trs.length!=2) {Util.prtErr("option is not a pair"+ op);}
                else parseArg(trs);
            }
        }
    }

    /**
     * Construct a cockpit mainframe, and only one mainframe
     * options: -s to pass data-feed-net-Inf example: -s   "porter.atd.ucar.edu:30000"
     * options: -debug to print debug information
     */
    public static void main(String[] args) {
        QApplication.initialize(args);
        //setNativeLookAndFeel();
        CockPit cockpit = new CockPit(args);
        QApplication.exec();

    }
    
} //eof-class


