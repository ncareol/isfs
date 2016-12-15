package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.QTimer;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QCursor;
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

/**
 * A page of Cockpit Gauges.
 * 
 * @author dongl
 *
 */
public class GaugePage extends QWidget {
    /**
     * Plot-widget: 
     * a widget for plots
     * a list of gauges to show instruments' data
     * and a timer to control update frequency 
     */

    /**
     * Gauges on this page.
     */
    List<Gauge> _gauges = new ArrayList<Gauge>();

    /**
     * QSizePolicy of Gauges.
     */
    private QSizePolicy _gaugePolicy = null;

    private QSize _gaugeSize = null;

    private QGridLayout _gaugelayout;

    private QScrollArea _scrollArea;

    /**
     * Number of columns in layout of Gauges.
     */
    int _ncols;

    QColor _currentColor = Cockpit.gdefCColor;
    QColor _historyColor = Cockpit.gdefHColor;
    QColor _bgColor = Cockpit.gdefBColor;

    /**
     * Data reduction period, in milliseconds. Points will
     * be plotted at this interval, typically 1000 msec for
     * 1 second data points.
     */
    int _reductionPeriod = 1000;

    /**
     *  plot-width-in-time-msec, Default width of Gauge plots, in milliseconds.
     */
    int _gaugeWidthMsec = 120 * 1000;

    /**
     * plot-nodata-timeout,default is 10 minutes
     */
    int _dataTimeout = 600; 

    HashMap<String, Gauge> _gaugesByName = new HashMap<String,Gauge>();

    String _name;

    CentTabWidget _centTabWidget = null;

    private Log _log;

    /**
     *  constructor.
     */	
    public GaugePage(CentTabWidget p, String name)
    {
        super.setParent(p);
        _log = p.getLog();
        _centTabWidget = p;
        _name = name;

        /*
        // policy for this GaugePage
        QSizePolicy sizePolicy = new QSizePolicy(
            Policy.Expanding,Policy.Expanding);
        // sizePolicy.setHorizontalStretch((byte)0);
        // sizePolicy.setVerticalStretch((byte)0);
        // sizePolicy.setHeightForWidth(true);
        setSizePolicy(sizePolicy);
        */

        /*
         * minimumSizeHint() returns an invalid size if there is no
         * layout for this widget, and returns the layouts minimum
         * size otherwise.  If minimumSize() is set, minimumSizeHint()
         * is ignored.
         * CentTabWidget (QTabWidget) has a layout manager,
         * QStackedLayout.
         *  resize(new QSize(900, 600).expandedTo(minimumSizeHint()));
         */
        resize(new QSize(900, 600));

        _gaugelayout = new QGridLayout();
        QWidget plotwidget = new QWidget();
        plotwidget.resize(new QSize(800, 600));//.expandedTo(minimumSizeHint()));
        plotwidget.setLayout(_gaugelayout);

        _scrollArea = new QScrollArea(this);
        _scrollArea.resize(800,600);
        _scrollArea.setWidget(plotwidget);
        _scrollArea.setWidgetResizable( true);
        _scrollArea.adjustSize();
        _scrollArea.setHorizontalScrollBarPolicy(
                Qt.ScrollBarPolicy.ScrollBarAlwaysOff);

	// create layout for this
        QVBoxLayout verticalLayout = new QVBoxLayout(this);
        verticalLayout.addWidget(_scrollArea);
        setLayout(verticalLayout);

        _gaugeSize = new QSize(Cockpit.gwdef, Cockpit.ghdef);
        _gaugePolicy = new QSizePolicy(
            Policy.Expanding,Policy.Expanding);
        _gaugePolicy.setHorizontalStretch((byte)0);
        _gaugePolicy.setVerticalStretch((byte)0);
        _gaugePolicy.setHeightForWidth(true);
        _ncols = calcGaugeColumns();
        System.out.printf("ncols=%d\n",_ncols);
    }

