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

import edu.ucar.nidas.apps.cockpit.core.CpConnection;
import edu.ucar.nidas.apps.cockpit.model.MinMaxer;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

/**
 * this is the main program of cockpit.
 * It contains a mainWindow ui instance,
 * an array of gauges.
 * and a timer
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

    private 
    QSizePolicy _sizePolicy;
    QGridLayout _gaugelayout;
    QScrollArea _scrollArea;

    //plot 
    ArrayList<Gauge> _gauges = new ArrayList<Gauge>();
    ArrayList<Gauge> _gaugesWait = new ArrayList<Gauge>();
    int _gw=CockPit.gwdef, _gh=CockPit.ghdef, _rectWidth, rectHeight;
    QColor _currentColor = CockPit.gdefCColor, _historyColor = CockPit.gdefHColor, _bgColor=CockPit.gdefBColor;

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
    int _gaugeNoDataTmout = 600; 

    /**
     * a subset of samples from cockpit, they are the samples in this widget
     */
    List<Sample>  	_samples = new ArrayList<Sample>(); //samples in this widget

    HashMap<String, Gauge> _nameToGauge = new HashMap<String,Gauge>(0);

    String _name;

    boolean _primary =true;

    CentTabWidget _parent= null;

    QTimer _tm = new QTimer();
    ///////////////////////////////////////////////////////////////////////
    /**
     *  constructor.
     */	
    public  GaugePage( CentTabWidget  p, List<Sample> samps, String name) {
        if (p==null || samps==null || samps.isEmpty() || name==null || name.length()<=0) {
            Util.prtErr("GaugePage doesn't get enough infor to create an instance. \nCheck new GaugePage details");
        }
        super.setParent(p);
        _parent=p;
        _name= name;
        _samples = samps;
        createUIs();

        _tm.timeout.connect(this, "VaryingTimeout()");
        _tm.start(60000); //1 min
    }

    public  GaugePage() {  }

    public void setPrimary( boolean prim){
        _primary=prim;
    }

    public boolean getPrimary() {
        return _primary;
    }  

    public void destroyWidget(boolean b) {
        super.destroy(b);
    }



    public Policy getPolicy() {
        return _sizePolicy.verticalPolicy(); //vertical and horizontal policy is the same
    }

    public HashMap<String, Gauge> getNameToGauge() {
        return _nameToGauge;
    }

    /**
     * set the new time-width-in-milli-seconds
     * @param msec
     */
    public void setGaugeTimeMSec(int msec) {
        if (msec != _gaugeWidthMsec)
        {
            _gaugeWidthMsec= msec;
            for (int i=0; i<_gauges.size(); i++) {
                _gauges.get(i).setNewTimeMSec(msec);
            }
        }
    }

    /**
     * get the time-width-in-milli-seconds
     * @return
     */
    public int getGaugeTimeMSec() {
        return _gaugeWidthMsec;
    }

    /**
     * get reduction period
     */
    public int getReductionPeriod() {
        return _reductionPeriod;
    }

    /**
     * set the new time-width-in-milli-seconds
     * @param msec
     */
    public void setGaugeNoDataTimeout(int sec) {
        if (sec != _gaugeNoDataTmout)
        {
            _gaugeNoDataTmout= sec;
            for (int i=0; i<_gauges.size(); i++) {
                _gauges.get(i).setNoDataTmout(sec);
            }
        }
    }

    /**
     * get the time-width-in-milli-seconds
     * @return
     */
    public int getGaugeNoDataTmout() {
        return _gaugeNoDataTmout;
    }


    public ArrayList<Gauge> getPlots() {
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

    
    protected void createUIs() {
        resize(new QSize(900, 600).expandedTo(minimumSizeHint()));
        getSizePol(Policy.Preferred); //set _sizePolicy=
        setSizePolicy(_sizePolicy);	 //set page's policy

        _gaugelayout = new QGridLayout();
        QWidget plotwidget = new QWidget();		//verticalLayout = new QVBoxLayout(this);
        plotwidget.resize(new QSize(800, 600));//.expandedTo(minimumSizeHint()));
        plotwidget.setLayout(_gaugelayout);

        _scrollArea = new QScrollArea(this);
        _scrollArea.resize(800,600);
        _scrollArea.setWidget(plotwidget);
        _scrollArea.setWidgetResizable( true);
        _scrollArea.adjustSize();
        _scrollArea.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff);
        // create layout for this
        QVBoxLayout verticalLayout = new QVBoxLayout(this);
        verticalLayout.addWidget(_scrollArea);
        setLayout(verticalLayout);
    }

    public void setSamples(List<Sample> ps) {
        _samples=ps;
    }

    public List<Sample> getSamples(){
        return _samples;
    }


    public void addSample(Sample ps) {
        _samples.remove(ps); //to avoid multiple
        _samples.add(ps);
    }

    public void removeSample(Sample ps) {
        _samples.remove(ps);
    }

    public void removeSamples(List<Sample> ps) {
        for (int i=0; i<ps.size(); i++) {
            _samples.remove(ps.get(i));
        }
    }

    public List<Var> getVars() {
        if (_gauges == null || _gauges.size()==0) {
            return null;
        }
        int len = _gauges.size();
        List<Var> vars = new ArrayList<Var>();
        for(int i=0; i<len; i++) {
            Var var= _gauges.get(i).getVar();
            if (!vars.contains(var)) vars.add(var);
        }
        return vars;
    }

    public void toggleFixedGaugeSize(Policy policy)
    {
        synchronized (_gauges){
            if (_gauges.size()<=0) return;
            getSizePol(policy); //reset _sizePolicy
            setSizePolicy(_sizePolicy);  //set page's policy
            _gw= _gauges.get(0).width();
            _gh=_gauges.get(0).height();
            if (policy==Policy.Preferred) { _gh=CockPit.ghdef;_gw=CockPit.gwdef;} //policy=preferred reset plotmin=gh gw
            for (int i=0; i<_gauges.size();i++) {
                _gauges.get(i).setSizePolicy(_sizePolicy);// .setFixedSize(_gw,_gh);
                _gauges.get(i).setMinimumSize(_gw,_gh);                   
            }
            if (policy == Policy.Preferred) resize(width(), height());
        }
    }

    private void getSizePol(Policy sp){
        _sizePolicy = new QSizePolicy(sp, sp);
        _sizePolicy.setHorizontalStretch((byte)0);
        _sizePolicy.setVerticalStretch((byte)0);
        _sizePolicy.setHeightForWidth(true);
    }
    /**
     * Create a Gauge.
     * @param Var -- the variable.
     */
    public Gauge createGauge(Var var) {
        synchronized (this){
            int ng = _gauges.size();
            int cols=width()/_gw ;
            if (width()%_gw!=0 ) cols--;
            int row = ng / cols;
            int col = ng % cols;  //index
            Gauge g = new Gauge( this, _gw, _gh, _gaugeWidthMsec, var);

            if (_sizePolicy !=null)  g.setSizePolicy(_sizePolicy);// .setFixedSize(_gw,_gh);
            _gauges.add(g);
            String pname = var.getName();
            _nameToGauge.put(pname, g);
            _gaugelayout.addWidget(g,row,col);
            return g;
        }
    } 

    protected void reArrangeGauges()
    {
        if (_gauges == null || _gauges.size() <1) return;
        for (int i = 0; i < _gauges.size(); i++) {
            _gaugelayout.removeWidget(_gauges.get(i));
        }

        int cols = getColNum();
        if (cols<=0) return;
        for (int i = 0; i < _gauges.size(); i++) {
            int row = i / cols;
            int col = i % cols;  //index
            _gaugelayout.addWidget(_gauges.get(i),row, col);
        }
        update(); 
    } 

    public void focusInEvent( QFocusEvent arg){
        if (!isActiveWindow()) return;
        //_parent.setMinimumSize(_pageSize);
    }


    public void resetGaugeOrder(ArrayList<Gauge> newgs){
        if (_gauges.size()<=1) return;
        synchronized (this) {
            for (int i = 0; i < _gauges.size(); i++) {
                _gaugelayout.removeWidget(_gauges.get(i));
            }

            _gauges.clear();
            _gauges = new ArrayList<Gauge>();
            _gauges.addAll(newgs);
            int cols = getColNum();
            if (cols<=0) return;
            for (int i = 0; i < _gauges.size(); i++) {
                int row = i / cols;
                int col = i % cols;  //index
                Gauge g = _gauges.get(i);
                if (g == null) continue;
                _gaugelayout.addWidget(g,row, col++);
            }
        }
    }

    public void resizeEvent(QResizeEvent ent) {

        if (!isActiveWindow()) return;

        if (ent.isAccepted()){
            synchronized(this){
                reArrangeGauges();
                if( _sizePolicy.horizontalPolicy()== Policy.Preferred && !_tm.isActive()) _tm.start(60000); //1 min
            }
        }

        update();
    }

    public void deleteIt(Gauge g) {
        synchronized (this) {
            //remove all plots
            for (int i = 0; i < _gauges.size(); i++) {
                _gaugelayout.removeWidget(_gauges.get(i));
            }
            //remove one from gauge list
            _gauges.remove(g); 
            _gaugesWait.add(g);
            g.hide();        
            update();
            //set fixed policy
            if (getPolicy()==Policy.Preferred) { //set it to fixed
                setAllPolicy(Policy.Fixed);
            }
            //reArrangeGauges;
            int cols = getColNum();
            if (cols<=0) return;
            for (int i = 0; i < _gauges.size(); i++) {
                int row = i / cols;
                int col = i % cols;  //index
                _gaugelayout.addWidget(_gauges.get(i),row, col++);
            }
            update();
        }
    }

    public void setAllPolicy(Policy policy) {
        toggleFixedGaugeSize(policy); //set gauges's policy
        getSizePol(policy);          //set _sizePolicy=
        setSizePolicy(_sizePolicy);  //set page's policy
        if (_parent!=null)  _parent.syncCurrentSizePolicy(policy); //set menu
    }

    int getColNum() {
        int cols = (int)( width() / _gw - 0.5);
        if (cols <= 4) {
            int gwAdd = _gw + 4; //width of a plot + 4 pixels
            if ((width() - (cols * gwAdd)) > gwAdd) cols++;
        }
        if (getPolicy()==Policy.Preferred) cols=calColNum();
        return cols;
    }

    int calColNum(){
        if (_gauges==null || _gauges.size()<1) return -1;
        int mW = CockPit.gwdef; //margin=3 pix
        int mH = CockPit.ghdef; //margin=3 pix
        int size = width()*height();
        float persize = size/_gauges.size();
        if (persize <= mW*mH) return (int)(width()/mW);

        double newW = Math.sqrt(persize*3/2);
        _gw = (int)Math.round(newW);
        _gh = (int)Math.round(newW*2/3);
        return (int)(width()/newW); //cols
    }


    public void cleanupHistory() {
        for (int i=0; i<_gauges.size(); i++){
            _gauges.get(i).initPixmaps();
            _gauges.get(i).changeCColor(_currentColor);
        }
    }

    /**
     * If the flag=false, it means (local-rescale) forced rescale. 
     * Otherwise, it will check to see yf is in the range.  
     * @param flag
     */
    public void gautoScalePlots(boolean flag) {
        for (int i=0; i<_gauges.size(); i++){
            _gauges.get(i).autoScalePlot(flag);
        }
    }

    public void colorCurrent(QColor c) {
        _currentColor = c;// new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (int i=0; i<_gauges.size(); i++) {
            _gauges.get(i).changeCColor(_currentColor);
        }
    }


    public void colorHistory(QColor c) {
        _historyColor = c;//new QColor(c.getRed(), c.getGreen(), c.getBlue());
        for (int i=0; i<_gauges.size(); i++) {
            _gauges.get(i).changeHColor(_historyColor);
        }
    }


    public void colorBackGround(QColor c) {
        _bgColor =c;
        for (int i=0; i<this._gauges.size(); i++) {
            _gauges.get(i).changeBGColor(_bgColor);
        }
    }

    public void createDataClients()
    {
        if (_samples==null || _samples.size()<1){
            Util.prtErr("Pwidget-createDataClient  _samples ==null");
            return;
        }
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        synchronized (_samples){
            Util.prtDbg("createDataClient-begin");
            for (int i = 0; i < _samples.size(); i++) {
                Sample samp = _samples.get(i);
                ArrayList<Var> vars = samp.getVars();
                for (int j = 0; j < vars.size(); j++) {
                    Var var = vars.get(j);
                    createOneDataClient(samp, var);
                }
            }
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void createOneDataClient(Sample samp, Var var){
        if ( var.getLength()!=1) return;   //for nearly all the cases, the variable only has one data, except 1d-probe. Ignore it for now
        MinMaxer mm  = new MinMaxer(_reductionPeriod);
        Gauge g=null;
        if (!var.getDynamic() && var.getDisplay())
            g = createGauge(var); 
        GaugeDataClient gdc = new GaugeDataClient(this, var, g);
        CentTabWidget._varToGdc.put(var, gdc);
        mm.addClient(gdc);
        if (CpConnection._dataTh==null) {Util.prtErr("dataTh==null"); return;}
        CpConnection._dataTh.addClient(samp,var,0,mm);         
    }

    /**
     * @param sampId -sampleId from data and sampleId from xml
     */

    public void removeGaugeFromWidget(Gauge g) {
        //set var-display=false
        if (g.getVar().getDisplay() ) return;
        _gaugelayout.removeEventFilter(g);
    }

    public void addGaugeToWidget(Gauge g) {
        g.getVar().setDisplay(true);
        _gaugelayout.addWidget(g);
    }

    public void mouseReleaseEvent(QMouseEvent pEvent)
    {
        if (pEvent.button() == MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu("");
            QMenu option= pMenu.addMenu("RenamePage");
            option.addAction("&RenamePage", this, "renamePage()");
        }
    }

    private void renamePage(){
        boolean ok;
        String text = QInputDialog.getText(this,
                "Get Page Name", "Enter a new name:", QLineEdit.EchoMode.Normal,"");
        if ( text!=null  ) {
            setWindowTitle(text);  // user entered something and pressed OK
        } 
    }

    void VaryingTimeout() {
        _tm.stop();
        if (_sizePolicy.horizontalPolicy() == Policy.Preferred) setAllPolicy(Policy.Fixed);
    }
}//eof class


