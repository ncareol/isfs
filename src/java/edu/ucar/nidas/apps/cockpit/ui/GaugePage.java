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

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.QTimer;
import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QAction;
import com.trolltech.qt.gui.QFocusEvent;
import com.trolltech.qt.gui.QGridLayout;
import com.trolltech.qt.gui.QInputDialog;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QMenu;
import com.trolltech.qt.gui.QMouseEvent;
import com.trolltech.qt.gui.QResizeEvent;
import com.trolltech.qt.gui.QScrollArea;
import com.trolltech.qt.gui.QSizePolicy;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QSizePolicy.Policy;

import edu.ucar.nidas.apps.cockpit.model.MinMaxer;
import edu.ucar.nidas.model.Dsm;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.model.Log;
import edu.ucar.nidas.model.DataSource;

/**
 * A page of Cockpit Gauges.
 * 
 */
public class GaugePage extends QWidget {
    /**
     * Plot-widget: 
     * a widget for plots
     * a list of gauges to show instruments' data
     * and a timer to control update frequency 
     */

    String _name;

    /**
     * Gauges on this page.
     */
    List<Gauge> _gauges = new ArrayList<Gauge>();

    private boolean _frozenPlotSizes = false;

    private boolean _frozenGrid = false;

    /**
     * QSizePolicy of Gauges.
     */
    private QSizePolicy _fixedPolicy;

    private QSizePolicy _varyingPolicy;

    private QSize _gaugeSize = null;

    private QGridLayout _gaugeLayout;

    private QScrollArea _scrollArea;

    /**
     * Number of columns in layout of Gauges.
     */
    int _ncols;

    QColor _traceColor = Cockpit.defTraceColor;

    QColor _historyColor = Cockpit.defHistoryColor;

    QColor _bgColor = Cockpit.defBGColors.get(0);

    /**
     * Data reduction period, in milliseconds. Points will
     * be plotted at this interval, typically 1000 msec for
     * 1 second data points.
     */
    int _statisticsPeriod = 1000;

    /**
     *  plot-width-in-time-msec, Default width of Gauge plots, in milliseconds.
     */
    int _gaugeWidthMsec = 120 * 1000;

    /**
     * plot-nodata-timeout,default is 10 minutes
     */
    int _dataTimeout = 600; 

    HashMap<String, Gauge> _gaugesByName = new HashMap<String,Gauge>();

    CentTabWidget _centTabWidget = null;

    private Log _log;

    public class PlotArea extends QWidget
    {
        public void mouseReleaseEvent(QMouseEvent event)
        {
            if (event.button() == MouseButton.RightButton)
            {
                QMenu menu = new QMenu("Page options");

                if (frozenPlotSizes()) {
                    menu.addAction("Unfreeze &Plot Sizes",
                            GaugePage.this, "unfreezePlotSizes()");
                }
                else {
                    menu.addAction("Freeze &Plot Sizes",
                            GaugePage.this, "freezePlotSizes()");
                }

                if (frozenGridLayout()) {
                    menu.addAction("Unfreeze &Grid Layout",
                            GaugePage.this, "unfreezeGridLayout()");
                }
                else {
                    menu.addAction("Freeze &Grid Layout",
                            GaugePage.this, "freezeGridLayout()");
                }
                menu.popup(event.globalPos());
            }
        }
    }

    /**
     *  constructor.
     */	
    public GaugePage(CentTabWidget p, String name)
    {
        super(p);
        _log = p.getLog();
        _centTabWidget = p;
        _name = name;

        QWidget plotarea = new PlotArea();
        _gaugeLayout = new QGridLayout();
        plotarea.setLayout(_gaugeLayout);

        _scrollArea = new QScrollArea(this);
        _scrollArea.setWidget(plotarea);
        _scrollArea.setWidgetResizable(true);
        _scrollArea.adjustSize();
        _scrollArea.setHorizontalScrollBarPolicy(
                Qt.ScrollBarPolicy.ScrollBarAlwaysOff);

	// create layout for this page of plots
        QVBoxLayout verticalLayout = new QVBoxLayout(this);
        verticalLayout.addWidget(_scrollArea);
        setLayout(verticalLayout);

        _gaugeSize = new QSize(Cockpit.gwdef, Cockpit.ghdef);

        _fixedPolicy = new QSizePolicy(
            Policy.Fixed,Policy.Fixed);
        // _fixedPolicy.setHorizontalStretch((byte)0);
        // _fixedPolicy.setVerticalStretch((byte)0);
        // _fixedPolicy.setHeightForWidth(true);

        _varyingPolicy = new QSizePolicy(
            Policy.Expanding,Policy.Expanding);
        // _varyingPolicy.setHorizontalStretch((byte)0);
        // _varyingPolicy.setVerticalStretch((byte)0);
        _varyingPolicy.setHeightForWidth(true);

        // _ncols = 
        // _ncols = calcGaugeColumns();
        _ncols = 7;
        // System.out.printf("initial ncols=%d\n",_ncols);
    }

