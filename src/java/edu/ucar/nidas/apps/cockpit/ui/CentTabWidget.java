package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.QTimer;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.gui.QAction;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QColorDialog;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QInputDialog;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QMenu;
import com.trolltech.qt.gui.QMouseEvent;
import com.trolltech.qt.gui.QStackedLayout;
import com.trolltech.qt.gui.QTabWidget;
import com.trolltech.qt.gui.QSizePolicy.Policy;
import com.trolltech.qt.gui.QMessageBox;
import com.trolltech.qt.gui.QMessageBox.StandardButton;

import edu.ucar.nidas.apps.cockpit.model.CockpitConfig;
// import edu.ucar.nidas.apps.cockpit.model.UserConfig;
import edu.ucar.nidas.apps.cockpit.model.GaugePageConfig;
import edu.ucar.nidas.apps.cockpit.model.GaugeConfig;
import edu.ucar.nidas.model.Site;
import edu.ucar.nidas.model.Dsm;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.model.DataSource;
import edu.ucar.nidas.model.Log;
/**
 * This class is the center tab-widget which controls all the tab-gauge-pages
 * It tracks the current tabs and mediate between the cockpit main with the other gauge-pages
 * Encapsulate all cent-tab-widget code in the class to simplify the cockpit main program 
 * 
 * @author dongl
 *
 */
public class CentTabWidget extends QTabWidget {

    /**
     * Reference to Cockpit.
     */
    private Cockpit _cockpit = null;

    private Log _log;

    /**
     * GaugePages by name
     */
    private HashMap<String, GaugePage> _gaugePageByName =
        new HashMap<String,GaugePage>();

    private GaugePage _currentPage = null;

    /**
     * Layout manager.
     */
    // private QStackedLayout _stacked= new QStackedLayout();

    /**
     * Name of widget, typically set to the project.
     * Saved in configuration. Not sure whether is it used otherwise.
     */
    private String _name;

    /**
     * auto-cycle tab-pages, default is 10 seconds
     */
    private QTimer _cycleTm;

    private int _cycleInt = 10;

    private QRect _pageGeometry = new QRect(350, 250, 1000, 700);

    /***********************************************/
    public CentTabWidget(Cockpit p)
    {
        super(p);
        _cockpit = p;
        _log = _cockpit.getLog();
        connectSlotsByName();
        // setLayout(_stacked);
        currentChanged.connect(this, "pageChanged()");  
        _cycleTm = new QTimer();
        _cycleTm.timeout.connect(this, "cycleTimeout()");
    }

    public Log getLog()
    {
        return _log;
    }

    /**
     * Create a GaugePage for every dsm.
     */
    public void addGaugePages(ArrayList<Site> sites)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        for (Site site : sites) {

            ArrayList<Dsm> dsms = site.getDsms();

            for (Dsm dsm : dsms) {
                GaugePage gp = _gaugePageByName.get(dsm.getName());
                if (gp == null) {
                    gp = new GaugePage(this, dsm.getName());
                    _gaugePageByName.put(dsm.getName(), gp);
                    int n = _gaugePageByName.size() % Cockpit.defBGColors.size();
                    gp.setBGColor(Cockpit.defBGColors.get(n));
                    gp.setGeometry(_pageGeometry);
                    addTab(gp, gp.getName());
                }
                gp.createGauges(dsm);
            }
        }

        setCurrentIndex(0);
        update();

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    /**
     * Create a GaugePage for a list of Var.
     */
    public void addGaugePage(List<Var> vars, String name)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        GaugePage gp = new GaugePage(this, name);

        int n = _gaugePageByName.size() % Cockpit.defBGColors.size();
        gp.setBGColor(Cockpit.defBGColors.get(n));
        gp.setGeometry(_pageGeometry);
        gp.createGauges(vars);
        _gaugePageByName.put(name, gp);

