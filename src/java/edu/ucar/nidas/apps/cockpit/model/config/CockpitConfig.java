package edu.ucar.nidas.apps.cockpit.model.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.ucar.nidas.apps.cockpit.ui.CentTabWidget;
import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.BasicDom;
import edu.ucar.nidas.model.Site;

/*
 * This class contains the project display parameters, and tabpages.
 * It also provides the interface to set and get the elements
 */
public class CockpitConfig   {

    String          _name;
    List<TabPageConfig>   _tabpages = new ArrayList<TabPageConfig>();

    public CockpitConfig() { }


    public CockpitConfig(CentTabWidget p) {
        _name = p.getName();
        createTabpageConfig(p.getGaugePages());
    }

    public void setName(String name) {
        _name = name;
    }

    public void setTabPageConfig(List<TabPageConfig> tps) {
        _tabpages = tps;
    }

    public String getName() {
        return _name;
    }

    public List<TabPageConfig> getTabPageConfig() {
        return _tabpages;
    }

    public void writeCockpitConfig(Document document) {
        Element rootElement = document.createElement("CockpitConfig");
        rootElement.setAttribute("name",_name);
        document.appendChild(rootElement);
        int size = _tabpages.size();
        for (int i = 0; i<size; i++){
            _tabpages.get(i).writeTabpageConfig(document, rootElement);
        }

    }

    public void walkCockpitConfig(Node n){
        if (n==null) return;
        String name = n.getAttributes().getNamedItem("name").getNodeValue();
        if (name==null || name.length()<1) return;
        _name = name;
        _tabpages.clear();
        NodeList nl = n.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            TabPageConfig tp = new TabPageConfig();
            tp.walkTabpageConfig(nl.item(i));
            if (tp!=null) _tabpages.add(tp);
        }
    }

      
    private void createTabpageConfig(List<GaugePage> ggs) {
        _tabpages.clear();// = new ArrayList<TabPageConfig>();
        for (int i=0; i<ggs.size(); i++) {
            GaugePage gp = ggs.get(i);
            TabPageConfig tp = new TabPageConfig(gp);
            if (tp!=null) _tabpages.add(tp);
        }

    }




}