    public Log getLog()
    {
        return _log;
    }

    /**
     * Create a Gauge.
     * @param Var -- the variable.
     */
    public Gauge addGauge(Var var)
    {
        synchronized(this) {
            int ng = _gauges.size();
            Gauge g = new Gauge(this, _gaugeSize, _gaugeWidthMsec, var);

            g.setSizePolicy(_gaugePolicy);
            _gauges.add(g);
            String pname = g.getName();
            _gaugesByName.put(pname, g);

            int row = ng / _ncols;
            int col = ng % _ncols;
            /*
            System.out.printf("adding Gauge=%s, row=%d,col=%d\n",
                    g.getName(),row,col);
            */
            _gaugelayout.addWidget(g,row,col);
            return g;
        }
    } 

    public QSize getGaugeSize()
    {
        return _gaugeSize;
    }


    public int getNumColumns()
    {
        return _ncols;
    }

    public List<Gauge> getGauges()
    {
        return _gauges;
    }

    public String getName() {
        return _name;
    }

    public QColor getCColor() {
        return _currentColor;
    }

    public QColor getHColor() {
        return _historyColor;
    }

    public QColor getBGColor() {
        return _bgColor;
    }
    public void setBGColor(QColor bgdc) {
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
     * get reduction period
     */
    public int getReductionPeriod()
    {
        return _reductionPeriod;
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
     */
    public void freezeLayout(int ncols, QSize gaugeSize)
    {
        _gaugePolicy.setHorizontalPolicy(Policy.Fixed);
        _gaugePolicy.setVerticalPolicy(Policy.Fixed);
        setSizePolicyOfGauges();
        _gaugeSize = gaugeSize;
        setSizeOfGauges();
        if (ncols != _ncols) {
            _ncols = ncols;
            redoLayout();
        }
        _ncols = ncols;
        update(); 
    }

    public void unfreezeLayout()
    {
        _gaugePolicy.setHorizontalPolicy(Policy.Expanding);
        _gaugePolicy.setVerticalPolicy(Policy.Expanding);
        setSizePolicyOfGauges();
        int ncols = calcGaugeColumns();
        if (ncols != _ncols) {
            _ncols = ncols;
            redoLayout();
        }
        _ncols = ncols;
        update(); 
        fetchGaugeSize();
    }

    public void setSizePolicyOfGauges()
    {
        synchronized (_gauges){
            for (Gauge gauge : _gauges) {
                gauge.setSizePolicy(_gaugePolicy);
            }
        }
    }

    public void setSizeOfGauges()
    {
        synchronized (_gauges){
            for (Gauge gauge : _gauges) {
                gauge.resize(_gaugeSize);
            }
        }
    }

    public int calcGaugeColumns()
    {
        // TODO: account for margins
        int cols = 1;
        if (_gaugePolicy.horizontalPolicy() == Policy.Expanding) {
            if (!_gauges.isEmpty()) {
                int mW = Cockpit.gwdef;
                int mH = Cockpit.ghdef;
                double perarea = (double)(width() * height()) / _gauges.size();
                perarea = Math.max(perarea, (double)(mW * mH));

                double newW = Math.sqrt(perarea * 3 / 2);
                // _gw = (int)newW;
                // _gh = (int)(newW * 2 / 3);
                cols = Math.max((int)((double)width() / newW), 1);
            }
            else {
                cols = Math.max((int)((double)width() / _gaugeSize.width()), 1);
            }
        }
        else {
            cols = Math.max((int)((double)width() / _gaugeSize.width()), 1);
        }
        return cols;
    }

    private void fetchGaugeSize()
    {
        if (!_gauges.isEmpty()) {
            Gauge g = _gauges.get(0);
            _gaugeSize = g.size();
        }
    }

    protected void redoLayout()
    {
        if (_gauges == null || _gauges.size() < 1) return;
        for (Gauge gauge : _gauges) {
            _gaugelayout.removeWidget(gauge);
        }

        for (int i = 0; i < _gauges.size(); i++) {
            int row = i / _ncols;
            int col = i % _ncols;
            Gauge g = _gauges.get(i);
            _gaugelayout.addWidget(g,row, col);
        }
        update(); 
    } 

    public void focusInEvent( QFocusEvent arg)
    {
        if (!isActiveWindow()) return;
        //_centTabWidget .setMinimumSize(_pageSize);
    }

    public void resizeEvent(QResizeEvent ent)
    {
        if (!isActiveWindow()) return;

        if (ent.isAccepted()){
            synchronized(this){
                redoLayout();
            }
        }
        update();
    }

    public void destroyWidget(boolean destroyWindow)
    {
        super.destroy(destroyWindow);
    }

    public void remove(Gauge g)
    {
        synchronized (this) {
            // Is this necessary?
            //remove all plots
            for (Gauge gauge : _gauges) {
                _gaugelayout.removeWidget(gauge);
            }
            //remove one from gauge list
            _gauges.remove(g); 
            // g.hide();        
            g.destroyWidget(true);
            update();
            //set fixed policy
            /*
            if (getPolicy()==Policy.Preferred) { //set it to fixed
                setAllPolicy(Policy.Fixed);
            }
            */
            //redoLayout;
            _ncols = calcGaugeColumns();
            for (int i = 0; i < _gauges.size(); i++) {
                int row = i / _ncols;
                int col = i % _ncols;  //index
                _gaugelayout.addWidget(_gauges.get(i),row, col);
            }
            update();
        }
    }

    public void cleanupHistory()
    {
        for (Gauge gauge : _gauges) {
            gauge.initPixmaps();
            gauge.changeCColor(_currentColor);
        }
    }

    /**
     * If the flag=false, it means (local-rescale) forced rescale. 
     * Otherwise, it will check to see yf is in the range.  
     * @param flag
     */
    public void gautoScalePlots(boolean flag)
    {
        for (Gauge gauge : _gauges) {
            gauge.autoScalePlot(flag);
        }
    }

    public void colorCurrent(QColor c)
    {
        _currentColor = c;// new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (Gauge gauge : _gauges) {
            gauge.changeCColor(_currentColor);
        }
    }


    public void colorHistory(QColor c)
    {
        _historyColor = c;//new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (Gauge gauge : _gauges) {
            gauge.changeHColor(_historyColor);
        }
    }


