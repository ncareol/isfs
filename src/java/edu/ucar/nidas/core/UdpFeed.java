package edu.ucar.nidas.core;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import org.w3c.dom.Document;

import edu.ucar.nidas.apps.cockpit.core.DataDescriptorReader;
import edu.ucar.nidas.util.Util;


/**
 * This class contains all the objects cockpit needs.
 * It contains 
 *  udpSocket to get data
 *  xml doc to get data descriptor
 *  and tcpSocket to keep server alive
 * 
 * 
 * @author dongl
 *
 */
public class UdpFeed {

    DatagramSocket _dsocket;
    Socket _tcpsocket;
    Document _doc;

    /**
     * a copy of original udp-feed-finder and inf
     */
    UdpFeedFinder _udp; UdpFeedInf _inf;

    /**
     * constructor:
     * get udp-socket,
     * create the tcp-socket and
     * read data-descriptor's xml 
     * @param udp
     * @param inf
     */
    public UdpFeed( UdpFeedFinder udp, UdpFeedInf inf){
        setDatagramSocket(udp, inf); ///set _dsocket
        _udp = udp;
        _inf = inf;
        _doc = readXml(_inf.getIpAddr(), _inf.getPort(), _udp.getRcvSckLocalPort()); //_tcksocket is signed in the method
    }

  
    /**
     * get the udp-data-socket
     * @return
     */
    public DatagramSocket getUdpSocket() {
        return _dsocket;
    };


    /**
     * get the Tcp socket
     * @return
     */
    public Socket geTcptSocket() {return _tcpsocket;};


    /**
     * get the data-descriptor's xml document
     * @return
     */
    public Document getXmlDoc() {return _doc;};


    /**
     * This method reads data-descriptor xml file from the server that a user selected.
     * It sends a tcp request to retrieve the xml file from the chosen server, and  
     * save the xml configuration to a file.
     * @param: servInf --the server information in a string format - server  port  dsms
     */
    private Document readXml(InetAddress ip, int port, int rcvSckLocPort) {
        if (ip==null ){
            Util.prtErr("Tcp Server is null...");
            return null;
        }
        Util.prtDbg("readXml- serv="+ ip.toString()+ " = "+ip.getHostName() + "  port="+ port);

        try {
            DataDescriptorReader xmlRder = new DataDescriptorReader();
            Document varDoc = xmlRder.readXml(ip, port, rcvSckLocPort);
            _tcpsocket = xmlRder.getTcpSocket();
            _tcpsocket.setKeepAlive(true);
            return varDoc;
        } catch (Exception e ) {
            Util.prtException(e,"cockpit-readXml exception");
            return null;
        }

    }

  
    /**
     * It checks the chosen data-feeder to see if it is a multicast socket, and perform a group-join if needed 
     * @return
     */
    private void setDatagramSocket(UdpFeedFinder udpFeedFinder, UdpFeedInf udpFeedInf){

        // This open of the MulticastSocket should
        //  happen after the user has chosen a server..
        //  In the case of a DatagramSocket connection we will want to
        //  keep it open. When the user chooses a connection, then
        //  we must close the connections not chosen.
        //
        // multicast data, then we need to close, reopen and bind to
        // a different port.

        Util.prtDbg(" udpfeed-joinMulticastGroup--" );
        if (udpFeedFinder==null){
            Util.prtErr(" No data server found... \n Please check data server and retry");
            return ;
        }

        if (udpFeedInf==null){
            Util.prtErr(" No data server found... \n Please check data server and retry");
            return ;
        }

        Util.prtDbg("connect or join-multicastGroup datagram socket ======");
        InetAddress ia= udpFeedFinder.getServAddr();
        _dsocket= udpFeedFinder.getRcvDataSock();

        try { 
            if (!ia.isMulticastAddress()) { //uni-cast
                return ;
            }

            //multiple cast -- rebind  _dsocket
            _dsocket.close();
            MulticastSocket msock = new MulticastSocket(udpFeedInf.getUDPPort());
            _dsocket = msock;

            InetSocketAddress mcSAddr =
                new InetSocketAddress(ia, udpFeedInf.getUDPPort());

            Enumeration<NetworkInterface> ne =
                NetworkInterface.getNetworkInterfaces();
            for ( ; ne.hasMoreElements(); ) {
                NetworkInterface ni = ne.nextElement();
                Util.prtDbg("mcSaddr=" + mcSAddr.toString() +
                        " joining interface=" + ni.toString());

                boolean joinG = false;
                try {

                    Method thd = ni.getClass().getMethod("isUp");
                    Boolean b1=((Boolean)thd.invoke(ni)).booleanValue();
                    Util.prtDbg("isUp b1="+ b1.booleanValue());

                    thd = ni.getClass().getMethod("supportsMulticast");
                    Boolean b2=((Boolean)thd.invoke(ni)).booleanValue();
                    Util.prtDbg("supportsMulticast b2=" +b2.booleanValue());

                    thd = ni.getClass().getMethod("isLoopback");
                    Boolean b3=((Boolean)thd.invoke(ni)).booleanValue();
                    Util.prtDbg("isLoopback b3=" +b3.booleanValue());

                    if (b1.booleanValue() && (b2.booleanValue() || b3.booleanValue()) ) {
                        Util.prtDbg("isUp and etc methods are found and joinG=true");
                        joinG=true; 
                    }
                } catch (NoSuchMethodException e) {
                    Util.prtDbg("No such mehtod found and joinG=true");
                    joinG=true;
                }
                if (joinG) {
                    msock.joinGroup(mcSAddr,ni);             

                }
            }
            _dsocket.setSoTimeout(0); // set alive
        } catch (SocketException se) {
            Util.prtException(se, "cockpit-rebind datagram Socket exception");
            return ;
        } catch (IOException e) {
            Util.prtException(e, "cockpit-rebind datagram IO exception");
            return ;
        }catch (Exception ee) {
            Util.prtException(ee, "cockpit-rebind datagram exception");
            return ;
        }
        return ;
    }


    public void closeSocket() {
        try {
            _dsocket.close();
        } catch (Exception e) {}
        try {
            _tcpsocket.close();
        } catch (Exception e) {}
    }
}
