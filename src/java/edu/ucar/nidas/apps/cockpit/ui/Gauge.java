package edu.ucar.nidas.apps.cockpit.ui;

import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;
import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.QRectF;
import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.QTimer;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QInputDialog;
import com.trolltech.qt.gui.QColorDialog;
import com.trolltech.qt.gui.QFont;
import com.trolltech.qt.gui.QMenu;
import com.trolltech.qt.gui.QMouseEvent;
import com.trolltech.qt.gui.QPaintEvent;
import com.trolltech.qt.gui.QPainter;
import com.trolltech.qt.gui.QPen;
import com.trolltech.qt.gui.QPixmap;
import com.trolltech.qt.gui.QResizeEvent;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QPainter.CompositionMode;
import com.trolltech.qt.gui.QSizePolicy.Policy;

import edu.ucar.nidas.model.FloatSample;
import edu.ucar.nidas.model.DataClient;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.model.Log;

/**
 * Gauge class is a plot unit of cockpit.
 * It draws the current data in the plot. 
 * The time-span in a plot is 2 minutes be default.
 * When the new data's time-tag is out of the range, it draws the current data into different color 
 * as history. and Clear the plot to draw new data.  
 * 
 * Its main members are:
 * the variable whose data is plotted.
 * gauge-width in milli-seconds
 * history image 
 * _painter to draw the data-line  
 * 
 * @author dongl
 */
public class Gauge extends QWidget implements DataClient
{
    /*
     * gauge's parent page
     */
    GaugePage _parent;

    /**
     * reduction-time-of minMaxer, mili-seconds
     */
    int _reductionTm = 1000;

    /**
     * pixels/sec in Int
     */
    // int _PixelSecInt;

    /**
     * If it does not receive the data for the max-time-out, it will put no-data-image to the plot 
     */
    int     _timeoutSec = 300; 
    /**
     * represent the no-data-time-out
     */
    boolean _noDataPaint = false;

    /**
     * timetag of last sample with non-nan data.
     */
    long    _lastTm =-1;

    /**
     * previous ttag
     */
    long _prevTtag=-1;

    /**
     * previous x-pixel, the previous xp
     */
    int _prevXp= -1;

    /**
     * timer to track the no-data-time-out 
     */
    QTimer  _tm ;

    /**
     * global-request or not.
     * If local, it is forced to re-scale; If global & there is data in the plot, ignore it   
     */
    boolean _gautoScale = false;

    /**
     * data-pair for current plot span
     */
    List<QPoint> _pts = new ArrayList<QPoint>();
    float[] _maxmin;

    /**
     * current image
     */
    QPixmap _pixmap;

    /**
     * history image
     */
    QPixmap _historyPixmap;

    /**
     * no-data image
     */
    QPixmap _noDataPixmap;

    /**
     * painter for current data
     */
    QPainter _painter;

    QPainter _historyPainter;

    /**
     * painter for history data
     */

    /**
     * plot label
     */
    public String _name;
    /**
     * plot units
     */
    String _units;
    /**
     * create it dynamically or not
     */
    boolean _dynamic;
    /**
     * Min float value in y-range
     */
    float _ymin; 
    /**
     * Max float value in y-range
     */
    float _ymax;

    /**
     * Current time in milliseconds of start of plot.
     */
    long _xmin;

    /**
     * Width of plot in milliseconds.
     */
    int _xwidth;

    /**
     * x-position for the newest data
     */
    //int _xp =-1; //x-position

    /**
     * pix/msec in x-axis
     */
    float _xscale = 1.0f;

    /**
     * pix/1.0 in y-axis
     */
    float _yscale = 1.0f;

    /**
     * ticDelta
     */
    float _ticDelta;

    /**
     * x and y locate when a mouse is clicked 
     */
    int _xmouse, _ymouse;  //when right-mouse clicks, get the location

    /**
     * plot width  and height in pix
     */
    int _pwidth, _pheight;  //plot-height-in-pix

    //color
    QColor _cColor= Cockpit.gdefCColor;
    QColor _hColor = Cockpit.gdefHColor;	
    QColor _bgColor = Cockpit.gdefBColor;

    static QColor _red = new QColor(Qt.GlobalColor.red);

    private Log _log;

