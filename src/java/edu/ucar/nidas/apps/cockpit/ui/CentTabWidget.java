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

    // private GaugePage _currentPage = null;

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
        // currentChanged.connect(this, "pageChanged()");  
        _cycleTm = new QTimer();
        _cycleTm.timeout.connect(this, "cycleTimeout()");
    }

    public Log getLog()
    {
        return _log;
    }

    /**
     * Create a GaugePage for every dsm in the array of sites.
     */
    public void connect(ArrayList<Site> sites)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        HashMap<String, GaugePage> nonDSMGauges =
            new HashMap<String,GaugePage>(_gaugePageByName);

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
                nonDSMGauges.remove(dsm.getName());
            }
        }

        setCurrentIndex(0);
        update();

        /* Reconnect the gauges on GaugePages that are not associated with
         * a specific DSM, i.e. added by the user.
         */
        for (GaugePage gp: nonDSMGauges.values()) {
            gp.connectGauges();
        }

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    /**
     * Create a GaugePage for a list of Var.
     */
    public void addGaugePage(String name, List<Var> vars)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        GaugePage gp = new GaugePage(this, name);
        _gaugePageByName.put(name, gp);

        int n = _gaugePageByName.size() % Cockpit.defBGColors.size();
        gp.setBGColor(Cockpit.defBGColors.get(n));
        gp.setGeometry(_pageGeometry);
        addTab(gp, gp.getName());

        // System.err.printf("creating gauges for %d variables\n", vars.size());
        gp.createGauges(vars);

        setCurrentWidget(gp);
        update();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    /*
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
    */

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
    
    public Cockpit getCockpit()
    {
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

    public void plotSizeStateChange()
    {
        int nfrozen = 0;
        for (GaugePage gpp : _gaugePageByName.values()) {
            if (gpp.frozenPlotSizes()) nfrozen++;
        }
        if (nfrozen == _gaugePageByName.size()) {
            _cockpit.disableFreezePlotSizeMenu();
        }
        else {
            _cockpit.enableFreezePlotSizeMenu();
        }
        if (nfrozen == 0) {
            _cockpit.disableUnfreezePlotSizeMenu();
        }
        else {
            _cockpit.enableUnfreezePlotSizeMenu();
        }
    }

    public void gridStateChange()
    {
        int nfrozen = 0;
        for (GaugePage gpp : _gaugePageByName.values()) {
            if (gpp.frozenGridLayout()) nfrozen++;
        }
        if (nfrozen == _gaugePageByName.size()) {
            _cockpit.disableFreezeGridLayoutMenu();
        }
        else {
            _cockpit.enableFreezeGridLayoutMenu();
        }
        if (nfrozen == 0) {
            _cockpit.disableUnfreezeGridLayoutMenu();
        }
        else {
            _cockpit.enableUnfreezeGridLayoutMenu();
        }
    }

    /**
     * Change all pages' policy to resize 
     */
    public void freezeGridLayouts()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.freezeGridLayout();
        }
    }

    /**
     * Change all pages' policy to resize 
     */
    public void unfreezeGridLayouts()
    {
        for (GaugePage gpp : _gaugePageByName.values()) {
            gpp.unfreezeGridLayout();
        }
    }

    /**
     * Clean up history for all plots on current page.
     */
    public void pageClearHistory()
    {
        getCurrentGaugePage().clearHistory();
    }

    /**
     * Clean up history for all plots in all pages
     */
    public void globalClearHistory()
    {
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.clearHistory();
        }
    }

    /**
     * Scale each plot in the active page based on its max-min in the span 
     */
    public void pageAutoScalePlots()
    {
        getCurrentGaugePage().autoScalePlots();
    }

    /**
     * Scale each plot in all page based on its max-min in the span 
     */
    public void globalAutoScalePlots()
    {
        for (GaugePage gp : _gaugePageByName.values()) {
            gp.autoScalePlots();
        }
    }

    /**
     * Color each plot in all pages with new color  
     */
    public void pageTraceColor()
    {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getTraceColor());
        getCurrentGaugePage().traceColor(c);
    }

    /**
     * Color the history image of each plot in the active page with new color  
     */
    public void pageHistoryColor()
    {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getHistoryColor());
        getCurrentGaugePage().historyColor(c);
    }

    /**
     * Color the back-ground of each plot in all pages with new color  
     */
    public void pageBackgroundColor()
    {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getBGColor());
        getCurrentGaugePage().backgroundColor(c);
    }

    /**
     * Change the time width of all plots on all pages.
     */
    public void changeAllPlotTimeWidth()
    {
        int oldw = getCurrentGaugePage().getPlotWidthMsec();
        int neww = QInputDialog.getInt(this,tr("Plot Width"),
                tr("Width of plot (seconds)"),
                oldw / 1000,60,3600,60) * 1000;

        for (GaugePage gp : _gaugePageByName.values()) {
            gp.setPlotWidthMsec(neww);
        }
    }

    /**
     * Change the time width of plots on current page.
     */
    public void changePagePlotTimeWidth()
    {
        int oldw = getCurrentGaugePage().getPlotWidthMsec();
        int neww = QInputDialog.getInt(this,tr("Plot Width"),
                tr("Width of plot (seconds)"),
                oldw / 1000,60,3600,60) * 1000;
        getCurrentGaugePage().setPlotWidthMsec(neww);
    }

    public void setDataTimeout()
    {
        int timeout = QInputDialog.getInt(this,tr("Data Timeout"),
                tr("Seconds"),600,0,3600);

        for (GaugePage gp : _gaugePageByName.values()) {
            gp.setDataTimeout(timeout);
        }
    }

    public void setSingleDataTimeout()
    {
        int oldtm = getCurrentGaugePage().getDataTimeout();
        Integer timeout = QInputDialog.getInt(this,tr("Data Timeout"),
                tr("Seconds"),600,0,3600);
        if (timeout <= 0 || oldtm == timeout) return;
        getCurrentGaugePage().setDataTimeout(timeout);
    }

    /*
    public void pageChanged()
    {
        if (_currentPage != null && !_currentPage.frozenPlotSizes()) {
            _currentPage.freezePlotSizes();
        }
        _currentPage = getCurrentGaugePage();
    }
    */

    /*
    public boolean isAnyPlot()
    {
        if ( _gaugePages==null || _gaugePages.get(0)==null || _gaugePages.get(0)._gauges.size()<1) return false;
        else return true;
    }
    */

    public void mouseReleaseEvent(QMouseEvent event)
    {
        if (event.button() == MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu(this);
            pMenu.addAction(tr("&Rename Page"), this, "renameCurrentPage()");
            pMenu.addAction(tr("&Delete Page"), this, "deleteCurrentPage()");
            pMenu.popup(event.globalPos());
        }
    }

    /**
     * auto cycle tabs based on users' chosen time-interval 
     */
    private void autoCycleTabs(){
        synchronized(this) {
            QAction at = _cockpit.autoCycleTabsAction;
            String tx = at.text();
            if (tx.equals(tr("Auto Cycle &Tabs"))) {
                at.setText(tr("Stop Cycle Tabs"));
                _cycleInt = QInputDialog.getInt(this,tr("Tab Cycle Time"),
                        tr("Seconds"),_cycleInt,0,3600);
                _cycleTm.start(_cycleInt * 1000); 
                status(tr("Cycle tabs every ") + _cycleInt + tr(" seconds"), -1);
            } else {
                at.setText(tr("Auto Cycle &Tabs"));
                _cycleTm.stop();
                status(tr("Stop cycle tabs"), 10000);
            }
        }
    }

    public void renameCurrentPage()
    {
        String name = QInputDialog.getText(this,
            tr("Get Page Name"), tr("Enter a new name:"),
            QLineEdit.EchoMode.Normal,"");
        if (name != null) {
            GaugePage gp = (GaugePage)currentWidget();
            _gaugePageByName.remove(gp.getName());
            gp.setName(name);
            _gaugePageByName.put(name, gp);
            setTabText(currentIndex(), name);
        } 
    }

    public void deleteCurrentPage()
    {
        GaugePage gp = (GaugePage)currentWidget();
        _gaugePageByName.remove(gp.getName());
        gp.close();
        removeTab(currentIndex());
    }

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
            for (GaugePageConfig gpc : conf.getGaugePageConfig()) {

                String pname = gpc.getName();
                GaugePage gp = getGaugePage(gpc.getName());

                if (gp == null) {
                    gp = new GaugePage(this, pname);
                    _gaugePageByName.put(pname, gp);
                    addTab(gp, gp.getName());
                }

                List<GaugeConfig> gcs = gpc.getGaugeConfigs();
                for (GaugeConfig gc : gcs) {
                    String vname = gc.getName();
                    Var var = _cockpit.getVar(vname);
                    if (var != null) {
                        Gauge g = gp.addGauge(var, gc.getTraceColor(),
                                gc.getHistoryColor(), gc.getBGColor());

                        g.changeYMaxMin(gc.getMax(),gc.getMin());
                        g.setDataTimeout(gc.getDataTimeout());
                        g.setWidthMsec(gc.getPlotWidthMsec());

                    }
                }
                gp.connectGauges();

                gp.setWindowTitle(gpc.getName());
                gp.resize(gpc.getSize());
            }
        }
    }

} //eof-cent-tab-widget class
