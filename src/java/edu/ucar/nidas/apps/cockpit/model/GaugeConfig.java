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

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import com.trolltech.qt.core.QSize;
import com.trolltech.qt.gui.QColor;

import edu.ucar.nidas.apps.cockpit.ui.Gauge;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.DOMUtils;

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

    QColor _traceColor;

    QColor _historyColor;

    QColor _bgColor;

    public GaugeConfig(String name,
            float min, float max,
            int timeout, int widthMsec,
            QColor c,  QColor h, QColor b)
    {
        _name = name;
        _min = min;
        _max = max;
        _dataTimeout = timeout;
        _plotWidthMsec = widthMsec;
        _traceColor = c;
        _historyColor= h;
        _bgColor = b;
    }

    public GaugeConfig(Node n) throws NumberFormatException, SAXException
    {
        String value = DOMUtils.getAttribute(n, "name");
        if (value != null && !value.isEmpty()) _name = value;
        else throw new SAXException("Gauge has no name attribute");

        value = DOMUtils.getAttribute(n, "max");
        if (value != null && !value.isEmpty())
            _max = Float.valueOf(value).floatValue();

        value = DOMUtils.getAttribute(n, "min");
        if (value != null && !value.isEmpty())
            _min = Float.valueOf(value).floatValue();

        value = DOMUtils.getAttribute(n, "dataTimeout");
        if (value != null && !value.isEmpty())
            _dataTimeout = Integer.valueOf(value).intValue();

        value = DOMUtils.getAttribute(n, "plotWidthMsec");
        if (value != null && !value.isEmpty())
            _plotWidthMsec = Integer.valueOf(value).intValue();

        value = DOMUtils.getAttribute(n, "traceColor");
        if (value != null && !value.isEmpty())
            _traceColor = new QColor(Integer.parseUnsignedInt(value,16));

        value = DOMUtils.getAttribute(n, "historyColor");
        if (value != null && !value.isEmpty())
            _historyColor = new QColor(Integer.parseUnsignedInt(value,16));

        value = DOMUtils.getAttribute(n, "bgColor");
        if (value != null && !value.isEmpty())
            _bgColor = new QColor(Integer.parseUnsignedInt(value,16));
    }

    public String getName()
    {
        return _name;
    }

    public QColor getTraceColor()
    {
        return _traceColor;
    }

    public QColor getHistoryColor()
    {
        return _historyColor;
    }

    public QColor getBGColor()
    {
        return _bgColor;
    }

    public void setTraceColor(QColor val)
    {
        _traceColor = val;
    }

    public void setHistoryColor(QColor val)
    {
        _historyColor = val;
    }

    public void setBGColor(QColor val) 
    {
        _bgColor = val;
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

    public void setPlotWidthMsec(int tm)
    {
        _plotWidthMsec = tm;
    }

    public int getPlotWidthMsec()
    {
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
        subem.setAttribute("traceColor", String.format("%08x", _traceColor.rgba()));
        subem.setAttribute("historyColor", String.format("%08x", _historyColor.rgba()));
        subem.setAttribute("bgColor", String.format("%08x", _bgColor.rgba()));
        parent.appendChild(subem);
    }
}

