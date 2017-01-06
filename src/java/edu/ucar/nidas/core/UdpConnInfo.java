package edu.ucar.nidas.core;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import edu.ucar.nidas.util.Util;

/**
 * 
 * This class contains the information about a data-feeder
 *   server-name
 *   project-name
 *   tcp-port
 *   udp-port
 *   ip-address, etc
 */
public class UdpConnInfo
{
    String _server = null;
    String _projectName = null;
    int _tcpPort = -1;
    int _udpPort = -1;
    InetAddress _addr = null;
    
    /**
     * Names of DSMs are sent in initial connection exchange.
     */
    List<String> _dsms = new ArrayList<String>();

    public void setServer(String val) { _server = val; }
    public void setProjectName(String val) { _projectName = val; }
    public void setPort(int val) { _tcpPort = val; }
    public void setUDPPort(int val) { _udpPort = val; }
    public void addDsm(String dsm) { _dsms.add(dsm); }
    public void setIpAddr(InetAddress val) { _addr = val; }

    public String getServer() { return _server; }
    public String getProjectName() { return _projectName; }
    public int getPort() { return _tcpPort; }
    public int getUDPPort() { return _udpPort; }
    public List<String> getDsms() { return _dsms; }
    public InetAddress getIpAddr() { return _addr; }
    
    public String toString()
    {
        return _server + ": " + _projectName;
    }
}
