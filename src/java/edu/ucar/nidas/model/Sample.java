package edu.ucar.nidas.model;


import java.util.ArrayList;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class contains the sample's parameters
 *  sample-id
 *  a list of Variables
 * 
 * @author dongl
 *
 */
public class Sample {

    /**
     * SampleId = dsm+sensor+sample from nidas' sample-complix_id  bit31-26--typeId, 25-16=DsmId 15-0=sensor+sampleIds  
     */
	int _id;
	
	/**
	 * A list of variables in the same sample. 
	 */
	ArrayList<Var> _vars = new ArrayList<Var>(0);

	/**
	 * Set the sample's ID
	 * @param val
	 */
	public void setId(int val) 		{_id=val;}
	public int getId() 	        	{ return _id;}
	public void addVar(Var v)	 	{_vars.add(v);}
	public Var getVar(int i)	 	{ return _vars.get(i);}
	public int getNumVars() 		{ return _vars.size();}
	public ArrayList<Var> getVars() { return _vars;}

	/**
	 * Loop through all the variables for ONE sample.
	 * @param sampNode  - sample node
	 */
	public void walkVars(Node sampNode, int siteNum) {
	   	NodeList nl = sampNode.getChildNodes();

		int offset = 0;
		for (int i=0; i<nl.getLength();i++){
			Node n= nl.item(i);
			if (n==null) return;
	           
			//get variable attrs
			NamedNodeMap attrs = n.getAttributes();
			String name="", units="", range="";
			int length=1;
			boolean dyn= false, disp =true;
			for (int j=0; j< attrs.getLength(); j++){
				Attr attr = (Attr)attrs.item(j);
				if ("name".equals(attr.getName())) 
					name = attr.getValue();
				if ("units".equals(attr.getName())) 
					units = attr.getValue();
				if ("plotrange".equals(attr.getName())) 
					range = attr.getValue();
				if ("length".equals(attr.getName())) 
					length = Integer.valueOf(attr.getValue());
				if ("dynamic".equals(attr.getName())) 
					dyn = Boolean.valueOf(attr.getValue());
				if ("display".equals(attr.getName())) 
					disp = Boolean.valueOf(attr.getValue());
			}
			
			Var v= new Var();			
			v.setSampleId(_id);
			v.setName(name+"#"+siteNum);
		       
			v.setUnits(units);
			v.setOffset(offset);
			v.setLength(length);
			if(dyn) v.setDynamic(dyn);
			if(!disp) v.setDisplay(disp);
	     		
			offset += length;
			if (range ==null || range.length()<1) {
				v.setMin(-10);
				v.setMax(10);
//				Util.prtDbg("sample-walk-var: name=" +name+" range=null" );
			} else {
//				Util.prtDbg("sample-walk-var: name=" +name+ " range="+ range);
				float min = Float.valueOf(range.split(" ")[0].trim());
				float max = Float.valueOf(range.split(" ")[1].trim());
				v.setMin(Float.valueOf(range.split(" ")[0].trim()));
				v.setMax(Float.valueOf(range.split(" ")[1].trim()));
//				Util.prtDbg("sample-walk-var: name=" +name+"  min="+min + "  max="+max );
			}
			_vars.add(v);
		}
	}
}
