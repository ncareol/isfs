package edu.ucar.nidas.apps.cockpit.model;

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
import edu.ucar.nidas.model.Var;

public class GaugePageConfig {

    List<GaugeConfig> _gaugeConfigs = new ArrayList<GaugeConfig>();

    String _name;

    int[] _size = new int[2];
    
    public GaugePageConfig(GaugePage gp)
    {
        _name = gp.getName();
        _size[0] = gp.size().width();
        _size[1] = gp.size().height();
        for (Gauge gauge : gp.getGauges()) {
            GaugeConfig vui = new GaugeConfig(gauge.getName(),
                gauge.getYMin(), gauge.getYMax(),
                gauge.getDataTimeout(), gauge.getWidthMsec(),
                gauge.getTraceColor().rgb(), gauge.getHistoryColor().rgb(),
                gauge.getBGColor().rgb());
            _gaugeConfigs.add(vui);
        }
    }

    public GaugePageConfig(Node n)
    {

        String value = getValue(n,"name");
        if (value!=null && value.length() > 0) _name = value;

        value = getValue(n,"width");
        if (value!=null && value.length() > 0) _size[0] = (Integer.valueOf(value).intValue());

        value = getValue(n,"height");
        if (value!=null && value.length() > 0) _size[1] = (Integer.valueOf(value).intValue());

        // value = getValue(n, "prim");
        // if (value!=null && value.length() > 0) _primary = (Boolean.valueOf(value).booleanValue());
            
        NodeList nl = n.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            GaugeConfig p = new GaugeConfig(nl.item(i));
            _gaugeConfigs.add(p);
        }
    }
    
    public String getName() {
        return _name;
    }
    
    public int[] getSize(){
        return _size;
    }
    
    public List<GaugeConfig> getGaugeConfigs() {
        return _gaugeConfigs;
    }
    
    public void setName(String name) {
        _name = name;
    }
    
    public void setSize(int[] size){
        _size = size;
    }
    
            
    public void toDOM(Document document, Element parent)
    {
        Element em = document.createElement("GaugePageConfig");
        em.setAttribute("name", _name);
        // em.setAttribute("prim", String.valueOf(_primary));
        em.setAttribute("width", String.valueOf(_size[0]));
        em.setAttribute("height", String.valueOf(_size[1]));
        parent.appendChild(em);
        for (GaugeConfig config : _gaugeConfigs) {
            config.toDOM(document, em);
        }
    }
    
    /*
    public void applyGaugePageConfig(GaugePage gp){
        gp.setWindowTitle(_name);
        gp.resize(_size[0], _size[1]);
        gp.setPrimary(_primary);
        System.out.println("tabpage-apply "+_name);

        for (int i = 0; i<_gaugeConfigs.size(); i++) {
            GaugeConfig plotc = _gaugeConfigs.get(i);
            Gauge g = gp.getNameToGauge().get(plotc.getName());
            System.out.println(i+" gauge= "+_name);
            if (g!=null) plotc.applyGaugeConfig(g);
        }
    }
    */
    
    private String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }
    
    
}
