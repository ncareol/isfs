package edu.ucar.nidas.apps.cockpit.model.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.trolltech.qt.core.QDir;
import com.trolltech.qt.gui.QFileDialog;
import com.trolltech.qt.gui.QWidget;

import edu.ucar.nidas.apps.cockpit.model.XmlDom;
import edu.ucar.nidas.apps.cockpit.ui.CentTabWidget;
import edu.ucar.nidas.apps.cockpit.ui.GaugePage;
import edu.ucar.nidas.model.BasicDom;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;


/**
 * In this class: it will provides the methods to save and retrieve project's
 * display parameters and its sub-objects's display parameters
 * It saves the data in xml formats.   
 * @author dongl
 */
public class UserConfig extends BasicDom {

    private CentTabWidget _parent;
    public static String _configName = "cockpitUserConfig.conf";
    private CockpitConfig _cconf;

    public UserConfig () { 
        //openFile(_pe)
    }

   
    public void createCockpitConfig(CentTabWidget p) {
        _parent = p; 
        if (_parent==null) {
            Util.prtErr("Cannot createCockpitconfig: parent is null");
            return;
        }
 
        //openFile;
        getSaveFileName();
        if (_configName==null || _configName.length()<=0){
            return;
        }
           
        _cconf = new CockpitConfig(_parent);
    }

    public String writeUserConfig() {
        if (_cconf==null ) return null;
        if (_configName == null || _configName.length()<=0) return null;

        try {
            DocumentBuilderFactory documentBuilderFactory = 
                DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = 
                documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            _cconf.writeCockpitConfig(document);

            super.saveXmldoc(document, _configName);
        } catch (ParserConfigurationException pex) {
            return null;
        }
        // printContent("Save-config ");
        return _configName;

    }

    public void readUserConfig(boolean openfile) {
        _cconf = null;
        if (openfile) getOpenFileName();
        if (_configName==null || _configName.length()<=0){
            return;
        }
        
        Document doc=parseXmlFile(_configName,false );
        if (doc==null) return;
        Node n = doc.getFirstChild(); //there is only one

        if (n==null) return;
        _cconf= new CockpitConfig();
        _cconf.walkCockpitConfig(n);
        //printContent("Open-config ");

    }

    public CockpitConfig getCockpitConfig() {
        return _cconf;
    }

    public void printContent(String msg){
        if (_cconf == null) return;
        //CockpitConfig pj = _cconf;
        System.out.println(msg+_cconf._name+" =");
        List<TabPageConfig> tps = _cconf.getTabPageConfig();
        for (int i=0; i<tps.size(); i++){
            TabPageConfig tp = tps.get(i);
            System.out.println(msg+"tpage="+tp.getName());
            List<PlotConfig> uivars = tp.getUIVars();
            for (int j=0; j< uivars.size(); j++){
                PlotConfig vui = uivars.get(j);
                if (j==0){
                    System.out.println(msg+"varui j="+j+" "+vui.getName()+" "+vui.getMax()+" "+vui.getMin()+" "+vui.getNoDataTm()+" "+vui.getplotTmRange() );
                    System.out.println("colors= "+vui.getCColor()+" "+vui.getHColor()+ " "+vui.getBGColor());
                }
            }
        }
    }

      
    public void  setConfigName(String nm) {
        _configName = nm;
    }
    
    private void getOpenFileName() {
        _configName = QFileDialog.getOpenFileName((QWidget)_parent, "Open File", QDir.currentPath()+"/"+_configName);
        if (_configName==null || _configName.length()<=0){ 
            //if (_parent!=null) _parent.getParent().statusBar().showMessage("No configure file is selected.", 10000); //10 sec
        }
    }
    
    private void getSaveFileName() {
        _configName = QFileDialog.getSaveFileName((QWidget)_parent, "Save File", QDir.currentPath()+"/"+_configName);
        if (_configName==null || _configName.length()<=0){
            _parent.getParent().statusBar().showMessage("Save user configure file is NOT selected.", 10000); //10 sec
          //  Util.prtErr("Save user configure file is NOT selected.");
        }
    }
   
   
}