    /**
     * @param parent
     * @param w Initial widget width (pixels)
     * @param h Initial widget height (pixels)
     * @param gaugeWidthMsec - gauge-width in milli-seconds
     * @param var - variable whose data is plotted
     */
    public Gauge(GaugePage p, QSize size, int gaugeWidthMsec, Var var) {

        _parent = p;
        _log = p.getLog();
        _xmin = 0; //plot-start-tm-in-sec
        _xwidth = gaugeWidthMsec;  //in-milli-seconds
        _reductionTm = p.getReductionPeriod();

        setMinimumSize(size.width()/2,size.height()/2);

        resize(size);

        _name = var.getNameWithStn();
        _units = var.getUnits();
        _dynamic = var.getDynamic();

        _pwidth = size.width();
        _pheight = size.height();

        calTics(var.getMax(), var.getMin());
        rescale(size());
        initPixmaps();

        _cColor= p.getCColor();
        _hColor = p.getHColor();
        _bgColor = p.getBGColor();
        //show();
        _lastTm = System.currentTimeMillis();
        _tm = new QTimer();
        _tm.timeout.connect(this, "timeout()");
        // wakeup more often than the _timeoutSec so that we
        // detect a timeout quicker. 10th of timeoutSec
        int tmlen = (_timeoutSec*1000)/10;
        _tm.start(tmlen);
        /*
        System.out.printf("new Gauge %s: w=%d,h=%d\n",
                getName(),size.width(),size.height());
        */
    }

    public int heightForWidth(int w)
    {
        System.out.printf("Gauge heightForWidth %s: w=%d,h=%d\n",
                getName(),w,w*2/3);
        return w * 2 / 3;
    }

    public void destroyWidget(boolean destroyWindow)
    {
        super.destroy(destroyWindow);
    }

    /**
     * @ param x milliseconds into plot
     */
    private int xpixel(long x) {
        return Math.round(x * _xscale);
    }

    /**
     * @ param y value in data units
     */
    private int ypixel(float y) {
        return Math.round((_ymax - y) * _yscale);
    }


    /**
     * get the parent of the plot
     * @return
     */
    public GaugePage getPage() { return _parent;}

    public void autoResize(){
        synchronized(this) {
            QSize qs = size();
            resize(qs);
        }
    }

    public void setWidthMsec(int msec)
    {
        if (msec== _xwidth) return;
        synchronized(this) {
            _xwidth = msec;

            QSize qs = size();
            resize(qs);
            rescale(qs);
            initPixmaps();
            _pts.clear();
            _maxmin = null;
        }
    }

    public void setDataTimeout()
    {
        int timeout = QInputDialog.getInt(this,"Data Timeout",
                "Seconds",_timeoutSec,1,3600);
        if (timeout <= 0 || _timeoutSec == timeout) return;    
        setDataTimeout(timeout);
    }

    public void setDataTimeout(int sec) {

        if (_timeoutSec == sec) return;
        _timeoutSec = sec;
        _tm.stop();
        _tm.start(sec * 1000 / 6);
        _noDataPaint = false;
        _parent.repaint();
    }

    /**
     * recalculate the scale of the x and y
     * x-axis -- pix/msec
     * y-axis -- pix /1.0
     * @param qs
     */
    private void rescale(QSize qs)
    {
        synchronized(this) {
            _pheight = qs.height();
            _pwidth = qs.width();
            _xscale = _pwidth / (float)_xwidth;
            _yscale = getYScale(); //pheight changed

            _prevTtag = -1;
            _prevXp = -1;
        }
    }


    /**
     * Paint the current data line
     */
    private void paintLines()
    {
        synchronized(this) {
            if (_painter!=null)  {
                _painter.drawLinesFromPoints(_pts);
                drawOutliners(_pts); 
                update();
            }
        }

    }

    /**
     * Clean up the data-history
     */
    public void cleanupHistory ()
    {
        initPixmaps();
        paintLines();
        // repaint();
    }

    /**
     * Color the current data with the new color
     * @param 
     */
    public void changeCColor(QColor c)
    {
        _cColor = c;
        resetPainter();
        paintLines();
        //repaint();
    }

    /**
     * Color the history data-line with the new color.
     * This method will clean up history, and make a new image 
     * @param c
     */
    public void changeHColor(QColor c)
    {
        _hColor = c;
        initPixmaps();
        paintLines();
        //repaint();
    }

    /**
     * /**
     * Color the back-ground of a plot with the new color
     * @param 
     */
    public   void changeBGColor(QColor c)
    {
        _bgColor = c;
        initPixmaps();
        paintLines();
        // repaint();
    }