    public Log getLog()
    {
        return _log;
    }

    public boolean frozenPlotSizes()
    {
        return _frozenPlotSizes;
    }

    public boolean frozenGridLayout()
    {
        return _frozenGrid;
    }

    /**
     * Create a Gauge.
     * @param Var -- the variable.
     */
    public Gauge addGauge(Var var, QColor tc, QColor hc, QColor bg)
    {
        synchronized(this) {
            int ng = _gauges.size();
            Gauge g = _gaugesByName.get(var.getNameWithStn());
            if (g == null) {
                g = new Gauge(this, _gaugeSize, _gaugeWidthMsec, var,
                        tc, hc, bg);

                if (_frozenPlotSizes) g.setSizePolicy(_fixedPolicy);
                else g.setSizePolicy(_varyingPolicy);

                _gauges.add(g);
                String pname = g.getName();
                _gaugesByName.put(pname, g);

                int row = ng / _ncols;
                int col = ng % _ncols;
                /*
                System.out.printf("adding Gauge=%s, row=%d,col=%d\n",
                        g.getName(),row,col);
                */
                _gaugeLayout.addWidget(g,row,col);
            }
            return g;
        }
    } 

    /**
     * Connect the Gauges to their DataSources.
     */
    public void connectGauges()
    {
        for (Gauge gauge: _gauges) {
            DataSource ds = _centTabWidget.getCockpit().getDataSource(gauge.getName());
            if (ds != null)
                ds.addClient(gauge.getDataClient());
        }
    }

    public QSize getGaugeSize()
    {
        /*
        System.out.printf("gauges size=%d\n",
                _gauges.size());
        System.out.printf("gauge QSize=%d x %d\n",
                _gaugeSize.width(), _gaugeSize.height());
        */
        QSize size = new QSize();
        if (!_gauges.isEmpty())
            size = _gauges.get(0).size();
        /*
        System.out.printf("gauge QSize=%d x %d, valid=%b\n",
                size.width(), size.height(),
                size.isValid());
        */
        return size;
    }


    public int getNumColumns()
    {
        return _ncols;
    }

    public List<Gauge> getGauges()
    {
        return _gauges;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String val)
    {
        _name = val;
    }

    public QColor getTraceColor()
    {
        return _traceColor;
    }

    public QColor getHistoryColor()
    {
        return _historyColor;
    }

    public QColor getBGColor()
    {
        return _bgColor;
    }
    public void setBGColor(QColor bgdc)
    {
        _bgColor = bgdc;
    }

    void status(String msg, int tm)
    {
        _centTabWidget.status(msg, tm);
    }

    public Gauge getGauge(String name) 
    {
        return _gaugesByName.get(name);
    }

    /**
     * set the new time-width-in-milli-seconds
     * @param msec
     */
    public void setPlotWidthMsec(int msec) {
        if (msec != _gaugeWidthMsec)
        {
            _gaugeWidthMsec = msec;
            for (Gauge gauge : _gauges) {
                gauge.setWidthMsec(msec);
            }
        }
    }

    /**
     * get the time-width-in-milli-seconds
     * @return
     */
    public int getPlotWidthMsec()
    {
        return _gaugeWidthMsec;
    }

    /**
     * get data reduction period
     */
    public int getStatisticsPeriod()
    {
        return _statisticsPeriod;
    }

    /**
     * Set the data timeout in seconds.
     * @param msec
     */
    public void setDataTimeout(int sec) {
        if (sec != _dataTimeout)
        {
            _dataTimeout = sec;
            for (Gauge gauge : _gauges) {
                gauge.setDataTimeout(sec);
            }
        }
    }

