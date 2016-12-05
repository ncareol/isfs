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

    int _noDataTm;

    int _plotTmRange;

    int _ccolor;

    int _hcolor;

    int _bgcolor;

    public GaugeConfig(String name,
            float min, float max,
            int dtm, int ptm,  int c,  int h, int b )
    {
        _name = name;
        _min = min;
        _max = max;
        _noDataTm = dtm;
        _plotTmRange = ptm;
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
        value = getValue(n, "noDataTm");
        if (value!=null && value.length()>0) _noDataTm = (Integer.valueOf(value).intValue());
        value = getValue(n, "plotTmRange");
        if (value!=null && value.length()>0) _plotTmRange = (Integer.valueOf(value).intValue());
        value = getValue(n, "ccolor");
        if (value!=null && value.length()>0) _ccolor = (Integer.valueOf(value).intValue());  
        value = getValue(n, "hcolor");
        if (value!=null && value.length()>0) _hcolor = (Integer.valueOf(value).intValue()); 
        value = getValue(n, "bgcolor");
        if (value!=null && value.length()>0) _bgcolor = (Integer.valueOf(value).intValue()); 
    }

    public String getName() {
        return _name;
    }

    public int getCColor() {
        return _ccolor;
    }

    public int getHColor() {
        return _hcolor;
    }

    public int getBGColor() {
        return _bgcolor;
    }

    public void setCColor(int color) {
        _ccolor = color;
    }

    public void setHColor(int color) {
        _hcolor = color;
    }

    public void setBGColor(int color) {
        _ccolor = color;
    }

    public void setMin(int min){
        _min=min;
    }

    public float getMin(){
        return _min;
    }

    public void setMax(int m){
        _max=m;
    }

    public float getMax(){
        return _max;
    }

    public void setNoDataTm(int t){
        _noDataTm=t;
    }

    public int getNoDataTm(){
        return _noDataTm;
    }

    public void setPlottmRange(int tm){
        _plotTmRange=tm;
    }

    public int getplotTmRange(){
        return _plotTmRange;
    }

    public void toDOM(Document document, Element parent)
    {
        Element subem = document.createElement("GaugeConfig");
        subem.setAttribute("name", _name);
        //    subem.setAttribute("cname", _cname);
        subem.setAttribute("max",  String.valueOf(_max));
        subem.setAttribute("min",  String.valueOf(_min));
        subem.setAttribute("noDataTm", String.valueOf(_noDataTm));
        subem.setAttribute("plotTmRange", String.valueOf(_plotTmRange));
        subem.setAttribute("ccolor", String.valueOf(_ccolor));
        subem.setAttribute("hcolor", String.valueOf(_hcolor));
        subem.setAttribute("bgcolor", String.valueOf(_bgcolor));
        parent.appendChild(subem);
    }

    private String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }
}

