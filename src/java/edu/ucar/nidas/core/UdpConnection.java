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

package edu.ucar.nidas.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.util.Enumeration;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import edu.ucar.nidas.model.Log;

/**
 * 
 */
public class UdpConnection {

    DatagramSocket _udpSocket = null;

    Socket _tcpSocket = null;

    int _udpPort;

    public UdpConnection(int udpPort)
    {
        _udpPort = udpPort;
    }

    public DatagramSocket getUdpSocket()
    {
        return _udpSocket;
    }

    public void close() throws  IOException
    {
        if (_udpSocket != null) {
            _udpSocket.close();
            _udpSocket = null;
        }
            
        if (_tcpSocket != null) {
            _tcpSocket.close();
            _tcpSocket = null;
        }
    }

    public ArrayList<UdpConnInfo> search(String addr, int destPort,
        int ttl, Log log, boolean debug)
        throws IOException
    {
        ArrayList<UdpConnInfo> connections = new ArrayList<UdpConnInfo>();
        InetAddress inetAddr = InetAddress.getByName(addr);

        close();
        if (inetAddr.isMulticastAddress()) {
            // MulticastSocket msock = new MulticastSocket(destPort);
            // _udpSocket = msock;
            _udpSocket = openDatagramSocket(_udpPort, log);
        }
        else {
            _udpSocket = openDatagramSocket(_udpPort, log);
        }

        //build packet and display the contents
        DatagramPacket reqPacket =
            buildRequestPacket(_udpSocket.getLocalPort());

        InetSocketAddress sockAddr = new InetSocketAddress(addr, destPort);

        reqPacket.setSocketAddress(sockAddr);

        _udpSocket.setSoTimeout(1000); // .5sec

        for (int i = 0; i < 3 && connections.isEmpty() ;i++) {
            _udpSocket.send(reqPacket);
            if (debug) log.debug("Sending connection request to " +
                    sockAddr.toString());

            long stime = System.currentTimeMillis();
            while ((stime + 2000)> System.currentTimeMillis()) {
                //receive server info 
                int buffLen = 1024;
                byte[] dbuffer = new byte[buffLen];
                DatagramPacket packet = new DatagramPacket(dbuffer, buffLen);

                try {
                    _udpSocket.receive(packet);
                    if (debug) log.debug("Packet received from " +
                           packet.getSocketAddress().toString());
                    UdpConnInfo info = parseConnInfo(packet,
                            inetAddr, log, debug);
                    connections.add(info);
                }
                catch(SocketTimeoutException e)
                {
                    if (debug)
                        log.debug("Timeout receiving connection response");
                }
                catch(IOException ee) {
                    log.error("search: " + ee.toString());
                }
            }
        }
        _udpSocket.setSoTimeout(0);
        return connections;
    }

    public DatagramSocket openDatagramSocket(int port, Log log)
        throws SocketException
    {

        SocketException exc = null;
        DatagramSocket sock = null;
        for (int i = 0; i < 10; i++) {
            try {
                log.debug("Creating UDP socket for initial requests, port=" +
                        String.valueOf(port+i));
                sock = new DatagramSocket(port+i);
                break;
            }
            catch(SocketException e) {
                log.debug(e.toString());
                exc = e;
            }
        }
        if (sock == null) throw exc;
        return sock;
    }

    /**
     * Send a TCP connection request packet to a TCP port
     * on the server corresponding to conn.getTcpAddr().
     * The request packet inclues the UDP port that we
     * want to receive packets on.
     * After this is done, the XML can be read from the 
     * tcp socket with readDOM.
     */
    public void connect(UdpConnInfo conn, Log log, boolean debug)
        throws IOException
    {
        /*
         * If the request was sent to a multicast port.
         * then we want to receive multicasts back,
         * on the same group address.
         */
        if (conn.getUdpAddr().isMulticastAddress()) {
            _udpSocket.close();
            _udpSocket = null;
            log.debug("Creating Multicast socket, address=" +
                    conn.getUdpAddr().toString() + ":" +
                    String.valueOf(conn.getMulticastPort()));
            MulticastSocket msock = new MulticastSocket(
                    conn.getMulticastPort());
            msock.joinGroup(conn.getUdpAddr());
            _udpSocket = msock;
        }

        if (debug)
            log.debug("Creating TCP socket: " +
                    conn.getTcpAddr().getHostAddress() + ':' +
                    String.valueOf(conn.getPort()));

        if (_tcpSocket != null) {
            _tcpSocket.close();
            _tcpSocket = null;
        }
        
        _tcpSocket = new Socket(conn.getTcpAddr(), conn.getPort());

        ByteBuffer buf =  ByteBuffer.allocate(6);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0x76543210);
        buf.putShort((short)_udpSocket.getLocalPort());	

