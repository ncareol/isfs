package edu.ucar.nidas.apps.cockpit.model.config;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.gui.QColor;

import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.BasicDom;
import edu.ucar.nidas.model.Var;

public class TabPageConfig {

    List<PlotConfig>    _uivars = new ArrayList<PlotConfig>();
    String              _name;
    boolean             _primary;
    int[]               _size = new int[2];
    
    
    public TabPageConfig(GaugePage gp) {
        _name = gp.getName();
        _size[0] = gp.size().width();
        _size[1] = gp.size().height();
        createPlotConfig(gp.getPlots());
        _primary = gp.getPrimary();
    }
    
    public TabPageConfig() {}
    
    public String getName() {
        return _name;
    }
    
    public int[] getSize(){
        return _size;
    }
    
    public List<PlotConfig> getUIVars() {
        return _uivars;
    }
    
    public boolean getPrm() {
        return _primary;
    }
    
    public void setName(String name) {
        _name = name;
    }
    
    public void setSize(int[] size){
        _size = size;
    }
    
    public void setUIVars(List<PlotConfig> uivars) {
        _uivars = uivars ;
    }
    
     
    public void setPrm(boolean prm) {
        _primary = prm;
    }
            
    public void writeTabpageConfig(Document document, Element parent) {
        Element em = document.createElement("TabPageConfig");
        em.setAttribute("name", _name);
        em.setAttribute("prim", String.valueOf(_primary));
        em.setAttribute("width", String.valueOf(_size[0]));
        em.setAttribute("height", String.valueOf(_size[1]));
        parent.appendChild(em);
        for (int i=0; i<_uivars.size(); i++ ){
            _uivars.get(i).writePlotConfig(document, em);
        }
    }
    
    private void createPlotConfig(List<Gauge> gauges) {
        _uivars.clear();
        for (int i=0; i< gauges.size(); i++){
            Gauge g = gauges.get(i);
            PlotConfig vui = new PlotConfig(g.getName(), g.getVar(), g.getYMin(), g.getYMax(),g.getNoDataTmout(),g.getGaugeTimeMSec(),g.getCColor().rgb(),g.getHColor().rgb(), g.getBGColor().rgb());
            _uivars.add(vui);
        }
        //return puivars;
    }
    
    
    public void walkTabpageConfig(Node n){
        if (n==null) return;
        String value = getValue(n,"name");
        if (value!=null && value.length()>0) _name = value;
        value = getValue(n,"width");
        if (value!=null && value.length()>0) _size[0] = (Integer.valueOf(value).intValue());
        value = getValue(n,"height");
        if (value!=null && value.length()>0) _size[1] = (Integer.valueOf(value).intValue());
        value = getValue(n, "prim");
        if (value!=null && value.length()>0) _primary = (Boolean.valueOf(value).booleanValue());
            
        NodeList nl = n.getChildNodes();
        _uivars.clear();
        for (int i=0; i<nl.getLength(); i++) {
            PlotConfig p = new PlotConfig();
            p.walkPlotConfig(nl.item(i));
            _uivars.add(p);
        }
    }
    
   
   /* public void applyTabpageConfig(GaugePage gp){
        gp.setWindowTitle(_name);
        gp.resize(_size[0], _size[1]);
        gp.setPrimary(_primary);
        System.out.println("tabpage-apply "+_name);

        for (int i=0; i<_uivars.size(); i++) {
            PlotConfig plotc = _uivars.get(i);
            Gauge g = gp.getNameToGauge().get(plotc.getName());
            System.out.println(i+" gauge= "+_name);
            if (g!=null) plotc.applyPlotConfig(g);
        }
    }*/
    
    private String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }
    
    
}
