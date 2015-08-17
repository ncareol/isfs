package edu.ucar.nidas.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.serializer.DOMSerializer;
import org.apache.xml.serializer.Method;
import org.apache.xml.serializer.OutputPropertiesFactory;
import org.apache.xml.serializer.Serializer;
import org.apache.xml.serializer.SerializerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucar.nidas.util.Util;

public class BasicDom {
   
    public static String lineFeed = "\n";

    /**
     * xml document object
     */
    protected Document   _doc;
    /**
     * Get the xml document object
     * @return _doc
     */
    public Document getDoc() {return _doc;}

    

	/** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public Document parseXmlFile(String filename, boolean validating) {
        try {
            // Create a builder factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(validating);

            // Create the builder and parse the file
            _doc = factory.newDocumentBuilder().parse(new File(filename));
            return _doc;
        } catch (SAXException e) {
            // A parsing error occurred; the xml input is not valid
        	Util.prtException(e,"A parsing error occurred; the xml input is not valid.\n"+e.getMessage());
        } catch (ParserConfigurationException e) {
        	Util.prtException(e,"A parsing config error occurred; the xml parsing failed.\n"+e.getMessage());
        } catch (IOException e) {
        	Util.prtException(e,"An i/o exception error occurred; the xml parsing failed.\n"+e.getMessage());
        }
        return null;
    }
    
    /** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public Document parseXmlFile(InputStream is, boolean validating) {
    	
        try {
            // Create a builder factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(validating);

            // Create the builder and parse the file
            _doc = factory.newDocumentBuilder().parse(is); 
            return _doc;
        } catch (SAXException e) {
            // A parsing error occurred; the xml input is not valid
        	Util.prtException(e, "A parsing error occurred; the inputStream is not valid.\n"+is.toString()+ "\n"+e.getMessage());
        } catch (ParserConfigurationException e) {
        	Util.prtException(e,"A parsing config error occurred; the inputStream is not valid.\n"+is.toString()+e.getMessage());
        } catch (IOException e) {
        	Util.prtException(e,"An i/o exception error occurred; the inputStream is not valid.\n"+is.toString()+e.getMessage());
        }
        return null;
    }
    
    
    /**
     * Save a xml parser document to a file
     * @param doc -    The document that contains the xml items 
     * @param fn -  The file name to save the xml items 
     */
    public void saveXmldoc(Document doc, String fn) {
		//save all to the file
	    Properties props = OutputPropertiesFactory.getDefaultMethodProperties(Method.XML);
	    Serializer ser = SerializerFactory.getSerializer(props);
	 
	    try {
	    	
	       	FileOutputStream fout= new FileOutputStream(new File(fn));
	    	ser.setOutputStream( fout);
	       	DOMSerializer dser = ser.asDOMSerializer(); // a DOM will be serialized
	    	dser.serialize(doc); 						// serialize the DOM, sending output to owriter
	    	fout.close();
		    ser.reset(); 								// get ready to use the serializer for another document
	    } catch (    FileNotFoundException e) {
	    	Util.prtException(e,"Cannot create file writer to "+fn +", config is not saved.\n"+e.getMessage());
	    } catch (IOException e) {
	    	Util.prtException(e,"Encount IO exception, config is not saved.\n"+ e.getMessage());	  	  
	    } finally {
		    ser.reset(); 
	    }
   }
	
    
    
    
    
    
    public Node getNode(NodeList nl, String tagName) {
        if (nl==null) return null;

        if (nl.getLength()==1) return nl.item(0);
        for ( int i=0; i<nl.getLength(); i++){
            String n= nl.item(i).getNodeName();
            if (n.equals(tagName)) {
                return nl.item(i);
            }
        }
        return null;
    }

    public Node getNode(NodeList nl, String attr, String attrVal) {
        if (nl==null) return null;

        if (nl.getLength()==1 ) return nl.item(0);
        if (attr==null) return nl.item(nl.getLength()-1);//return last one, if multiple
        for ( int i=0; i<nl.getLength(); i++){
            Node n= nl.item(i).getAttributes().getNamedItem(attr);
            if (n!=null) {
                if (n.getNodeValue().equals(attrVal))
                    return nl.item(i);
            }
        }
        return null;
    }

    public Node getNode(String tagName, String attr, String attrVal) {
        if (_doc==null) {
            Util.prtErr("Doc is mepty. Cannot retrieve a node...");
            return null;
        }
        NodeList nl = _doc.getElementsByTagName(tagName);
        return getNode(nl, attr, attrVal);
    }


    public String getValue(String tagName, String hintAttr, String hintAttrVal,  String attr) {
        Node n = getNode(tagName, hintAttr, hintAttrVal);
        if (n==null) return null;
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }

