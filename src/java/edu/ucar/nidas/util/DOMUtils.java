// -*- mode: java; indent-tabs-mode: nil; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
 ** ISFS: NCAR Integrated Surface Flux System software
 **
 ** 2016, Copyright University Corporation for Atmospheric Research
 **
 ** This program is free software; you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation; either version 2 of the License, or
 ** (at your option) any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** The LICENSE.txt file accompanying this software contains
 ** a copy of the GNU General Public License. If it is not found,
 ** write to the Free Software Foundation, Inc.,
 ** 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **
 ********************************************************************
*/

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

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource; 
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;

import org.xml.sax.SAXException;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
     * @param fileName -  The file name to save the xml items 
     */
    public static void writeXML(Document doc, String fileName)
        throws FileNotFoundException, IOException
    {

	try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    if (doc.getDoctype() != null) {
		String systemValue = (new File (doc.getDoctype().getSystemId())).getName();
		transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemValue);
	    }

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new FileOutputStream(new File(fileName)));
            transformer.transform(source, result);

        } 
        catch (TransformerConfigurationException tce) {
            System.out.println("* Transformer Factory error");
            System.out.println(" " + tce.getMessage());

            Throwable x = tce;
            if (tce.getException() != null)
                x = tce.getException();
            x.printStackTrace(); 
        } 
        catch (TransformerException te) {
            System.out.println("* Transformation error");
            System.out.println(" " + te.getMessage());

            Throwable x = te;
            if (te.getException() != null)
                x = te.getException();
            x.printStackTrace();
        } 

    }
    
    public static String getAttribute(Node n, String attr)
    {
        Node nn = n.getAttributes().getNamedItem(attr);
        if (nn == null) return null;
        return nn.getNodeValue();
    }

}
