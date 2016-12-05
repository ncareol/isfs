package edu.ucar.nidas.model;

import java.util.ArrayList;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
public class Dsm {

    String _name;

    String _location;

    Site _site;

    int  	_id;

    ArrayList<Sample>  _samples = new ArrayList<Sample>();

    public Dsm(Site site)
    {
    }

    public void setId(int val) {_id = val; }
    public void setName(String val) {_name = val; }
    public void setLocation(String val) { _location = val; }

    public int getId() { return _id; }
    public String getName() { return _name; }
    public String getLocation() { return _location; }

    public Site getSite() { return _site; }

    public ArrayList<Sample> getSamples()
    {
        return _samples;
    }

    public void walkSamples(Node dsmNode, Site site) {
        NodeList nl = dsmNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n= nl.item(i);
            if (n==null) return;
            //get sample attribute
            NamedNodeMap attrs = n.getAttributes();
            int sampId = 0;
            for (int j=0; j< attrs.getLength(); j++){
                Attr attr = (Attr)attrs.item(j);
                if ("id".equals(attr.getName())) 
                    sampId = Integer.valueOf(attr.getValue());
            }
            Sample s = new Sample(this);
            s.setId(sampId);
            //System.out.println("sample_gid="+ val);
            s.walkVars(n, site); 
            _samples.add(s);
        }
    }
}
