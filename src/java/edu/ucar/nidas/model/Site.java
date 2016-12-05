package edu.ucar.nidas.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.ucar.nidas.util.DOMUtils;
import edu.ucar.nidas.util.Util;

/**
 * 
 * Dsm class a dsm's parameters in the data-descriptor 
 *  dsm-name
 *  dsm-id
 *  dsm-location
 *  samples-in-the dsm
 * 
 * @author dongl
 *
 */
public class Site {

    public static int _totalNum =0;
    String  _name;
    int     _number;
    ArrayList<Dsm>  _dsms = new ArrayList<Dsm>();

    HashMap<String,Var> _variables = new HashMap<String,Var>();

    public void setNumber(int num) { _number = num; }

    public void setName(String name) { _name = name; }
 
    public int getNumber()      {return _number;}
    public String getName() {return _name;}
    public ArrayList<Dsm> getDsms(){  return _dsms; }
 
    public Site(Node siteNode)
    {
        String name = DOMUtils.getValue(siteNode, "name");
        if (name== null || name.length()<1) return;
        _name = DOMUtils.getValue(siteNode, "name");
        
        String number = DOMUtils.getValue(siteNode, "number");
        if (number== null || number.length()<1) _number= (++_totalNum);
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
    public void  walkDsms(Node siteNode) {
        NodeList nl = siteNode.getChildNodes();

        // get all dsms in a site
        for (int i = 0; i < nl.getLength();i++){
            Node n= nl.item(i);
            if (n==null) return;
            String val = DOMUtils.getValue(n, "id");
            if (val == null || val.length() < 1) continue; 
            Dsm dsm = new Dsm(this);
            dsm.setId(Integer.valueOf(val).intValue());
            dsm.setName(DOMUtils.getValue(n, "name"));
            dsm.setLocation(DOMUtils.getValue(n, "location"));
            dsm.walkSamples(n, this);
            //printDsm(dsm);
            _dsms.add(dsm);
        }
    }

    /**
     * Print all dsms in the site
     */
    public void printAll() {
        Util.prtDbg("Site = "+ _name);
        for (int i=0; i<_dsms.size();i++) {
            Dsm dsm = _dsms.get(i);
            printDsm(dsm);
        }
    }

    /**
     * Print a dsm's information
     * @param dsm
     */
    private void printDsm(Dsm dsm) {
        Util.prtDbg("Dsm "+dsm.getId()+ "  name= "+dsm.getName());
        ArrayList<Sample> samples = dsm.getSamples();

        //Util.prtDbg("Print out all _samples:");
        for (int j=0; j<samples.size();j++) {
            Sample s = samples.get(j);
            Util.prtDbg("Sample "+ s.getId());

            ArrayList<Var>  vars= s.getVars();
            Util.prtDbg("Print out all _vars:");
            for (int k=0; k<vars.size();k++) {
                Var v = vars.get(k);
                Util.prtDbg("Var "+ s.getOffset(v)+ "  name= "+v.getName()+ "  pid= "+ "  dsmid= "+(Integer.valueOf(s.getId())>>16)+ " _gid= "+s.getId()+ " min="+v.getMin()+ " max="+v.getMax());
            }
        }
    }

    public static ArrayList<Site> parse(Document doc)
    {
        ArrayList<Site> sites = new ArrayList<Site>();
        NodeList nl = doc.getElementsByTagName("site");
        for (int i=0; i<nl.getLength();i++){
            Node n= nl.item(i);
            if (n==null) continue;
            Site site = new Site(n);
            sites.add(site);
        }
	return sites;
    }

} //eof Site