    public String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }

    public void setAttribute(String tagName, String hintAttr, String hintAttrVal,String attr, String attrVal) {
        Node at= getNode(tagName, hintAttr, hintAttrVal);
        if (at ==null) {
            Util.prtErr("Reset attribute failed.\n attr= "+ attr+ " attrVal= "+ attrVal );
            return;
        }
        Node nn=at.getAttributes().getNamedItem(attr);
        if (nn==null) return;
        nn.setNodeValue(attrVal);
    }

    public void addAttribute(String tagName, String hintAttr, String hintAttrVal, String attr, String attrVal) {
        if (_doc==null) {
            Util.prtErr("Doc is mepty...");
            return ;
        }

        Node n= getNode(tagName, hintAttr, hintAttrVal);
        if (n==null) {
            Util.prtErr("Cannot find the node.addAttribute failed. "+ hintAttr+ "  "+ hintAttrVal);
            return;
        }

        //create a new attr
        Attr a = _doc.createAttribute(attr);
        a.setValue(attrVal);
        n.getAttributes().setNamedItem(a);

    }


    public void removeAttribute(String tagName, String hintAttr, String hintAttrVal, String attr) {
        Node at= getNode(tagName, hintAttr, hintAttrVal);
        if (at ==null) {
            Util.prtErr("Remove attribute failed.\n attr= "+ hintAttr+ " attrVal= "+ hintAttrVal );
            return;
        }
        try {
            at.getAttributes().removeNamedItem(attr);
        } catch (Exception e) {}

    }

    public void addNode(String tagName, String hintAttr, String hintAttrVal, String nodeTag, String nameVal, String nodeText )throws DOMException {
        if (_doc==null) {
            try {
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                _doc = docBuilder.newDocument();
                Node newnn = _doc.createElement(tagName);
                if (hintAttr!=null) {
                    Attr newaa = _doc.createAttribute(hintAttr);
                    if (hintAttrVal!=null) newaa.setValue(hintAttrVal);
                    newnn.getAttributes().setNamedItem(newaa);
                }
                newnn.setTextContent(lineFeed);
                _doc.appendChild(newnn);
            } catch (Exception e ) {
                Util.prtDbg("_doc is empty; addNode failed...");
            }
        }

        //check if node exists
        Node nn= getNode(nodeTag, "name", nameVal);
        if (nn!=null) {
            Util.prtErr(" There is a duplicated node exists.\n Node_tag="+nodeTag+ "\n name_id= "+nameVal+"\n Get a new name_id?");
            //String inputValue = JOptionPane.showInputDialog("Please input a new name_id"); 
            //if (inputValue==null || inputValue.length()<1){
            //  Util.prtDbg("\n A duplicated node is created...\n Node_tag="+nodeTag+ " name_id= "+nameVal);
            //}
            //else {
            //  nameVal=inputValue;
            //}
        }

        //get p-node
        Node n= getNode(tagName, hintAttr, hintAttrVal);

        Node newn = _doc.createElement(nodeTag);
        newn.setTextContent(lineFeed);

        //add new attr
        Attr newa = _doc.createAttribute("name");
        if (nameVal!=null) newa.setValue(nameVal);

        //add attr to node & add node to parent
        newn.getAttributes().setNamedItem(newa);

        if (n==null) 
            _doc.appendChild(newn);
        else 
            n.appendChild(newn);

    }


    public void addNode(String tagName, String hintAttr, String hintVal, String nodeTag, ArrayList<String> attrList ) throws DOMException {
        if (_doc==null) {
            Util.prtDbg("_doc is empty; addNode failed...");
            return;
        }

        //create a new node
        Node newnode = _doc.createElement(nodeTag);

        //add all attrs
        for (int i=0; i<attrList.size();i++) {
            String attr = attrList.get(i);
            //create new attr
            Attr newattr = _doc.createAttribute(attr.split(" ")[0]);
            assert(attr.split(" ").length==2);
            newattr.setValue(attr.split(" ")[1]);
            //add attr to node 
            newnode.getAttributes().setNamedItem(newattr);
        }

        //add line feed
        newnode.setTextContent(lineFeed);

        //get p-node
        NodeList nl = _doc.getElementsByTagName(tagName);
        if (nl==null || nl.getLength()<=0) {
            Util.prtDbg("No node named "+ tagName+ " exists. ");
            return; 
        }

        Node pnode;

        if (hintAttr==  null || hintVal==null)
            pnode= nl.item(nl.getLength()-1); //if multiple, take the last one
        else 
            pnode= getNode(nl, hintAttr, hintVal);

        //attach new node to parent
        if (pnode==null) {
            _doc.appendChild(newnode);
        } else { 
            pnode.appendChild(newnode);
        }

    }


    public void removeNode(String tagName, String hintAttr, String hintAttrVal, String nodeTag){
        Node p= getNode(tagName, hintAttr, hintAttrVal);
        if (p==null) {
            Util.prtErr("Cannot find the node.removeNode failed. "+ hintAttr+ "  "+ hintAttrVal);
            return;
        }

        Node n=null;
        NodeList nl= p.getChildNodes();
        for (int i=0; i< nl.getLength(); i++){
            n=nl.item(i);
            if (n.getNodeName().equals(nodeTag)) {
                p.removeChild(n);
                return;
            }
        }

    }
	
}