    /**
     * get the new data, and plot it
     * @param samp - sample-data
     * @param offset -beginning index to read the pair of min and max
     */
    public void receive(FloatSample samp, int offset)
    {

        synchronized (this){
            long x = samp.getTimeTag();
            if (_xmin == 0) {
                _xmin = x - (x % _xwidth);
                // show();
            }
            int xd = (int)(x - _xmin); // milliseconds into plot

            // reach eof plot
            if (xd > _xwidth) {
                drawHistData();
                resetPainter();
                _pts.clear();
                _maxmin = null;
                _xmin = x - (x % _xwidth);
                xd = (int)(x - _xmin);
            }
            int xp = xpixel(xd); //x-pixel-pos

            // String str = getName();
            // if (str.equals("P.2m") || str.equals("Lon"))   System.out.println(" var="+str +"   x="+x + "  xd="+xd+ "  _xp="+ xp +" (int)_xp="+(int)xp);

            float ymin = samp.getData(offset);
            float ymax = samp.getData(offset+1);
            /*
            System.out.printf("name=%s, ymin=%f,ymax=%f\n",
                    getName(),ymin,ymax);
            */

            if (!Float.isNaN(ymin) && !Float.isNaN(ymax)) {
                _noDataPaint = false;
                _lastTm = x;

                int ypmin = ypixel(ymin);
                int ypmax = ypixel(ymax);
                if (ypmin == ypmax) ypmax++;
                //check the _xp-gape
                List<QPoint> pa = new ArrayList<QPoint>();
                int xpts = xp - _prevXp;
                if (_prevTtag>0 && (x-_prevTtag)==_reductionTm ) {
                    for (int i = 0; i< xpts; i++){
                        int xind = _prevXp + 1 + i;
                        pa.add( new QPoint(xind,ypmin));
                        pa.add( new QPoint(xind,ypmax));
                    }
                } else{
                    pa.add( new QPoint(xp,ypmin));
                    pa.add( new QPoint(xp,ypmax));      
                }

                if (_painter != null) {
                    _painter.drawLinesFromPoints(pa);
                    drawOutliners(pa);
                } else {
                    status(" plot=" + getName() + " in receive, null painter.", 10000);
                }

                _pts.addAll(pa);        //push all to the pts
                if (_maxmin == null) {_maxmin = new float[2];_maxmin[0] = ymax;_maxmin[1] = ymin;}
                if (ymax > _maxmin[0]) _maxmin[0] = ymax;
                if (ymin < _maxmin[1]) _maxmin[1] = ymin;

                _prevTtag = x;            //keep prevtime
                _prevXp = xp;

                update();
            }
        }
    }

    public void resizeEvent(QResizeEvent ent)
    {
        // if (_parent.getPolicy() == Policy.Fixed) return;
        synchronized(this) {
            rescale(ent.size());
            initPixmaps();
            // synchronized(this) {
            _pts.clear();
            _maxmin = null;
        }
    }

    /**
     * timer to check the no-data-time-out 
     */
    private void timeout() {
        long c = System.currentTimeMillis();
        if ((c - _lastTm) / 1000 < _timeoutSec) return; 
        _noDataPaint = true;
        repaint();
    }


    public void paintEvent(QPaintEvent e)
    {
        QPainter p = null;
        if (! _pixmap.isNull()){
            p = new QPainter(this);
            p.drawPixmap(0,0, _pixmap);

            if (_noDataPaint) {
                p.setPen(new QColor(Qt.GlobalColor.lightGray));
                p.drawText(width() / 2, height() / 2, "RIP");
            }
        }
        /*if (!_noDataPixmap.isNull()) 
            {
                CompositionMode cpmode = p.compositionMode();//QPainter::CompositionMode_Clear;
                p.setCompositionMode(QPainter.CompositionMode.CompositionMode_Destination);
                p.drawPixmap(0,0,_noDataPixmap);
            }*/
        /*} else  {
            if (! _pixmap.isNull()) p.drawPixmap(0,0, _pixmap);

        }*/

        if (p!=null) p.end();
    }

