package edu.ucar.nidas.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class contains the sample's parameters
 *  sample-id
 *  a list of Variables
 * 
 * @author dongl
 *
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
    public void setId(int val) {
	_id=val;
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
 
    /**
     * Need to save site in order to implement this method.
	
    public void addVar(Var v)
    {
        _vars.add(v);
        site.addVariable(v);
    }
    */

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
    public void walkVars(Node sampNode, Site site)
    {
        NodeList nl = sampNode.getChildNodes();

        int offset = 0;
        for (int i=0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n==null) return;
       
            //get variable attrs
            NamedNodeMap attrs = n.getAttributes();

            Attr vnattr = (Attr)attrs.getNamedItem("name");
            if (vnattr == null) return;
            String varname = vnattr.getValue();

            Var v = site.getVariable(varname);
            int length = 1;

            if (v == null) {

                v = new Var(site, varname);

                String units = "";
                String range = "";
                boolean dyn= false, disp =true;
                for (int j=0; j< attrs.getLength(); j++){
                    Attr attr = (Attr)attrs.item(j);
                    if ("units".equals(attr.getName())) 
                        units = attr.getValue();
                    if ("plotrange".equals(attr.getName())) 
                        range = attr.getValue();
                    if ("length".equals(attr.getName())) 
                        length = Integer.valueOf(attr.getValue());
                    if ("dynamic".equals(attr.getName())) 
                        dyn = Boolean.valueOf(attr.getValue());
                    if ("display".equals(attr.getName())) 
                        disp = Boolean.valueOf(attr.getValue());
                }
            
                v.setUnits(units);
                v.setLength(length);
                if (dyn) v.setDynamic(dyn);
                if (!disp) v.setDisplay(disp);
            
                if (range ==null || range.length()<1) {
                    v.setMin(-10);
                    v.setMax(10);
                } else {
                    float min = Float.valueOf(range.split(" ")[0].trim());
                    float max = Float.valueOf(range.split(" ")[1].trim());
                    v.setMin(Float.valueOf(range.split(" ")[0].trim()));
                    v.setMax(Float.valueOf(range.split(" ")[1].trim()));
                }
                site.add(v);
            }
            _offsets.put(v, offset);
            offset += v.getLength();
            _vars.add(v);
        }
    }
}
