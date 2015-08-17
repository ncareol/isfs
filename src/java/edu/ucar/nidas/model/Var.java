package edu.ucar.nidas.model;

/**
 *  This class contains a variable parameters in the data-descriptor
 *  
 *  sample-id (dsm+sensor+sample)
 *  var-name
 *  var-unit
 *  var-len
 *  
 * @author dongl
 *
 */
public class Var {

	String 	_name, _units;
	int    	_sampleId, _length;
	
	/**
	 *  index of the start of my data within my enclosing sample
	 *  For example: 
	 *  SampleOne:
	 *     VarOne, datalen=1, offset =0;
	 *     VarTwo, datalen=4, offset =1;
	 *     VArThree, datalen=1, offset=5;
	 */
	int _offset;
	
	/**
	 * deafult plot_min and plot_max of the variable from the data descriptor
	 */
	float 	_plotMax, _plotMin;
	
	/**
	 * create a plot at beginning, or later when data arrives.
	 * default=false.
	 */
	boolean _dynamic=false; 					//default=false--create at beginning, =true--create it when its data is received.
	
	/**
	 * The variable is measured in the nidas, but not intended to display. 
	 * default =true; 
	 */
	boolean _display=true;						//display this plot or not -- =false-create a data-client but not a gauge
	
	
	public void setSampleId(int val) 	    {_sampleId=val;} //dsm_sensor+sample Id
   	public void setName(String val) 	{_name=val;}
	public void setUnits(String val) 	{_units=val;}
	public void setMin(float val) 		{_plotMin=val;}
	public void setMax(float val) 		{_plotMax=val;}
	public void setDynamic(boolean dyn)		{_dynamic=dyn;}
	public void setDisplay(boolean disp)		{_display=disp;}
	 /**
     * set the index of the start of my data within my enclosing sample
     * it is calculated based on the definition as above.
     */
    public void setOffset(int val)      {_offset=val;}
    
    /**
     * set the data-length of the variable. 
     * Most time, it has one measurement per variable; but it can has more than one.
     */
    public void setLength(int val)      {_length=val;}
    
	
	public int getSampleId()	{return _sampleId;}
	public int getOffset() 		{return _offset;}
	public int getLength()			{return _length;}
	public String getName() 	{return _name;}
	public String getUnits() 	{return _units;}
	public float getMin() 		{return _plotMin;}
	public float getMax() 		{return _plotMax;}
	public boolean getDynamic()		{return _dynamic;}
	public boolean getDisplay()		{return _display;}

 }
