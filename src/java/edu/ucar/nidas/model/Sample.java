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
import java.util.HashMap;

import edu.ucar.nidas.util.DOMUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A collection of Vars, with an identifier.
 */
public class Sample
{

    Dsm _dsm = null;

    int _id;

    Sample(Dsm dsm) 
    {
        _dsm = dsm;
    }

    Sample() 
    {
    }

    public Dsm getDsm()
    {
        return _dsm;
    }

    /**
     * Set the sample's ID
     * @param val
     */
    public void setId(int val)
    {
	_id = val;
    }

    public int getId() { return _id; }

    /**
     * List of variables in this sample.
     */
    ArrayList<Var> _vars = new ArrayList<Var>();

    /**
     * Offsets of each variable in the data of this sample.
     */
    HashMap<Var, Integer> _offsets = new HashMap<Var, Integer>();
 
    public Var getVar(int i) { return _vars.get(i);}

    public int getNumVars() { return _vars.size();}

    public ArrayList<Var> getVars() { return _vars;}

    /**
     * Get the offset of the variable in this sample's data.
     */
    public int getOffset(Var var) { return _offsets.get(var); }

    /**
     * Loop through all the variables for ONE sample.
     * @param sampNode  - sample node
     */
    public void walkVars(Node sampNode, Site site) throws SAXException
    {
        NodeList nl = sampNode.getChildNodes();

        int offset = 0;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (!"variable".equals(n.getNodeName())) continue;
       
            String varname = DOMUtils.getAttribute(n, "name");

            if (varname == null || varname.isEmpty())
                throw new SAXException("<variable> element with no name");

            Var v = site.getVariable(varname);

            if (v == null) {

                v = new Var(site, varname);

                String attrval = DOMUtils.getAttribute(n, "units");
                if (attrval != null)
                    v.setUnits(attrval);

                int length = 1;
                attrval = DOMUtils.getAttribute(n, "length");
                if (attrval != null && !attrval.isEmpty())
                    length = Integer.valueOf(attrval);
                v.setLength(length);

                String range = DOMUtils.getAttribute(n, "plotrange");
                if (range == null || range.isEmpty()) {
                    v.setMin(-10);
                    v.setMax(10);
                } else {
                    float min = Float.valueOf(range.split(" ")[0].trim());
                    float max = Float.valueOf(range.split(" ")[1].trim());
                    v.setMin(min);
                    v.setMax(max);
                }
                boolean dyn = false;
                attrval = DOMUtils.getAttribute(n, "dynamic");
                if (attrval != null && !attrval.isEmpty())
                    dyn = Boolean.valueOf(attrval);
                v.setDynamic(dyn);

                boolean disp = true;
                attrval = DOMUtils.getAttribute(n, "display");
                if (attrval != null && !attrval.isEmpty())
                    disp = Boolean.valueOf(attrval);
                v.setDisplay(disp);
            
                site.add(v);
            }
            _offsets.put(v, offset);
            offset += v.getLength();
            _vars.add(v);
        }
    }
}
