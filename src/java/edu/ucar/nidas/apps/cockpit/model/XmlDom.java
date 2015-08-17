package edu.ucar.nidas.apps.cockpit.model;

import org.w3c.dom.Document;

import com.trolltech.qt.core.QFile;

import edu.ucar.nidas.model.BasicDom;
import edu.ucar.nidas.util.Util;

/**
 * This class is the cockpit-Xml service center. It provides the 
 *  1> parse the data-descriptor from data-feeder
 *  2> add plot-attributes/remove them from the ui-configuration
 *  3> save a config file to a local disk
 *  4> retrieve a config file back to cockpit ui  
 * 
 * @author dongl
 *
 */
public class XmlDom extends BasicDom {

     /**
     * _fno= ui config xml file name, cockpitConfig.xml 
     * _fni= cockpit xml from the server 
     */
    protected String _fni, _fno;
    
   
    /**
     * constructor
     */
    public XmlDom(){}
    public XmlDom(String fi,String fo){
        setfn (fi, fo);
    }

    /**
     * Set the data-descriptor and use configuration file names
     * 
     * @param fni
     * @param fno
     */
    public void setfn (String fni, String fno){
        if (fni!=null)	{_fni= fni; parseXml(fni);}
        if (fno!=null)	{_fno= fno; parseXml(fno);}
    }

    /**
     * Get user config file name
     * @return
     */
    public String getUserConfigXml() {return _fno;}

    /**
     * Get DataDescriptor file name
     * @return
     */
    public String getDataDescriptXml() {return _fni;}

    
    /**
     * Parse xml from the data-descriptor file
     * _fni="cockpit.xml"
     * OR
     * Parse user config xml from UI
     * fno="cockpitConfig.xml"
     * 
     * @return
     */
    public Document parseXml(String fn) {
        if (fn==null) {
            Util.prtErr("Xml file: " + fn +" is empty; Skip parsering...");
            return null;
        }
        _doc= super.parseXmlFile(fn,false);
        return _doc;
    }

     /**
     * Save the document to the data-descriptor xml file
     * @param doc
     */
    public void saveDataDescriptor(Document doc) {

        if (doc==null){
            Util.prtErr("The document is empty, skip savexml...");
            return;
        }
         _doc = doc;

        if (_fni==null) {
            _fni = DataDescriptor.cockpitXml;
        } else {
            QFile qf = new QFile(_fni);
            if (qf.exists()) {
                qf.remove();
            }
        }
        super.saveXmldoc(_doc, _fni);
    }
    
    /**
     * Save the document to the user-config xml file
     * @param doc
     */
    public void saveUserConfig(Document doc) {

        if (doc==null){
            Util.prtErr("The document is empty, skip saveconfigXml...");
            return;
        }
        
        _doc = doc;

        if (_fno==null) {
            _fno= UserConfig.cockpitConfigXml;
        } else {
            QFile qf = new QFile(_fno);
            if (qf.exists()) {
                qf.remove();
            }
        }
        super.saveXmldoc(_doc, _fno);
    }

   
}