        addTab(gp, gp.getName());
        setCurrentWidget(gp);
        update();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    public void remove(GaugePage gp)
    {
        _gaugePageByName.remove(gp.getName());

        int cidx = currentIndex();
        int idx = indexOf(gp);
        if (idx >= 0) removeTab(idx);
        if (idx <= cidx) cidx = cidx - 1;
        setCurrentIndex(cidx);
    }

    public void closeCurrentTab()
    {
        remove(getCurrentGaugePage());
    }

    public GaugePage getCurrentGaugePage()
    {
        return (GaugePage)currentWidget();
    }


    public GaugePage getGaugePage(String name) 
    {
        return _gaugePageByName.get(name);
    }

    public Gauge getGauge(String dsmname, String varname) 
    {
        GaugePage gp = getGaugePage(dsmname);
        if (gp != null)
            return gp.getGauge(varname);
        return null;
    }

    public Set<Gauge> getGauges(String name)
    {
        Set<Gauge> gauges = new HashSet<Gauge>();
        synchronized(_gaugePageByName) {
            for (GaugePage gp : _gaugePageByName.values()) {
                Gauge g = gp.getGauge(name);
                if (g != null) gauges.add(g);
            }
        }
        return gauges;
    }

    void status(String msg, int tm)
    {
        _cockpit.status(msg, tm);
    }

    public void setName(String name) { _name = name; }

    public String getName() { return _name; }
    
    public Cockpit getParent() {
        return _cockpit;
    }

    public Collection<GaugePage> getGaugePages()
    {
        return _gaugePageByName.values();
    }

