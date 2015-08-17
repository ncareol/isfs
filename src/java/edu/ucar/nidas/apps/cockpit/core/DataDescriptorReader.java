package edu.ucar.nidas.apps.cockpit.core;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.w3c.dom.Document;

import edu.ucar.nidas.apps.cockpit.model.DataDescriptor;
import edu.ucar.nidas.core.SocketXmlReader;

/**
 * 
 * Class DataDescripterReader gets the data-descriptor-xml via a tcp-socket
 * 
 * @author dongl
 *
 */
public class DataDescriptorReader extends SocketXmlReader {

   /**
    * TCP Socket to read the data-descriptor.
    */
    private Socket _sock;
    
    
    /**
     * get the TcpSocket which helped retrieve the data-descriptor-xml
     * the tcpsocket is kept in the cockpit for the cockpit to determine whether the server is alive or not  
     * 
     * @return Socket
     */
    public Socket getTcpSocket() {
        return _sock;
    }
      
    
    /**
     * to read the data-descriptor from the server, and parse it to a Document object 
     * 
     * establishes the connection to the chosen server via tcp-socket, sends a packet based on the
     * nidas-server protocol to read the data-descriptor-xml, and parse the xml-input stream into a xml document. 
     * 
     * @param addr
     * @param tcpPort
     * @param rcvSckLocPort
     * @return Document
     * 
     * @throws IOException
     */
    public Document readXml(InetAddress addr, int tcpPort, int rcvSckLocPort) throws  IOException {
        _sock = super.connect(addr, tcpPort, rcvSckLocPort);
        _sock.setSoTimeout(10000);
        XMLInputStream xmlIS = new XMLInputStream( _sock.getInputStream());
        _sock.setSoTimeout(0); //keep it alive
        if (xmlIS!=null) {
            DataDescriptor cpxml = new DataDescriptor(xmlIS);
            return cpxml.getDoc();
        }

        return null;
    }
    
}
