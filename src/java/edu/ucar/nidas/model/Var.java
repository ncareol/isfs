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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Comparator;

/**
 *  This class describes an ISFS data variable.
 */
public class Var {

    /**
     * regex for getting height:
     */
    static Pattern htregex = Pattern.compile("\\.([0-9]+(\\.[0-9]+)?)(c?m)");

    String _name;

    /**
     * Variable name, with an optional "#N" suffix for station N.
     */
    String _nameWithStn;

    String _units;

    int _length;

    /**
     * Height in meters, parsed from name, such as "u.10m.tower".
     * Negative values are depths, parsed from a name such as
     * "Tsoil.1.5cm.grass". Float.NaN if no height is found in name.
     */
    float _height;
    
    /**
     * default plot_min and plot_max of the variable from the data descriptor
     */
    float _plotMax;
    
    float _plotMin;
    
    /**
     * create a plot at beginning, or later when data arrives.
     * default=false.
     */
    boolean _dynamic=false;
    
    /**
     * The variable is measured in the nidas, but not intended to display. 
     * default =true; 
     */

    boolean _display=true;

    public Var(String nameWithStn)
    {
        _nameWithStn = nameWithStn;
        int pi = _nameWithStn.indexOf("#");
        if (pi >= 0) {
            _name = _nameWithStn.substring(0,pi);
        }
        else {
            _name = _nameWithStn;
        }
        _height = Var.height(_name);
    }

    public Var(Site site, String name)
    {
        _name = name;
        int sitenum =  site.getNumber();
        if (sitenum > 0) {
            _nameWithStn = _name + "#" + sitenum;
        }
        else {
            _nameWithStn = _name;
        }
        _height = Var.height(_name);
    }

    /**
     * Parse height from variable name.
     */
    public static float height(String str) 
    {
        float val = Float.NaN;
	Matcher matcher = htregex.matcher(str);

        if (matcher.find() && matcher.groupCount() == 3) {
            try {
                val = Float.parseFloat(matcher.group(1));
                // depth in cm
                if (matcher.group(3).equals("cm")) {
                    val = -val * 0.01f;
                }
            }
            catch(NumberFormatException e) {
            }
        }
        return val;
    }

    public static final Comparator<Var> HEIGHT_ORDER = 
        new Comparator<Var>() {
            public int compare(Var v1, Var v2) {
                // Float.compare treats NaN as greater than all other float values
                // We'll flip that around
                int res;
                if (Float.isNaN(v1.getHeight()) || Float.isNaN(v2.getHeight()))
                    res =  -Float.compare(v1.getHeight(),v2.getHeight());
                else
                    res = Float.compare(v1.getHeight(),v2.getHeight());

                if (res != 0) return res;
                return v1.getNameWithStn().compareTo(v2.getNameWithStn());
            }
        };

    public static final Comparator<Var> HEIGHT_INVERSE_ORDER = 
        new Comparator<Var>() {
            public int compare(Var v1, Var v2) {
                // Float.compare treats NaN as greater than all other float values
                // We'll flip that around
                int res;
                if (Float.isNaN(v1.getHeight()) || Float.isNaN(v2.getHeight()))
                    res = -Float.compare(v2.getHeight(),v1.getHeight());
                else
                    res = Float.compare(v2.getHeight(),v1.getHeight());

                if (res != 0) return res;
                return v1.getNameWithStn().compareTo(v2.getNameWithStn());
            }
        };

    public static final Comparator<Var> NAME_ORDER = 
        new Comparator<Var>() {
            public int compare(Var v1, Var v2) {
                return v1.getNameWithStn().compareTo(v2.getNameWithStn());
            }
        };

    public static final Comparator<Var> NAME_INVERSE_ORDER = 
        new Comparator<Var>() {
            public int compare(Var v1, Var v2) {
                return v2.getNameWithStn().compareTo(v1.getNameWithStn());
            }
        };

    public void setUnits(String val) { _units = val; }

    public void setMin(float val) { _plotMin = val; }

    public void setMax(float val) { _plotMax = val; }

    public void setDynamic(boolean val)	{ _dynamic = val; }

    public void setDisplay(boolean val) { _display = val; }
    
    /**
     * The data length of the variable. 
     * Usually there is one measurement per variable.
     */
    public void setLength(int val)
    {
        _length=val;
    }

    public float getHeight() { return _height; }

    public int getLength() { return _length; }
	
    public String getName() { return _name; }

    public String getNameWithStn() { return _nameWithStn; }

    public String getUnits() { return _units; }

    public float getMin() { return _plotMin; }

    public float getMax() { return _plotMax; }

    public boolean getDynamic()	{ return _dynamic; }

    public boolean getDisplay()	{ return _display; }

}
