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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucar.nidas.util.DOMUtils;
import edu.ucar.nidas.util.Util;

/**
 * A Site consists of one or more Dsms.
 */
public class Site {

    public static int _totalNum = 0;

    String  _name;

    int _number;

    ArrayList<Dsm>  _dsms = new ArrayList<Dsm>();

    HashMap<String,Var> _variables = new HashMap<String,Var>();

    public void setNumber(int num) { _number = num; }

    public void setName(String name) { _name = name; }
 
    public int getNumber()      {return _number;}

    public String getName() {return _name;}

    public ArrayList<Dsm> getDsms(){  return _dsms; }
 
    public Site(Node siteNode) throws SAXException
    {
        String name = DOMUtils.getAttribute(siteNode, "name");
        if (name == null || name.length() < 1) 
            throw new SAXException("name attribute missing for site");
        _name = name;
        
        String number = DOMUtils.getAttribute(siteNode, "number");
        if (number == null || number.length() < 1) _number = (++_totalNum);
        else _number = (Integer.valueOf(number).intValue());
        walkDsms(siteNode);
    }

    /**
     * Return a variable by name for this Site.
     */
    public Var getVariable(String name)
    {
        return _variables.get(name);
    }
    public void add(Var var)
    {
        _variables.put(var.getName(), var);
    }
    
    /**
     * Walk through the data-descriptor-xml to get dsm, sensor, sample, and variable information
     */
    public void  walkDsms(Node siteNode) throws SAXException
    {
        NodeList nl = siteNode.getChildNodes();

        // get all dsms in a site
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (!"dsm".equals(n.getNodeName())) continue;

            String name = DOMUtils.getAttribute(n, "name");
            if (name == null || name.length() < 1)
                throw new SAXException("<dsm> element with no name attribute");

            String id = DOMUtils.getAttribute(n, "id");
            if (id == null || id.length() < 1)
                throw new SAXException("<dsm> " + name + " has no id");

            Dsm dsm = new Dsm(this);
            dsm.setId(Integer.valueOf(id).intValue());
            dsm.setName(name);

            dsm.setLocation(DOMUtils.getAttribute(n, "location"));
            dsm.walkSamples(n, this);
            _dsms.add(dsm);
        }
    }

    public static ArrayList<Site> parse(Document doc) throws SAXException
    {
        ArrayList<Site> sites = new ArrayList<Site>();
        NodeList nl = doc.getElementsByTagName("site");
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            Site site = new Site(n);
            sites.add(site);
        }
	return sites;
    }
}