    public void mouseReleaseEvent(QMouseEvent pEvent)
    {
        if (pEvent.button() == MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu("");
            QMenu option = pMenu.addMenu("Plot_Option");
            QMenu color = option.addMenu("Color"); 
            color.addAction("&CleanUp_History", this, "cleanupLocalHistory()");
            color.addAction("Co&lor_Current", this, "changeLocalCColor()");
            color.addAction("Color_&History", this, "changeLocalHColor()");
            color.addAction("Color_&BackGround", this, "changeLocalBGColor()");
            QMenu scale = option.addMenu("Scale");
            scale.addAction("&ManuallyScale_Plot", this, "changeYMaxMin()");
            scale.addAction("&AutoScale_Plot", this, "forcedAutoScalePlot()");
            option.addAction("SetData&Timeout", this, "setDataTimeout()");
            option.addAction("&AutoResize", this, "autoResize()");
            option.addAction("&DeletePlot", this, "deletePlot()");
            _xmouse= pEvent.globalX();
            _ymouse= pEvent.globalY();
            option.popup(new QPoint(_xmouse, _ymouse) );	
        }
    }

    private void deletePlot()
    {
        _parent.remove(this) ;
    }

    /**
     * paint the history image with new data
     */
    private void drawHistData()
    {
        synchronized(this) {
            if (_historyPainter!=null) _historyPainter.drawLinesFromPoints(_pts);
        }
    }

    /**
     * Make a new history image based on the plot-size, paint the label, unit, and scale on the new image
     * Redraw the no-data-image based on the history image,
     * get the copy of history image for current data drawing
     */
    public void initPixmaps()
    {
        _historyPixmap = new QPixmap(size().width(),size().height());
        _historyPixmap.fill(_bgColor);
        _noDataPixmap = _historyPixmap.copy();

        resetHistoryPainter();
        resetPainter();

        //init nodataImg
        if (_noDataPixmap.isNull()) return;
        QPainter p = new QPainter(_noDataPixmap);
        //if (Cockpit.gnodataImg!=null) { 
        QPen qp = p.pen();
        p.setPen(new QColor(Qt.GlobalColor.lightGray));
        p.drawText(width()/2, height()/2, "RIP");
        p.setPen(qp);
        paintText(p); 
        //}
        p.end();
    }

    /**
     * Color the plot with new color
     */
    private void changeLocalCColor()
    {
        QColor cc = QColorDialog.getColor(_cColor);
        if (cc.value()==0) return;
        _cColor = cc;
        changeCColor(_cColor);
    }

    /**
     * Color the history data with new color, discard the previous histroy image
     */
    private void changeLocalHColor()
    {
        QColor cc = QColorDialog.getColor(_hColor);
        if (cc.value()==0) return;
        _hColor = cc;
        changeHColor(_hColor);
    }

    /**
     *  Color the plot back-ground with new color.
     */
    private void changeLocalBGColor()
    {
        QColor cc = QColorDialog.getColor(_bgColor);
        if (cc.value()==0) return;
        _bgColor = cc;
        changeBGColor(_bgColor);
    }

    private void cleanupLocalHistory()
    {
        cleanupHistory();
    }

    /**
     * Rescale y-axis  (pix/1.0data-value)
     */
    private void changeYMaxMin()
    {
        RescaleDialog rd = new RescaleDialog( _ymax, _ymin, _xmouse, _ymouse) ;
        if (!rd.getOk())  return;
        if (rd.getMax() <=rd.getMin()) {
            _log.error("Y-axis-Max is smaller than Y-axis-Min");
            return;
        }

        changeYMaxMin(rd.getMax(), rd.getMin()); 
    }

    /**
     * Rescale y-axis  , based on given ymax and ymin
     */
    public void changeYMaxMin(float ymax, float ymin)
    {
        if (ymax == _ymax && ymin == _ymin) return;
        synchronized(this) {
            _yscale = getYScale(ymax, ymin);
            _pts.clear();
            _maxmin = null;
            initPixmaps();
        }
        repaint();
    }

    /**
     * forced auto-sclae from the plot's drop-down menu
     */
    public void forcedAutoScalePlot() {
        _gautoScale = false;

        autoScalePlot();
    }

    /**
     * Rescale the plot. 
     * If the g is false, it means (local-rescale) forced rescale. 
     * Otherwise, it will check to see yf is in the range.  
     * @param g
     */
    public void autoScalePlot(boolean g){
        _gautoScale = g;
        autoScalePlot();
        _gautoScale = false;
    }