    /**
     * Change all pages' policy to resize 
     */
    public void freezePlotSizes()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.freezePlotSizes();
        }
    }

    /**
     * Change all pages' policy to resize 
     */
    public void unfreezePlotSizes()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.unfreezePlotSizes();
        }
    }

    /**
     * Change all pages' policy to resize 
     */
    public void freezeGrids()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.freezeGrid();
        }
    }

    /**
     * Change all pages' policy to resize 
     */
    public void unfreezeGrids()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.unfreezeGrid();
        }
    }

    /**
     * Clean up history for all plots in all pages
     */
    public void clearHistory()
    {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Clear History") ==
            StandardButton.Abort.value()) return;
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.clearHistory();
        }
    }

    /**
     * Scale each plot in the active page based on its max-min in the span 
     */
    public void gautoScalePlots()
    {
        getCurrentGaugePage().gautoScalePlots(true);
    }

    /**
     * Scale each plot in all page based on its max-min in the span 
     */
    public void ggautoScalePlots() {
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.gautoScalePlots(true);
        }
        GaugePage gp = (GaugePage)currentWidget();
        setCurrentWidget(gp);
    }

    /**
     * Color each plot in the active page with new color  
     */
    public void colorCurrent() {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getTraceColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorCurrent(c);
    }
    /**
     * Color each plot in all pages with new color  
     */
    public void gcolorCurrent() {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getTraceColor());
        if (c.value()==0) return;
        for (GaugePage gp : _gaugePageByName.values()) gp.colorCurrent(c);
    }

    /**
     * Color the history image of each plot in the active page with new color  
     */
    public void colorHistory() {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Color History") ==
            StandardButton.Abort.value()) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getHistoryColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorHistory(c);
    }

    /**
     * Color the history image of each plot in the active page with new color  
     */
    public void gcolorHistory() {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Color History") ==
            StandardButton.Abort.value()) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getHistoryColor());
        if (c.value()==0) return;
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.colorHistory(c);
        }
    }

    /**
     * Color the back-ground of each plot in the active page with new color  
     */
    public void colorBackGround() {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Color Background") ==
            StandardButton.Abort.value()) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getBGColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorBackGround(c); 
    }

    /**
     * Color the back-ground of each plot in all pages with new color  
     */
    public void gcolorBackGround() {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Color Background") ==
            StandardButton.Abort.value()) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getBGColor());
        if (c.value()==0) return;
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.colorBackGround(c); 
        }
    }

    /**
     * change the gauge-time-span-x_axis for every gauge page and its plots 
     * set the time-range in milli-second in x_axis
     */
    public void changePlotWidthMsec()
    {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Change time span") ==
            StandardButton.Abort.value()) return;

        int oldw = getCurrentGaugePage().getPlotWidthMsec();
        int neww = QInputDialog.getInt(this,"Plot Width",
                "Width of plot (seconds)",
                oldw / 1000,60,3600,60) * 1000;

        for (GaugePage gp : _gaugePageByName.values()) {
            gp.setPlotWidthMsec(neww);
        }
    }

    /**
     * change the gauge-time-span-x_axis for the current gauge page and its plots 
     * set the time-range in milli-second in x_axis
     */
    public void changeSinglePlotWidthMsec()
    {
        if (_cockpit.confirmMessageBox(
            "All plot history will be lost", "Change time span") ==
            StandardButton.Abort.value()) return;

        int oldw = getCurrentGaugePage().getPlotWidthMsec();
        int neww = QInputDialog.getInt(this,"Plot Width",
                "Width of plot (seconds)",
                oldw / 1000,60,3600,60) * 1000;
        getCurrentGaugePage().setPlotWidthMsec(neww);
    }

    public void setDataTimeout()
    {
        int timeout = QInputDialog.getInt(this,"Data Timeout",
                "Seconds",600,0,3600);

        for (GaugePage gp : _gaugePageByName.values()) {
            gp.setDataTimeout(timeout);
        }
    }

    public void setSingleDataTimeout()
    {
        int oldtm = getCurrentGaugePage().getDataTimeout();
        Integer timeout = QInputDialog.getInt(this,"Data Timeout",
                "Seconds",600,0,3600);
        if (timeout <= 0 || oldtm == timeout) return;
        getCurrentGaugePage().setDataTimeout(timeout);
    }

    public void pageChanged()
    {
        if (_currentPage != null && !_currentPage.frozenPlotSizes()) {
            _currentPage.freezePlotSizes();
        }
        _currentPage = getCurrentGaugePage();
    }


    /*
    public boolean isAnyPlot()
    {
        if ( _gaugePages==null || _gaugePages.get(0)==null || _gaugePages.get(0)._gauges.size()<1) return false;
        else return true;
    }
    */

    public void mouseReleaseEvent(QMouseEvent event)
    {
        if (event.button()==MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu("");
            QMenu option = pMenu.addMenu("RenamePage");
            option.addAction("&RenamePage", this, "renamePage()");
            option.popup(event.globalPos());
        }
    }

    public void rescaleGaugesInTab() {

        Gauge g = getCurrentGaugePage()._gauges.get(0);

        RescaleDialog rd = new RescaleDialog( g._ymax, g._ymin, geometry().width()/3, geometry().height()/3) ;
        if (!rd.getOk())  return;
        if (rd.getMax() <=rd.getMin()) {
            _cockpit.logError("Y-axis-Max is smaller than Y-axis-Min");
            return;
        }

        List<Gauge> gs = getCurrentGaugePage()._gauges;
        for (int i = 0; i < gs.size(); i++) 
            gs.get(i).changeYMaxMin(rd.getMax(), rd.getMin()); 
    }


    /**
     * auto cycle tabs based on users' chosen time-interval 
     */
    private void autoCycleTabs(){
        synchronized(this) {
            QAction at = _cockpit.gsetup.actions().get(2);
            String tx = at.text();
            if (tx.equals("AutoCycleTabs")) {
                at.setText("StopCycleTabs");
                _cycleInt = QInputDialog.getInt(this,"Tab Cycle Time",
                        "Seconds",_cycleInt,0,3600);
                _cycleTm.start(_cycleInt * 1000); 
                status("Cycle tabs every " + _cycleInt + " seconds", -1);
            } else {
                at.setText("AutoCycleTabs");
                _cycleTm.stop();
                status("Stop cycle tabs", 10000);
            }
        }
    }


    private void renamePage()
    {
        String text = QInputDialog.getText(this,
                "Get Page Name", "Enter a new name:", QLineEdit.EchoMode.Normal,"");
        if ( text!=null  ) {
            setTabText(currentIndex(),text);
        } 
    }

    /**
     * timer to set current page, and auto-scale. Only one time when the program starts. 
     */
    /*
    private void timeout() {
        _tm.stop();
        GaugePage gp=(GaugePage)currentWidget();
        for (int i = 0; i<_gaugePages.size(); i++ ) {
            GaugePage p = _gaugePages.get(i);
            setCurrentWidget(p);
        }
        setCurrentWidget(gp);
       
    }
    */

    /**
     * timer to set user config, and set current, only onetime when the program starts 
     */
    /*
    private void ucfTimeout() {
        _ucftm.stop();
        if (_cockpit.getUserConfig()!=null) {
            openUserConfig(_cockpit.getUserConfig());
        }    
        GaugePage gp=(GaugePage)currentWidget();
        for (int i = 0; i < _gaugePages.size(); i++ ) {
            GaugePage p = _gaugePages.get(i);
            setCurrentWidget(p);
        }
        setCurrentWidget(gp);
    }
    */
   
    private void cycleTimeout(){

        if (_cycleInt <= 0) {
            _cycleTm.stop();
        } else {
            if (count() > 0) setCurrentIndex((currentIndex() + 1) % count());
        }
    }

    public void apply(CockpitConfig conf)
    {
        synchronized (this){
            setWindowTitle(conf.getName());
            // System.out.println("conf-apply "+conf.getGaugePageConfig().size()+ "   "+conf.getGaugePageConfig().get(0).getName());
            List<GaugePageConfig> tps = conf.getGaugePageConfig();
            for (int i = 0; i<tps.size(); i++){
                GaugePageConfig tp = tps.get(i);
                String pname = tp.getName();
                GaugePage gp = getGaugePage(tp.getName());

                if (gp == null) {
                    gp = new GaugePage(this, pname);
                    _gaugePageByName.put(pname, gp);
                }

                List<GaugeConfig> gcs = tp.getGaugeConfigs();
                for (GaugeConfig gc : gcs) {
                    String vname = gc.getName();
                    Var var = _cockpit.getVar(vname);
                    if (var != null) {
                        Gauge g = gp.addGauge(var);
                        if ((g.getYMax() != gc.getMax()) || (g.getYMin() != gc.getMin())) {
                            g.changeYMaxMin(gc.getMax(),gc.getMin());
                        }
                        if (!g.getTraceColor().equals(new QColor(gc.getTraceColor()))) {
                            g._noDataPaint = false; g.setTraceColor(new QColor(gc.getTraceColor()));
                        }
                        if (!g.getHistoryColor().equals(new QColor(gc.getHistoryColor()))) {
                            g._noDataPaint = false; g.setHistoryColor(new QColor(gc.getHistoryColor()));
                        }
                        if (!g.getBGColor().equals(new QColor(gc.getBGColor()))) {
                            g._noDataPaint = false; g.setBGColor(new QColor(gc.getBGColor()));
                        }
                        if (gc.getDataTimeout() != g.getDataTimeout()) g.setDataTimeout(gc.getDataTimeout());
                        if (gc.getPlotWidthMsec() != g.getWidthMsec()) g.setWidthMsec(gc.getPlotWidthMsec());
                        DataSource ds = _cockpit.getDataSource(var);
                        ds.addClient(g);
                    }
                }

                gp.setWindowTitle(tp.getName());
                gp.resize(tp.getSize()[0], tp.getSize()[1]);
            }
        }
    }

} //eof-cent-tab-widget class