    /**
     * get the time-width-in-milli-seconds
     * @return
     */
    public int getDataTimeout()
    {
        return _dataTimeout;
    }

    /**
     * Freeze the layout of the Gauges.
    public void fixPlotSizes(int ncols, QSize gaugeSize)
    {
        _frozenPlotSizes = true;
        _gaugeSize = gaugeSize;
        setMinSizeOfGauges(_gaugeSize);
        setSizePolicyOfGauges(_fixedPolicy);
        if (ncols != _ncols) {
            _ncols = ncols;
            redoLayout();
        }
        _ncols = ncols;
        update(); 
    }
     */

    /**
     * Freeze the size of the Gauges.
     */
    public void freezePlotSizes()
    {
        _frozenPlotSizes = true;
        QSize gsize = getGaugeSize();
        /*
        System.err.printf("fixPlotSizes, before policy change, gsize valid=%b, size=%dx%d\n",
            gsize.isValid(), gsize.width(), gsize.height());
        */

        if (gsize.isValid()) {
            _gaugeSize = gsize;
            setMinSizeOfGauges(_gaugeSize);
        }
        setSizePolicyOfGauges(_fixedPolicy);
        if (!_frozenGrid) {
            int ncols = calcGaugeColumns();
            if (ncols != _ncols) {
                _ncols = ncols;
                redoLayout();
            }
            _ncols = ncols;
        }
        _centTabWidget.plotSizeStateChange();
    }

    public void unfreezePlotSizes()
    {
        _frozenPlotSizes = false;
        setSizePolicyOfGauges(_varyingPolicy);
        // QSize gsize = new QSize(Cockpit.gwdef, Cockpit.ghdef);
        QSize gsize = new QSize(0,0);
        setMinSizeOfGauges(gsize);
        if (!_frozenGrid) {
            int ncols = calcGaugeColumns();
            if (ncols != _ncols) {
                _ncols = ncols;
                redoLayout();
            }
            _ncols = ncols;
        }
        _centTabWidget.plotSizeStateChange();
    }

    /**
     * Freeze the grid.
     */
    public void freezeGridLayout()
    {
        _frozenGrid = true;
        _centTabWidget.gridStateChange();
    }

    /**
     * Freeze the grid.
     */
    public void unfreezeGridLayout()
    {
        _frozenGrid = false;
        int ncols = calcGaugeColumns();
        if (ncols != _ncols) {
            _ncols = ncols;
            redoLayout();
        }
        _ncols = ncols;
        _centTabWidget.gridStateChange();
    }

    public void setSizePolicyOfGauges(QSizePolicy val)
    {
        synchronized (_gauges){
            for (Gauge gauge : _gauges) {
                gauge.setSizePolicy(val);
                gauge.updateGeometry();
            }
        }
    }

    public void setMinSizeOfGauges(QSize size)
    {
        synchronized (_gauges){
            for (Gauge gauge : _gauges) {
                // gauge.resize(size);
                gauge.setMinimumSize(size);
                // gauge.updateGeometry();
            }
        }
    }

    public int calcGaugeColumns()
    {
        // TODO: account for margins
        int w = width();
        int ncols = _ncols;
        QSize gsize = getGaugeSize();
        
        if (!gsize.isValid()) {
            /*
            System.err.printf("calcGaugeColumns, frozenPlotSizes=%b, w=%d, #gauges=%d, invalid gauge size, ncols=%d\n",
                    _frozenPlotSizes, w, _gauges.size(), ncols);
            */
            return ncols;
        }

        if (_gauges.isEmpty()) {
            ncols = Math.max((int)((double)w / gsize.width()), 1);
            /*
            System.err.printf("calcGaugeColumns, frozenPlotSizes=%b, w=%d, #gauges=%d, ncols=%d\n",
                    _frozenPlotSizes, w, _gauges.size(), ncols);
            */
            return ncols;
        }

        if (_frozenPlotSizes) {
            ncols = Math.max((int)((double)w / gsize.width()), 1);
            /*
            System.err.printf("calcGaugeColumns, frozenPlotSizes, w=%d, gw=%d, gh=%d, ncols=%d\n",
                    w, gsize.width(), gsize.height(), ncols);
            */
        }
        else {
            int minarea = Cockpit.gwdef * Cockpit.ghdef;
            int h = height();

            double perarea = (double)(w * h) / _gauges.size();
            perarea = Math.max(perarea, (double)(minarea));

            double newW = Math.sqrt(perarea * 3 / 2);
            // _gw = (int)newW;
            // _gh = (int)(newW * 2 / 3);
            ncols = Math.max((int)Math.ceil((double)w / newW), 1);

            /*
            System.err.printf("calcGaugeColumns, !frozenPlotSizes, w=%d, h=%d, gw=%d, gh=%d, ncols=%d, perarea=%f, minarea=%d\n",
                    w, h, gsize.width(), gsize.height(), ncols, perarea, minarea);
            */
        }
        return ncols;
    }

