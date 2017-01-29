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

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.gui.QColor;

import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.DOMUtils;

public class GaugePageConfig {

    List<GaugeConfig> _gaugeConfigs = new ArrayList<GaugeConfig>();

    String _name;

    QSize _size = null;

    boolean _frozenPlotSizes = false;

    boolean _frozenGridLayout = false;

    int _ncols;
    
    public GaugePageConfig(GaugePage gp)
    {
        _name = gp.getName();
        _size = gp.size();
        _frozenPlotSizes = gp.frozenPlotSizes();
        _frozenGridLayout = gp.frozenGridLayout();
        for (Gauge gauge : gp.getGauges()) {
            GaugeConfig vui = new GaugeConfig(gauge.getName(),
                gauge.getYMin(), gauge.getYMax(),
                gauge.getDataTimeout(), gauge.getWidthMsec(),
                gauge.getTraceColor(), gauge.getHistoryColor(),
                gauge.getBGColor());
            _gaugeConfigs.add(vui);
        }
    }

    public String getName()
    {
        return _name;
    }
    
    public QSize getSize()
    {
        return _size;
    }
    
    public List<GaugeConfig> getGaugeConfigs() {
        return _gaugeConfigs;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public void setSize(QSize size)
    {
        _size = size;
    }
            
    public GaugePageConfig(Node n) throws NumberFormatException, SAXException
    {

        String value = DOMUtils.getAttribute(n,"name");
        if (value != null && !value.isEmpty()) _name = value;
        else throw new SAXException("GaugePage has no name attribute");

        _size = new QSize();
        value = DOMUtils.getAttribute(n,"width");
        if (value != null && !value.isEmpty())
            _size.setWidth(Integer.valueOf(value).intValue());

        value = DOMUtils.getAttribute(n,"height");
        if (value != null && !value.isEmpty())
            _size.setHeight(Integer.valueOf(value).intValue());

        value = DOMUtils.getAttribute(n,"frozenPlotSizes");
        if (value != null && !value.isEmpty()) _frozenPlotSizes =
            (Boolean.valueOf(value).booleanValue());

        value = DOMUtils.getAttribute(n,"frozenGridLayout");
        if (value != null && !value.isEmpty()) _frozenGridLayout =
            (Boolean.valueOf(value).booleanValue());

        // value = DOMUtils.getAttribute(n, "prim");
        // if (value != null && value.isEmpty() > 0) _primary = (Boolean.valueOf(value).booleanValue());
            
        NodeList nl = n.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            n = nl.item(i);
            if ("Gauge".equals(n.getNodeName())) {
                GaugeConfig p = new GaugeConfig(n);
                _gaugeConfigs.add(p);
            }
        }
    }
    
    public void toDOM(Document document, Element parent)
    {
        Element em = document.createElement("GaugePage");
        em.setAttribute("name", _name);
        // em.setAttribute("prim", String.valueOf(_primary));
        em.setAttribute("width", String.valueOf(_size.width()));
        em.setAttribute("height", String.valueOf(_size.height()));

        em.setAttribute("frozenPlotSizes", String.valueOf(_frozenPlotSizes));
        em.setAttribute("frozenGridLayout", String.valueOf(_frozenGridLayout));

        parent.appendChild(em);
        for (GaugeConfig config : _gaugeConfigs) {
            config.toDOM(document, em);
        }
    }
}