    /**
     * Rescale the plot. 
     * If the g is false, it means (local-rescale) forced rescale. 
     * Otherwise, it will check to see yf is in the range.  
     */
    private void autoScalePlot(){
        synchronized(this) {
            if (_pts == null || _pts.size()<1){
                _noDataPaint = true;
                repaint();
                return;
            }
            if (_maxmin == null) return;

            float ymax = _maxmin[0];
            float ymin = _maxmin[1];

            //check the old ones with 2% margin
            float oldmargin = Math.abs(_ymax - _ymin) * (float).02;
            boolean ck = _gautoScale && ymin > (_ymin + oldmargin) && ymax < (_ymax - oldmargin);
            if ( ck ) return; //in range

            // add range if the range is small 
            float range = Math.abs(ymax - ymin);
            if (range < 1.0 ) {
                ymax =  ymax + 5;
                ymin =  ymin - 5;
                ck = _gautoScale && ymin > (_ymin + oldmargin) && ymax < (_ymax - oldmargin);
                if (ck) return; //in the range
            }

            _yscale = getYScale(ymax, ymin);
            _pts.clear();
            _maxmin = null;

            initPixmaps();
        }
        repaint();
    }

    /**
     * Requested by a user to remove it
    public void removeSelf()
    {
        // _var.setDisplay(false);
        _parent.removeGaugeFromWidget(this); //use GaugePageThread instead
    }
     */

    private void resetHistoryPainter()
    {
        if (_historyPixmap.isNull()) {
            //initPixmaps();
            return;
        } 
        if (_historyPainter!=null) _historyPainter.end();
        _historyPainter  = new QPainter(_historyPixmap);
        _historyPainter.setPen(_hColor);
    }

    // not synchronized, only called from other synchronized methods
    private void resetPainter()
    {
        if (_historyPixmap.isNull()) {
            //initPixmaps();
            return;
        }
        synchronized(this) {
            if (_painter!=null) _painter.end();
            _pixmap = _historyPixmap.copy();
            QPainter ptr = new QPainter(_pixmap);
            paintText(ptr);
            ptr.end();

            //_pixmap = map.copy();
            _painter = new QPainter(_pixmap);
            _painter.setPen(_cColor);
        }
    }


    /**
     * Paint the plot label, unit, and scales
     */
    private void paintText(QPainter painter) {
        if (painter == null) return; 
        synchronized(this) {
            QFont hfont = painter.font();
            QPen hPen = painter.pen();
            double hsize = hfont.pointSizeF();
            QFont qf = new QFont(hfont);

            int len = rect().height()/10;
            int w = rect().width();

            painter.setPen(new QColor(Qt.GlobalColor.yellow));
            // name 
            qf.setPointSizeF(hsize*.8);
            painter.setFont(qf);
            QRectF rect = new QRectF(0,0,w,len);//QRectF(0,0,w,len*5.6);
            painter.drawText(rect(), Qt.AlignmentFlag.AlignRight.value(),_name);

            //and units
            qf.setPointSizeF(hsize*.75);
            painter.setFont(qf);
            rect = new QRectF(0,rect().height()-qf.pointSizeF()*1.5,w-2,qf.pointSizeF()*1.5);
            painter.drawText(rect, Qt.AlignmentFlag.AlignRight.value(),_units);

            //tics and labels  
            //max and min
            rect = new QRectF(5,0,w,rect().height());
            painter.drawText(rect,Qt.AlignmentFlag.AlignLeft.value(), getLabel(_ymax));
            rect = new QRectF(5,rect().height()-qf.pointSizeF()*1.5,w,qf.pointSizeF()*1.5);
            painter.drawText(rect,Qt.AlignmentFlag.AlignLeft.value(), getLabel(_ymin));

            //paint the rest of ticmarks
            painter.setFont(new QFont(hfont.family(), 5));
            int rofts = (int)((_ymax - _ymin) / _ticDelta) - 1 ;
            for (int i = 1; i<=rofts; i++) {
                int y = ypixel(_ymax - i*_ticDelta);
                painter.drawLine(0, y, 2, y);
            }

            //label the middle point(s)
            if (rofts%2 == 1) {
                int step = rofts / 2 + 1;
                paintLabel(step, painter);
            } else {
                int step = rofts / 2 ;
                paintLabel(step, painter);
                step = rofts / 2 + 1;
                paintLabel(step, painter);
            }

            //reset his-color
            painter.setPen(hPen);
            painter.setFont(hfont);
        }
    }

