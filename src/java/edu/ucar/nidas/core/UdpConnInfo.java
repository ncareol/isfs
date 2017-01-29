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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * This class contains the information about a data feed
 */
public class UdpConnInfo
{
    String _server = null;

    /**
     * Address that the original UDP request was sent to.
     * This could be a multicast address. 
     */
    InetAddress _udpAddr = null;

    String _projectName = null;

    /**
     * Port on server where the XML stream can be read.
     */
    int _tcpPort = -1;

    /**
     * If a multicast request is made, then the server will
     * send data to this multicast port.
     */
    int _multicastPort = -1;

    /**
     * When a response is returned from a server, this
     * is the address that the response returned from.
     * A TCP request can then be made to this address to fetch the XML.
     */
    InetAddress _tcpAddr = null;
    
    /**
     * Names of DSMs are sent in initial connection exchange.
     */
    List<String> _dsms = new ArrayList<String>();

    public void setServer(String val) { _server = val; }

    public void setProjectName(String val) { _projectName = val; }

    public void setPort(int val) { _tcpPort = val; }

    public void setMulticastPort(int val) { _multicastPort = val; }

    public void addDsm(String dsm) { _dsms.add(dsm); }

    public void setTcpAddr(InetAddress val) { _tcpAddr = val; }

    public void setUdpAddr(InetAddress val) { _udpAddr = val; }

    public String getServer() { return _server; }

    public String getProjectName() { return _projectName; }

    public int getPort() { return _tcpPort; }

    public int getMulticastPort() { return _multicastPort; }

    public List<String> getDsms() { return _dsms; }

    public InetAddress getTcpAddr() { return _tcpAddr; }

    public InetAddress getUdpAddr() { return _udpAddr; }
    
    public String toString()
    {
        return _server + ": " + _projectName;
    }
}
