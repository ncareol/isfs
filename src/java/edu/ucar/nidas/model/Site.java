package edu.ucar.nidas.model;

import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
public class Site extends BasicDom{

    public static int _totalNum =0;
    String  _name;
    int     _number;
    ArrayList<Dsm>  _dsms = new ArrayList<Dsm>();

    public void setNumber(int num)           {_number=num; }
    public void setName(String name)    {_name= name;}
 
    public int getNumber()      {return _number;}
    public String getName() {return _name;}
    public ArrayList<Dsm> getDsms(){  return _dsms; }

 
    public Site(Node siteNode) {
        String name =getValue(siteNode, "name");
        if (name== null || name.length()<1) return;
        _name =getValue(siteNode, "name");
        
        String number =getValue(siteNode, "number");
        if (number== null || number.length()<1) _number= (++_totalNum);
        else _number = (Integer.valueOf(number).intValue());
        walkDsms(siteNode);
    }
    
    /**
     * Walk through the data-descriptor-xml to get dsm, sensor, sample, and variable information
     */
    public void  walkDsms(Node siteNode) {
       NodeList nl = siteNode.getChildNodes();

       // get all dsms in a site
        for (int i=0; i<nl.getLength();i++){
            Node n= nl.item(i);
            if (n==null) return;
            String val =getValue(n, "id");
            if (val== null || val.length()<1) continue; 
            Dsm dsm= new Dsm();
            dsm.setId(Integer.valueOf(val).intValue());
            dsm.setName(getValue(n, "name"));
            dsm.setLoc(getValue(n, "location"));
            dsm.walkSamples(n, _number);
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
                Util.prtDbg("Var "+ v.getOffset()+ "  name= "+v.getName()+ "  pid= "+ "  dsmid= "+(Integer.valueOf(v.getSampleId())>>16)+ " _gid= "+v.getSampleId()+ " min="+v.getMin()+ " max="+v.getMax());
            }
        }
    }





} //eof Site