        _tcpSocket.setSoTimeout(500); //.5sec

        if (debug)
            log.debug("Sending TCP request: local UDP port=" +
                    String.valueOf(_udpSocket.getLocalPort()));
        _tcpSocket.getOutputStream().write(buf.array());

        _tcpSocket.setSoTimeout(0);
    }

    private DatagramPacket buildRequestPacket(int clientPort)
    {
        //throws BufferOverflowException, IndexOutOfBoundsException  {

        int buffLen = 12; // 4bytes magic, 4 bytes req#, 2bytes cp, 2bytes udp 
        ByteBuffer buf = ByteBuffer.allocate(buffLen); 
        buf.order(ByteOrder.BIG_ENDIAN);

        buf.putInt(0x01234567);
        buf.putInt(2);
        buf.putShort((short)clientPort);
        buf.putShort((short)2);

        return new DatagramPacket(buf.array(), buffLen);
    }
    
    public UdpConnInfo parseConnInfo(DatagramPacket packet,
        InetAddress udpAddr, Log log, boolean debug)
        throws IOException  
    {

        byte[] buffer = packet.getData();

        UdpConnInfo conn = new UdpConnInfo();

        conn.setTcpAddr(packet.getAddress());
        if (udpAddr.isMulticastAddress())
            conn.setUdpAddr(udpAddr);
        else
            conn.setUdpAddr(packet.getAddress());

        // decode _packet data to get servname, dsmname, tcpport -> list<servInf>
        //get port

        ByteBuffer cb = ByteBuffer.wrap(buffer, 0, packet.getLength());
        cb.order(ByteOrder.BIG_ENDIAN);

        int magic = cb.getInt();
        if (magic != 0x76543210)
            throw new UnknownServiceException("incorrect magic number received: " + Integer.toHexString(magic));

        conn.setPort(cb.getShort());
        if (debug) log.debug("Connection response: remote TCP port=" +
                String.valueOf(conn.getPort()));

        conn.setMulticastPort(cb.getShort());
        if (debug) log.debug("Connection response: optional multicast port=" +
                String.valueOf(conn.getMulticastPort()));

        conn.setServer(packet.getAddress().getHostName());
        if (debug) log.debug("Connection response: remote host=" +
            conn.getServer());

        getString(cb); //skip short server name
        conn.setProjectName(getString(cb));
        if (debug) log.debug("Connection response: project=" +
            conn.getProjectName());

        for ( ;; ) {
            String str = getString(cb);
            if (str == null) break;
            conn.addDsm(str);
            // System.out.println("dsm=" + str);
        }
        if (debug) log.debug("Number of DSMs=" + conn.getDsms().size());
        return conn;
    }

    /**
      * Read bytes from a ByteBuffer, up to a '\0', return at UTF-8 String
      * Return null if nothing left in buffer.
      */
    private String getString(ByteBuffer bb) throws UnsupportedEncodingException
    {
	int ib =  bb.position();
        int lb =  bb.limit();
        if (ib == lb) return null;
        byte[] bstr = new byte[lb - ib];
        int i;
        for (i = 0; ib < lb && (bstr[i] = bb.get()) != '\0'; ib++,i++) ;
        return new String (bstr,0,i, "UTF-8");
    }

    public Document readDOM() throws SAXException, IOException, ParserConfigurationException
    {
        XMLInputStream xmls = new XMLInputStream(_tcpSocket.getInputStream());

	// Create a builder factory
	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);

	// Create the builder and parse the file
	Document doc = factory.newDocumentBuilder().parse(xmls);

        // NIDAS code sets the keep alive value on this tcp socket,
        // in order to detect when the remote client is no longer running.
        // Therefore we don't want to close it.
        return doc;
    }

    /**
     * InputStream for XML from a TCP socket.
     * A byte value of 4 (EOT) indicates EOF.
     */
    public class XMLInputStream extends InputStream {

        boolean _eof = false;
        InputStream _is;

        public XMLInputStream(InputStream s )
        {
            _is = s;
        }

        public int read() throws IOException {
            if (_eof) return -1;
            byte[] c = new byte[1];
            int count = 0;
            count = _is.read(c);
            // System.out.printf("c=%s\n",new String(c,"UTF-8"));

            if (count == 1 && c[0] == 4){
                _eof = true;
                // System.out.println("eof="+ _eof );
                return -1;
            }
            return c[0];
        }

        public int read(byte[] b) throws IOException {
            if (_eof) return -1;
            int count = 0;
            count = _is.read(b);
            // System.out.printf("b=%s\n", b.toString());
            for (int i = 0; i < count; i++) {
                if (b[i] == 4) {
                    count = i;
                    _eof = true;
                }
            }
            //	System.out.println("count="+count + "eof="+_eof);
            return count;
        }
    }
}
