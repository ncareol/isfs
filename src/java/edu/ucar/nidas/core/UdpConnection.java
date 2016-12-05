package edu.ucar.nidas.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
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

import edu.ucar.nidas.util.Util;

/**
 * 
 */
public class UdpConnection {

    DatagramSocket _udpSocket = null;
    Socket _tcpSocket = null;

    public DatagramSocket getUdpSocket()
    {
        return _udpSocket;
    }

    //create a udp socket to find data connections
    public DatagramSocket open()
        throws  IOException
    {
        close();
        _udpSocket = new DatagramSocket();
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

    public ArrayList<UdpConnInfo> search(String addr, int port) throws IOException
    {
        ArrayList<UdpConnInfo> connections = new ArrayList<UdpConnInfo>();

        if (_udpSocket == null) open();

        //build packet and display the contents
        DatagramPacket reqPacket =
            buildRequestPacket(_udpSocket.getLocalPort());

        InetSocketAddress sockAddr = new InetSocketAddress(addr, port);

        reqPacket.setSocketAddress(sockAddr);

        long stime = System.currentTimeMillis();
        _udpSocket.setSoTimeout(2500); //.25sec

        for (int i = 0; i < 3 && connections.size()==0 ;i++) {
            _udpSocket.send(reqPacket);

            while ((stime + 2000)> System.currentTimeMillis()) {
                //receive server info 
                int buffLen = 1024;
                byte[] dbuffer = new byte[buffLen];
                DatagramPacket packet = new DatagramPacket(dbuffer, buffLen);

                try {
                    _udpSocket.receive(packet);
                    UdpConnInfo info = parseConnInfo(packet);
                    connections.add(info);
                }
                catch(SocketTimeoutException e)
                {
                    // no problem
                }
                catch(IOException ee) {
                    Util.prtException(ee,"UdpConn search" );
                }
            }
        }
        _udpSocket.setSoTimeout(0);
        return connections;
    }

    public Socket connect(UdpConnInfo conn) throws IOException
    {
        if (_udpSocket == null) open();
        if (_tcpSocket != null) _tcpSocket.close();

        _tcpSocket = new Socket(conn.getIpAddr(), conn.getPort());

        ByteBuffer buf =  ByteBuffer.allocate(6);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0x76543210);
        buf.putShort((short)_udpSocket.getLocalPort());	

        _tcpSocket.setSoTimeout(500); //.5sec
        _tcpSocket.getOutputStream().write(buf.array());

        _tcpSocket.setSoTimeout(0);
        
        return _tcpSocket;
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
    
    public UdpConnInfo parseConnInfo(DatagramPacket packet)
        throws IOException  
    {

        byte[] buffer = packet.getData();

        UdpConnInfo conn = new UdpConnInfo();
        conn.setIpAddr(packet.getAddress());
        System.out.println("packet.getAddress=" + packet.getAddress().toString());

        // decode _packet data to get servname, dsmname, tcpport -> list<servInf>
        //get port

        ByteBuffer cb = ByteBuffer.wrap(buffer, 0, packet.getLength());
        cb.order(ByteOrder.BIG_ENDIAN);

        int magic = cb.getInt();
        if (magic != 0x76543210)
            throw new UnknownServiceException("incorrect magic number received: " + Integer.toHexString(magic));

        conn.setPort(cb.getShort());
        System.out.printf("conn port=%d\n",conn.getPort());

        conn.setUDPPort(cb.getShort());
        System.out.printf("conn udp port=%d\n",conn.getUDPPort());

        conn.setServer(packet.getAddress().getHostName());
        System.out.println("conn serv=" + conn.getServer());
        getString(cb); //skip short server name
        conn.setProjectName(getString(cb));
        System.out.println("conn project=" + conn.getProjectName());

        for ( ;; ) {
            String str = getString(cb);
            if (str == null) break;
            conn.addDsm(str);
            System.out.println("dsm=" + str);
            Util.prtDbg("dsm="+str);
        }
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
        for (i = 0; ib < lb && (bstr[i++] = bb.get()) != '\0'; ib++) ;
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
                System.out.println("eof="+ _eof );
                return -1;
            }
            return c[0];
        }

        public int read(byte[] b) throws IOException {
            if (_eof) return -1;
            int count = 0;
            count = _is.read(b);
            System.out.printf("b=%s\n", b.toString());
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