    public void closePlot() {

        if (_painter!=null) _painter.end();
        if (_historyPainter!=null) _historyPainter.end();
        super.close();	
    }

    private void paintLabel(int step, QPainter painter){
        synchronized(this) {
            float yval = _ymax - step*_ticDelta;
            int y = ypixel(yval);
            painter.drawText(3, y+2, getLabel(yval));
        }
    }

    /**
     * based on the max and min, calculate _ticDelta, _ymax, and _ymin
     * @param ymax
     * @param ymin
     */
    public void calTics(float ymax, float ymin)
    {
        if (ymax <= ymin) {
            status("getTics(): The max is less or equal to min. Exit-calTics()", 10000);
            return;
        }      

        double dt = (ymax - ymin) / 6;
        double l10 = Math.floor(Math.log10(dt));
        double p10 = Math.pow(10.0,l10);
        int icoef = (int)Math.ceil(dt / p10);

        // round 6-9 up to 10, 3-4 to 5
        if (icoef > 5) icoef = 10;
        else if (icoef > 1) icoef = 5;

        synchronized (this) {
            _ticDelta = (float)(icoef * p10);
            _ymax = getMaxTick(ymax, _ticDelta );
            _ymin = getMinTick(ymin, _ticDelta);
        }
    }

    private float getMaxTick(float max, float tic)
    {
        return (float)Math.ceil(max / tic) * tic;
    }

    private float getMinTick(float min, float tic)
    {
        return (float)Math.floor(min / tic) * tic;
    }

    private String getLabel(float f) {
        String str = Float.toString(f).trim();
        if (str.endsWith(".0")) return  str.substring(0,str.length()-2);
        return str;
    }

    private void status(String msg, int tm)
    {
        _parent.status(msg, tm);
    }


    private float getYScale(float ymax, float ymin)
    {
        calTics(ymax, ymin); //_ticDelta , _ymax,  and _ymin
        return  getYScale();
    }

    private float getYScale()
    {
        return _pheight / (_ymax - _ymin);
    }

    /**
     * Transparency of an image
     */
    public  static class Transparency {
        public static Image makeColorTransparent(Image im, final Color color) {
            ImageFilter filter = new RGBImageFilter() {
                // the color we are looking for... Alpha bits are set to opaque
                public int markerRGB = color.getRGB() | 0xFF000000;

                public final int filterRGB(int x, int y, int rgb) {
                    if ( ( rgb | 0xFF000000 ) == markerRGB ) {
                        // Mark the alpha bits as zero - transparent
                        return 0x00FFFFFF & rgb;
                    }
                    else {
                        // nothing to do
                        return rgb;
                    }
                }
            }; 

            ImageProducer ip = new FilteredImageSource(im.getSource(), filter);
            return Toolkit.getDefaultToolkit().createImage(ip);
        }
    }

    /**
     * check the points out of the plot-range and draw an eclipse at the position
     */
    private void drawOutliners(List<QPoint> pts){

        for (int i = 0; i< pts.size(); i++){
            float yy = _ymax - pts.get(i).y()/_yscale;

            if (yy > _ymax && _painter!=null) {
                _painter.setPen(_red);
                _painter.drawEllipse(pts.get(i).x(), ypixel(_ymax), 2, 2);
                _painter.setPen(_cColor);
            }
            if (yy < _ymin && _painter!=null) {
                _painter.setPen(_red);
                _painter.drawEllipse(pts.get(i).x(), ypixel(_ymin)-3, 2, 2);
                _painter.setPen(_cColor);
            }

        }
    }

    //changeYMaxMin(float ymax, float ymin)
    public float getYMax(){
        return _ymax;
    }

    public float getYMin(){
        return _ymin;
    }

    //public void setDataTimeout(float ymax) exists

    public int getDataTimeout(){
        return _timeoutSec;
    }

    public int getWidthMsec()
    {
        return _xwidth;
    }

    public String getName()
    {
        return _name;
    }

    public void setCColor(QColor c)
    {
        changeCColor(c);
    }

    public QColor getCColor()
    {
        return _cColor;
    }

    public void setHColor(QColor c)
    {
        changeHColor(c);
    }

    public QColor getHColor()
    {
        return _hColor;
    }

    public void setBGColor(QColor c)
    {
        changeBGColor(c);
    }

    public QColor getBGColor()
    {
        return _bgColor;
    }
}
