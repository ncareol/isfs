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

package edu.ucar.nidas.apps.cockpit.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import edu.ucar.nidas.apps.cockpit.ui.CentTabWidget;
import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.Site;
import edu.ucar.nidas.util.DOMUtils;

/*
 * This class contains the project display parameters, and tabpages.
 * It also provides the interface to set and get the elements
 */
public class CockpitConfig {

    String _name;

    List<GaugePageConfig> _tabpages = new ArrayList<GaugePageConfig>();

    int _tabCycleSec = 0;

    // public CockpitConfig() { }

    /**
     * Construct CockpitConfig from current Widgets.
     */
    public CockpitConfig(CentTabWidget p)
    {
        setName(p.getName());
        _tabCycleSec = p.getTabCycleSec();
        for (GaugePage gp : p.getGaugePages()) {
            GaugePageConfig tp = new GaugePageConfig(gp);
            _tabpages.add(tp);
        }
    }

    public void setName(String name) { _name = name; }

    public String getName() { return _name; }

    public int getTabCycleSec() { return _tabCycleSec; }

    public List<GaugePageConfig> getGaugePageConfig()
    {
        return _tabpages;
    }

    /**
     * Construct from a DOM Document.
     */
    public CockpitConfig(Document doc) throws NumberFormatException, SAXException
    {
        Node n = doc.getFirstChild(); //there is only one
        if (n == null) throw new SAXException("First element is null");

        String value = DOMUtils.getAttribute(n, "name");
        if (value == null || value.isEmpty()) value = "unknown";
        _name = value;

        value = DOMUtils.getAttribute(n,"tabCycleSec");
        if (value != null && !value.isEmpty()) _tabCycleSec =
            (Integer.valueOf(value).intValue());
        else _tabCycleSec = 0;

        NodeList nl = n.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            n = nl.item(i);
            if ("GaugePage".equals(n.getNodeName())) {
                GaugePageConfig tp = new GaugePageConfig(n);
                _tabpages.add(tp);
            }
        }
    }

    public void toDOM(Document document)
    {
        Element root = document.createElement("Cockpit");

        root.setAttribute("name",_name);
        root.setAttribute("tabCycleSec", String.valueOf(_tabCycleSec));
        document.appendChild(root);

        int size = _tabpages.size();
        for (int i = 0; i < size; i++){
            _tabpages.get(i).toDOM(document, root);
        }
    }
}

