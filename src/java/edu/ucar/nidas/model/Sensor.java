package edu.ucar.nidas.model;

import java.util.ArrayList;

/**
 * This class contains a sensor's parameters in the data-descriptor
 *  sensor-id
 *  group-id (dsm+sensor)
 *  sensor-name
 * 
 * @author dongl
 *
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
