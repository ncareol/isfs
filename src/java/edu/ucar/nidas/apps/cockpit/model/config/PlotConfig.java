package edu.ucar.nidas.apps.cockpit.model.config;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.gui.QColor;

import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.model.BasicDom;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

public class PlotConfig {

    Var     _var;
    String  _name;

    float   _max;
    float   _min;
    int     _noDataTm;
    int     _plotTmRange;
    int     _ccolor;
    int     _hcolor;
    int     _bgcolor;

    public PlotConfig(String name,  Var var, float min, float max, int dtm, int ptm,  int c,  int h, int b ) {
        _name = name;
        _var = var;
        _min = min;
        _max = max;
        _noDataTm = dtm;
        _plotTmRange = ptm;
        _ccolor = c;
        _hcolor= h;
        _bgcolor = b;
    }

    public PlotConfig() { }

    public void setVar(Var var ) {
        _var = var;
    }

    public Var getVar() {
        return _var;
    }

    public void setName(String name ) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    /*  public void setcName(String cname ) {
        _cname = cname;
    }

    public String getcName() {
        return _cname;
    }
     */
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

    public void writePlotConfig(Document document, Element parent){
        Element subem = document.createElement("PlotConfig");
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

        //add var
        /**
         * public int getSampleId() {return _sampleId;}
            public int getOffset()      {return _offset;}
            public int getLength()          {return _length;}
            public String getName()     {return _name;}
            public String getUnits()    {return _units;}
            public float getMin()       {return _plotMin;}
            public float getMax()       {return _plotMax;}
            public boolean getDynamic()     {return _dynamic;}
            public boolean getDisplay()     {return _display;}
         */
        Var v = _var;
        Element ssem = document.createElement("VarConfig");
        ssem.setAttribute("name", v.getName());
        ssem.setAttribute("sampleId", String.valueOf(v.getSampleId()));
        ssem.setAttribute("units", String.valueOf(v.getUnits()));
        ssem.setAttribute("length", String.valueOf(v.getLength()));
        ssem.setAttribute("offset", String.valueOf(v.getOffset()));
        ssem.setAttribute("max",  String.valueOf(v.getMax()));
        ssem.setAttribute("min",  String.valueOf(v.getMin()));
        ssem.setAttribute("dynamic", String.valueOf(v.getDynamic()));
        ssem.setAttribute("display", String.valueOf(v.getDisplay()));
        subem.appendChild(ssem);
    }

    public void walkPlotConfig(Node n){
        if (n==null) return;
        Node vn = n.getChildNodes().item(0); //only one

        String value =getValue(n, "name");
        if (value!=null && value.length()>0) _name = value;
        //   value =getValue(n, "cname");
        //   if (value!=null && value.length()>0) _cname = value;
        value =getValue(n, "max");
        if (value!=null && value.length()>0) _max = (Float.valueOf(value).floatValue());
        value =getValue(n, "min");
        if (value!=null && value.length()>0) _min = (Float.valueOf(value).floatValue());
        value =getValue(n, "noDataTm");
        if (value!=null && value.length()>0) _noDataTm = (Integer.valueOf(value).intValue());
        value =getValue(n, "plotTmRange");
        if (value!=null && value.length()>0) _plotTmRange = (Integer.valueOf(value).intValue());
        value =getValue(n, "ccolor");
        if (value!=null && value.length()>0) _ccolor = (Integer.valueOf(value).intValue());  
        value =getValue(n, "hcolor");
        if (value!=null && value.length()>0) _hcolor = (Integer.valueOf(value).intValue()); 
        value =getValue(n, "bgcolor");
        if (value!=null && value.length()>0) _bgcolor = (Integer.valueOf(value).intValue()); 

        //var
        //Node vn = n.getChildNodes().item(0); //only one
        if (vn==null) {
            Util.prtErr("var node is null");
        }

        _var = new Var();
        value =getValue(vn, "name");
        if (value!=null && value.length()>0) _var.setName(value);
        value =getValue(vn, "sampleId");
        if (value!=null && value.length()>0) _var.setSampleId(Integer.valueOf(value).intValue());
        value =getValue(vn, "units");
        if (value!=null && value.length()>0) _var.setUnits(value);
        value =getValue(vn, "length");
        if (value!=null && value.length()>0) _var.setLength((Integer.valueOf(value).intValue()));
        value =getValue(vn, "offset");
        if (value!=null && value.length()>0) _var.setOffset(Integer.valueOf(value).intValue());
        value =getValue(vn, "max");
        if (value!=null && value.length()>0) _var.setMax(Float.valueOf(value).floatValue());  
        value =getValue(vn, "min");
        if (value!=null && value.length()>0) _var.setMin(Float.valueOf(value).floatValue()); 
        value =getValue(vn, "dynamic");
        if (value!=null && value.length()>0) _var.setDynamic(Boolean.valueOf(value).booleanValue()); 
        value =getValue(vn, "display");
        if (value!=null && value.length()>0) _var.setDisplay(Boolean.valueOf(value).booleanValue()); 

    }

    /*public void applyPlotConfig(Gauge g) {
        // TODO set name-min-max 
        g.setCColor(new QColor(_ccolor));
        g.setHColor(new QColor(_hcolor));
        g.setBGColor(new QColor(_bgcolor));
        g.setNoDataTmout(_noDataTm);
        g.setNewTimeMSec(_plotTmRange);
        //g.setVar(_var);
    }*/

    private String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }

}

