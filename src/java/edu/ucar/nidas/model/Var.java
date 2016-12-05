package edu.ucar.nidas.model;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Comparator;

/**
 *  This class contains a variable parameters in the data-descriptor
 *  
 *  var-name
 *  var-unit
 *  var-len
 *  
 * @author dongl
 *
 */
public class Var {

    /**
     * regex for getting height:
     */
    static Pattern htregex = Pattern.compile("\\.([0-9]+]\\.?[0-9]*)(c?m)");

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
        if (matcher.matches() && matcher.groupCount() == 2) {
            try {
                val = Float.parseFloat(matcher.group(1));
                if (matcher.group(2) == "cm") {
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
                // return new Float(v1.getHeight()).compareTo(new Float(v2.getHeight()));
                return Float.compare(v1.getHeight(),v2.getHeight());
            }
        };

    public static final Comparator<Var> NAME_ORDER = 
        new Comparator<Var>() {
            public int compare(Var v1, Var v2) {
                return v1.getNameWithStn().compareTo(v2.getNameWithStn());
            }
        };

    public void setUnits(String val) {_units=val; }

    public void setMin(float val) {_plotMin=val; }

    public void setMax(float val) {_plotMax=val; }

    public void setDynamic(boolean dyn)	{_dynamic=dyn; }

    public void setDisplay(boolean disp) {_display=disp; }
    
    /**
     * set the data-length of the variable. 
     * Most time, it has one measurement per variable; but it
     * can has more than one.
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