    public void colorBackGround(QColor c) {
        _bgColor = c;
        for (Gauge gauge : _gauges) {
            gauge.changeBGColor(_bgColor);
        }
    }

    public void createGauges(Dsm dsm)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        ArrayList<Sample> samples = dsm.getSamples();
        for (Sample samp : samples) {
            ArrayList<Var> vars = samp.getVars();
            for (Var var : vars) {
                addGauge(var);
            }
        }

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    public void createGauges(List<Var> vars) 
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        for (Var var : vars) {
            addGauge(var);
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    /*
    public void removeGaugeFromWidget(Gauge g) {
        // if (g.getVar().getDisplay() ) return;
        _gaugelayout.removeEventFilter(g);
    }

    public void addGaugeToWidget(Gauge g) {
        // g.getVar().setDisplay(true);
        _gaugelayout.addWidget(g);
    }
    */

    public void mouseReleaseEvent(QMouseEvent pEvent)
    {
        if (pEvent.button() == MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu("");
            QMenu option = pMenu.addMenu("RenamePage");
            option.addAction("&RenamePage", this, "renamePage()");
        }
    }

    private void renamePage(){
        boolean ok;
        String text = QInputDialog.getText(this,
                "Get Page Name", "Enter a new name:",
                QLineEdit.EchoMode.Normal,"");
        if ( text!=null  ) {
            setWindowTitle(text);  // user entered something and pressed OK
        } 
    }

}


