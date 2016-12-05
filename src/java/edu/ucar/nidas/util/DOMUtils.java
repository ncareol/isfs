package edu.ucar.nidas.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
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

public class DOMUtils {
   
    public static String lineFeed = "\n";


    /** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public static Document parseXml(String filename, boolean validating)
        throws FileNotFoundException, ParserConfigurationException
    {
        return parseXML(new FileInputStream(filename), validating);
    }
    
    /** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public static Document parseXML(InputStream is, boolean validating) {
    	
        try {
            // Create a builder factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(validating);

            // Create the builder and parse the file
            Document doc = factory.newDocumentBuilder().parse(is); 
            return doc;
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

    public static Document newDocument() throws ParserConfigurationException
    {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        return docBuilder.newDocument();
    }
    
    /**
     * Save a xml parser document to a file
     * @param doc -    The document that contains the xml items 
     * @param fn -  The file name to save the xml items 
     */
    public static void writeXML(Document doc, String fn)
    {
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
    
    public static Node getNode(NodeList nl, String tagName)
    {
        for ( int i=0; i<nl.getLength(); i++){
            String n= nl.item(i).getNodeName();
            if (n.equals(tagName)) {
                return nl.item(i);
            }
        }
        return null;
    }

    public static Node getNode(NodeList nl, String attr, String attrVal)
    {
        if (attr==null) return nl.item(nl.getLength()-1);//return last one, if multiple
        for ( int i=0; i<nl.getLength(); i++){
            Node n= nl.item(i).getAttributes().getNamedItem(attr);
            if (n != null) {
                if (n.getNodeValue().equals(attrVal))
                    return nl.item(i);
            }
        }
        return null;
    }

    public static Node getNode(Document doc, String tagName, String attr, String attrVal)
    {
        NodeList nl = doc.getElementsByTagName(tagName);
        return getNode(nl, attr, attrVal);
    }


    public static String getValue(Document doc, String tagName, String hasAttr,
            String hasAttrVal,  String attr)
    {
        Node n = getNode(doc, tagName, hasAttr, hasAttrVal);
        if (n==null) return null;
        Node nn = n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }

    public static String getValue(Node n, String attr) {
        Node nn=n.getAttributes().getNamedItem(attr);
        if (nn==null) return null;
        return nn.getNodeValue();
    }

    public static void setAttribute(Document doc, String tagName,
            String hasAttr, String hasAttrVal,String attr, String attrVal)
    {
        Node at= getNode(doc, tagName, hasAttr, hasAttrVal);
        if (at ==null) {
            Util.prtErr("Reset attribute failed.\n attr= "+ attr+ " attrVal= "+ attrVal );
            return;
        }
        Node nn = at.getAttributes().getNamedItem(attr);
        if (nn==null) return;
        nn.setNodeValue(attrVal);
    }

    public static void addAttribute(Document doc, String tagName, String hasAttr,
            String hasAttrVal, String attr, String attrVal) {

        Node n= getNode(doc, tagName, hasAttr, hasAttrVal);
        if (n==null) {
            Util.prtErr("Cannot find the node.addAttribute failed. "+ hasAttr+ "  "+ hasAttrVal);
            return;
        }

        //create a new attr
        Attr a = doc.createAttribute(attr);
        a.setValue(attrVal);
        n.getAttributes().setNamedItem(a);

    }


    public static void removeAttribute(Document doc, String tagName,
        String hasAttr, String hasAttrVal, String attr)
    {
        Node at= getNode(doc, tagName, hasAttr, hasAttrVal);
        if (at ==null) {
            Util.prtErr("Remove attribute failed.\n attr= "+ hasAttr+ " attrVal= "+ hasAttrVal );
            return;
        }
        try {
            at.getAttributes().removeNamedItem(attr);
        } catch (Exception e) {}

    }

    public static void addNode(Document doc, String tagName, String hasAttr,
            String hasAttrVal, String nodeTag, String nameVal, String nodeText) throws DOMException
    {
        Node newnn = doc.createElement(tagName);
        if (hasAttr!=null) {
            Attr newaa = doc.createAttribute(hasAttr);
            if (hasAttrVal!=null) newaa.setValue(hasAttrVal);
            newnn.getAttributes().setNamedItem(newaa);
        }
        newnn.setTextContent(lineFeed);
        doc.appendChild(newnn);

        //check if node exists
        Node nn= getNode(doc, nodeTag, "name", nameVal);
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
        Node n= getNode(doc, tagName, hasAttr, hasAttrVal);

        Node newn = doc.createElement(nodeTag);
        newn.setTextContent(lineFeed);

        //add new attr
        Attr newa = doc.createAttribute("name");
        if (nameVal!=null) newa.setValue(nameVal);

        //add attr to node & add node to parent
        newn.getAttributes().setNamedItem(newa);

        if (n==null) 
            doc.appendChild(newn);
        else 
            n.appendChild(newn);

    }

    public static void addNode(Document doc, String tagName, String hasAttr, String hasVal,
            String nodeTag, ArrayList<String> attrList ) throws DOMException
    {

        //create a new node
        Node newnode = doc.createElement(nodeTag);

        //add all attrs
        for (int i=0; i<attrList.size();i++) {
            String attr = attrList.get(i);
            //create new attr
            Attr newattr = doc.createAttribute(attr.split(" ")[0]);
            assert(attr.split(" ").length==2);
            newattr.setValue(attr.split(" ")[1]);
            //add attr to node 
            newnode.getAttributes().setNamedItem(newattr);
        }

        //add line feed
        newnode.setTextContent(lineFeed);

        //get p-node
        NodeList nl = doc.getElementsByTagName(tagName);
        if (nl==null || nl.getLength()<=0) {
            Util.prtDbg("No node named "+ tagName+ " exists. ");
            return; 
        }

        Node pnode;

        if (hasAttr==  null || hasVal==null)
            pnode= nl.item(nl.getLength()-1); //if multiple, take the last one
        else 
            pnode= getNode(nl, hasAttr, hasVal);

        //attach new node to parent
        if (pnode==null) {
            doc.appendChild(newnode);
        } else { 
            pnode.appendChild(newnode);
        }

    }


    public static void removeNode(Document doc, String tagName,
            String hasAttr, String hasAttrVal, String nodeTag)
    {
        Node p= getNode(doc, tagName, hasAttr, hasAttrVal);
        if (p==null) {
            Util.prtErr("Cannot find the node.removeNode failed. "+ hasAttr+ "  "+ hasAttrVal);
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
