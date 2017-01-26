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
