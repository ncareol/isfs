package edu.ucar.nidas.apps.cockpit.model;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.gui.QColor;

import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

/**
 * Configuration for a Gauge.
 */

public class GaugeConfig {

    /**
     * Full variable name plotted on the Gauge, including
     * "#N" station number suffix, if any.
     */
    String _name;

    float _max;

    float _min;

    int _dataTimeout;

    int _plotWidthMsec;

    int _ccolor;

    int _hcolor;

    int _bgcolor;

    public GaugeConfig(String name,
            float min, float max,
            int timeout, int widthMsec,  int c,  int h, int b )
    {
        _name = name;
        _min = min;
        _max = max;
        _dataTimeout = timeout;
        _plotWidthMsec = widthMsec;
        _ccolor = c;
        _hcolor= h;
        _bgcolor = b;
    }

    public GaugeConfig(Node n)
    {
        Node vn = n.getChildNodes().item(0); //only one

        String value = getValue(n, "name");
        if (value!=null && value.length()>0) _name = value;

        value = getValue(n, "max");
        if (value!=null && value.length()>0) _max = (Float.valueOf(value).floatValue());
        value = getValue(n, "min");
        if (value!=null && value.length()>0) _min = (Float.valueOf(value).floatValue());
        value = getValue(n, "dataTimeout");
        if (value!=null && value.length()>0) _dataTimeout = (Integer.valueOf(value).intValue());
        value = getValue(n, "plotWidthMsec");
        if (value!=null && value.length()>0) _plotWidthMsec = (Integer.valueOf(value).intValue());
        value = getValue(n, "ccolor");
        if (value!=null && value.length()>0) _ccolor = (Integer.valueOf(value).intValue());  
        value = getValue(n, "hcolor");
        if (value!=null && value.length()>0) _hcolor = (Integer.valueOf(value).intValue()); 
        value = getValue(n, "bgcolor");
        if (value!=null && value.length()>0) _bgcolor = (Integer.valueOf(value).intValue()); 
    }

    public String getName()
    {
        return _name;
    }

    public int getTraceColor()
    {
        return _ccolor;
    }

    public int getHistoryColor()
    {
        return _hcolor;
    }

    public int getBGColor()
    {
        return _bgcolor;
    }

    public void setTraceColor(int val)
    {
        _ccolor = val;
    }

    public void setHistoryColor(int val)
    {
        _hcolor = val;
    }

    public void setBGColor(int val) 
    {
        _ccolor = val;
    }

    public void setMin(int val)
    {
        _min = val;
    }

    public float getMin()
    {
        return _min;
    }

    public void setMax(int val)
    {
        _max = val;
    }

    public float getMax()
    {
        return _max;
    }

    public void setDataTimeout(int val)
    {
        _dataTimeout = val;
    }

    public int getDataTimeout()
    {
        return _dataTimeout;
    }

    public void setPlotWidthMsec(int tm){
        _plotWidthMsec = tm;
    }

    public int getPlotWidthMsec(){
        return _plotWidthMsec;
    }

    public void toDOM(Document document, Element parent)
    {
        Element subem = document.createElement("Gauge");
        subem.setAttribute("name", _name);
        //    subem.setAttribute("cname", _cname);
        subem.setAttribute("max",  String.valueOf(_max));
        subem.setAttribute("min",  String.valueOf(_min));
        subem.setAttribute("dataTimeout", String.valueOf(_dataTimeout));
        subem.setAttribute("plotWidthMsec", String.valueOf(_plotWidthMsec));
        subem.setAttribute("ccolor", String.valueOf(_ccolor));
        subem.setAttribute("hcolor", String.valueOf(_hcolor));
        subem.setAttribute("bgcolor", String.valueOf(_bgcolor));
        parent.appendChild(subem);
    }

    private String getValue(Node n, String attr) {
        Node nn = n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }
}

