package edu.ucar.nidas.apps.cockpit.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.ucar.nidas.model.Dsm;
import edu.ucar.nidas.model.Site;
import edu.ucar.nidas.util.Util;

//import com.sun.xml.internal.txw2.Document;

/**
 * take the cockpit xml from a server, and parser it to obtain
 * a set of Dsm, sensor, sample, and vars
 * @author dongl
 *
 */
public class DataDescriptor extends XmlDom {

    /**
     * Cockpit xml filename, if saved in the local disk
     */
    public static String cockpitXml="cockpit.xml";

    /**
     * All Sites' and their offer-springs' parameters
     */
    private ArrayList<Site> _sites = new ArrayList<Site>(0);




    /**
     * The constructor that parses the data-descriptor-xml from a Input-Stream 
     * @param is
     */
    public  DataDescriptor(InputStream is){
        _doc=parseXmlFile(is, false);
        try {saveXmldoc(_doc, "cockpit.xml");} catch (Exception e) {Util.prtException(e, "Cannot save cockpit xml file");}
   }

    /**
     * The constructor that receives a xml document as the input
     * @param doc
     */
    public  DataDescriptor(Document doc){
        _doc=doc;
        try {saveXmldoc(_doc, "cockpit.xml");} catch (Exception e) {Util.prtException(e, "Cannot save cockpit xml file");}
    }


    /**
     * Get the array of the DSMs that contain data-descriptors' information
     * @return
     */
    public  ArrayList<Site> getSites() {return _sites;} 
    
    public ArrayList<Dsm> getAllDsms() {
        ArrayList<Dsm> dsms= new ArrayList<Dsm>();
        for (int i=0; i<_sites.size();i++) {
            Site site= _sites.get(i);
            ArrayList<Dsm> ss = site.getDsms();
            if (ss !=null && ss.size()>0) dsms.addAll(site.getDsms());
        }
        return dsms;
    }
    


    /**
     * Walk through the data-descriptor-xml to get site, dsm, sensor, sample, and variable information
     */
    public void  walkSites() {
        if (_doc==null ) {
            Util.prtDbg("Xml document is null");
            return ;
        }

        NodeList nl = _doc.getElementsByTagName("site");
        for (int i=0; i<nl.getLength();i++){
            Node n= nl.item(i);
            if (n==null) continue;
            Site site = new Site(n);
            _sites.add(site);
        }
        
        printAll();
    }


    /**
     * Print all data-descriptors' information
     */
    public void printAll() {
        Util.prtDbg("Print out all data descriptors:");
        for (int i=0; i<_sites.size();i++) {
            Site site= _sites.get(i);
            site.printAll();
        }
    }

    
    /**
     * Parse the data-descriptor configuration from the input-stream to a xml-document, and save it to a local file
     * @param is
     * @return the xml file name
     * @throws IOException
     */
    public String saveStreamXml (InputStream is) throws IOException {

        Document doc=parseXmlFile(is,false );
        if (doc==null) return null;

        saveXmldoc(doc, cockpitXml);
        Util.prtDbg(" Plot configure xml is saved...");
        return cockpitXml;
    }	



}//class
