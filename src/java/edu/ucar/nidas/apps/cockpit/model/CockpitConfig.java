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

import edu.ucar.nidas.apps.cockpit.ui.CentTabWidget;
import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.Site;

/*
 * This class contains the project display parameters, and tabpages.
 * It also provides the interface to set and get the elements
 */
public class CockpitConfig {

    String _name;

    List<GaugePageConfig> _tabpages = new ArrayList<GaugePageConfig>();

    // public CockpitConfig() { }

    /**
     * Construct CockpitConfig from current Widgets.
     */
    public CockpitConfig(CentTabWidget p)
    {
        _name = p.getName();
        for (GaugePage gp : p.getGaugePages()) {
            GaugePageConfig tp = new GaugePageConfig(gp);
            _tabpages.add(tp);
        }
    }

    /**
     * Construct from a DOM Document.
     */
    public CockpitConfig(Document doc) throws NumberFormatException
    {
        Node n = doc.getFirstChild(); //there is only one

        if (n==null) return;
        String name = n.getAttributes().getNamedItem("name").getNodeValue();
        if (name==null || name.length()<1) return;
        _name = name;
        NodeList nl = n.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            GaugePageConfig tp = new GaugePageConfig(nl.item(i));
            _tabpages.add(tp);
        }
    }

    public void setName(String name) { _name = name; }

    public String getName() { return _name; }

    public List<GaugePageConfig> getGaugePageConfig()
    {
        return _tabpages;
    }

    public void toDOM(Document document) {
        Element rootElement = document.createElement("Cockpit");
        rootElement.setAttribute("name",_name);
        document.appendChild(rootElement);
        int size = _tabpages.size();
        for (int i = 0; i < size; i++){
            _tabpages.get(i).toDOM(document, rootElement);
        }
    }
}

