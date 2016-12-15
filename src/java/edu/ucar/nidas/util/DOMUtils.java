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
   
    /** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public static Document parseXml(String filename, boolean validating)
        throws IOException, ParserConfigurationException, SAXException
    {
        return parseXML(new FileInputStream(filename), validating);
    }
    
    /** Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     * @param finename -   xml doc file to parse
     * @param validating - boolean to confirm validation against DTD file  
     */
    public static Document parseXML(InputStream is, boolean validating)
        throws IOException, ParserConfigurationException, SAXException
    {
        // Create a builder factory
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(validating);
        // Create the builder and parse the file
        Document doc = factory.newDocumentBuilder().parse(is); 
        return doc;
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
        throws FileNotFoundException, IOException
    {
        Properties props = OutputPropertiesFactory.getDefaultMethodProperties(Method.XML);
        Serializer ser = SerializerFactory.getSerializer(props);
     
        FileOutputStream fout= new FileOutputStream(new File(fn));
        ser.setOutputStream( fout);
        DOMSerializer dser = ser.asDOMSerializer(); // a DOM will be serialized
        dser.serialize(doc); // serialize the DOM, sending output to owriter
        fout.close();
   }
    
    public static String getValue(Node n, String attr)
    {
        Node nn = n.getAttributes().getNamedItem(attr);
        if (nn == null) return null;
        return nn.getNodeValue();
    }

}
