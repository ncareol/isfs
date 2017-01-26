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

package edu.ucar.nidas.model;

import java.util.ArrayList;

/**
 * A NIDAS sensor. This java class is currently not being used.
 */
public class Sensor {

    String _id, _gid, _name;
    
    public void setId(String _id) 		{this._id=_id;}
    public void setGid(String _gid) 		{this._gid=_gid;}
    public void setName(String _name) 	{this._name=_name;}
    public void setAll (ArrayList<String> all){
        int i=0;
        _id= all.get(i++);
        _gid=all.get(i++);
        _name=all.get(i++);
    }
    
    
    public String getId() 	{return _id;}
    public String getGid() 	{return _gid;}
    public String getName() {return _name;}
    public  ArrayList<String> getAll() {
        ArrayList<String> all= new ArrayList<String>(0);
        all.add(_id);
        all.add(_gid);
        all.add(_name);
        return all;
    }
}