    protected void redoLayout()
    {
        for (Gauge gauge : _gauges) {
            _gaugeLayout.removeWidget(gauge);
        }

        for (int i = 0; i < _gauges.size(); i++) {
            int row = i / _ncols;
            int col = i % _ncols;
            Gauge g = _gauges.get(i);
            _gaugeLayout.addWidget(g,row, col);
        }
        // update(); 
    } 

    /*
    public void focusInEvent( QFocusEvent arg)
    {
        if (!isActiveWindow()) return;
        //_centTabWidget .setMinimumSize(_pageSize);
    }
    */

    public void resizeEvent(QResizeEvent ent)
    {
        // if (!isActiveWindow()) return;
        // System.err.println("QResizeEvent active");

        if (ent.isAccepted()) {
            /*
            System.err.printf("QResizeEvent, #gauges=%d, size=%dx%d\n",
                    _gauges.size(), ent.size().width(), ent.size().height());
            */
            synchronized(this) {
                if (! _frozenGrid) {
                    _ncols = calcGaugeColumns();
                    // System.err.printf("QResizeEvent ncols=%d\n", _ncols);
                    redoLayout();
                }

            }
            QSize gsize = getGaugeSize();
            if (gsize.isValid()) _gaugeSize = gsize;
            /*
            System.err.printf("QResizeEvent, gsize=%dx%d\n",
                    gsize.width(), gsize.height());
            */
        }
    }

    public void remove(Gauge g)
    {
        synchronized (this) {

            DataSource ds = _centTabWidget.getCockpit().getDataSource(g.getName());
            if (ds != null) ds.removeClient(g.getDataClient());

            _gauges.remove(g); 
            _gaugesByName.put(g.getName(), null);
            _gaugeLayout.removeWidget(g);
            update();

            _ncols = calcGaugeColumns();
            redoLayout();
        }
    }

    public void clearHistory()
    {
        for (Gauge gauge : _gauges) {
            gauge.initPixmaps();
            gauge.changeTraceColor(_traceColor);
        }
    }

    /**
     * If the flag=false, it means (local-rescale) forced rescale. 
     * Otherwise, it will check to see yf is in the range.  
     * @param flag
     */
    public void autoScalePlots()
    {
        for (Gauge gauge : _gauges) {
            gauge.autoScalePlot(false);
        }
    }

    public void traceColor(QColor c)
    {
        _traceColor = c;// new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (Gauge gauge : _gauges) {
            gauge.changeTraceColor(_traceColor);
        }
    }


    public void historyColor(QColor c)
    {
        _historyColor = c;//new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (Gauge gauge : _gauges) {
            gauge.changeHistoryColor(_historyColor);
        }
    }

    public void backgroundColor(QColor c)
    {
        _bgColor = c;
        for (Gauge gauge : _gauges) {
            gauge.changeBGColor(_bgColor);
        }
    }

    public void createGauges(Dsm dsm)
    {
        ArrayList<Sample> samples = dsm.getSamples();
        for (Sample samp : samples) {
            ArrayList<Var> vars = samp.getVars();
            for (Var var : vars) {
                addGauge(var, getTraceColor(), getHistoryColor(),
                        getBGColor());
            }
        }
        update(); 
        connectGauges();
        // fixPlotSizes();
    }

    public void createGauges(List<Var> vars) 
    {
        for (Var var : vars) {
            addGauge(var, getTraceColor(), getHistoryColor(),
                    getBGColor());
        }
        update(); 
        connectGauges();
        // fixPlotSizes();
    }

    public void mouseReleaseEvent(QMouseEvent event)
    {
        if (event.button() == MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu(this);
            pMenu.addAction("&Rename Page", _centTabWidget,
                    "renameCurrentPage()");
            pMenu.popup(event.globalPos());
        }
    }

}


