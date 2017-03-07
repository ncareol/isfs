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

package edu.ucar.nidas.model;

import java.util.ArrayList;

import edu.ucar.nidas.util.DOMUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A data sampling module, the soure of one or more samples.
 */
public class Dsm {

    String _name;

    String _location;

    Site _site;

    int _id;

    ArrayList<Sample> _samples = new ArrayList<Sample>();

    public Dsm(Site site)
    {
    }

    public void setId(int val) { _id = val; }
    public void setName(String val) { _name = val; }
    public void setLocation(String val) { _location = val; }

    public int getId() { return _id; }
    public String getName() { return _name; }
    public String getLocation() { return _location; }

    public Site getSite() { return _site; }

    public ArrayList<Sample> getSamples()
    {
        return _samples;
    }

    public void walkSamples(Node dsmNode, Site site) throws SAXException
    {
        NodeList nl = dsmNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (!"sample".equals(n.getNodeName())) continue;

            String id = DOMUtils.getAttribute(n, "id");
            if (id == null || id.length() < 1)
                throw new SAXException("<sample> with no id");
            int sampId = Integer.valueOf(id);

            Sample s = new Sample(this);
            s.setId(sampId);
            //System.out.println("sample_gid="+ val);
            s.walkVars(n, site); 
            _samples.add(s);
        }
    }
}
