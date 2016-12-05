package edu.ucar.nidas.util;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.trolltech.qt.gui.QMessageBox;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QMessageBox.StandardButton;

import edu.ucar.nidas.ui.TextStatus;

/**
 * Utility class for printing, and exception handling 
 * @author dongl
 *
 */
public class Util {

    private static Integer  msgBoxCount= new Integer(0);

    /**
     * debug mood or not
     */
    private static boolean bDbg=false;
    //public static String _debug=null;

    /**
     * UI message-box good return
     */
    public static int RetOk = 1024;

    /**
     * UI message-box abort
     */
    public static int RetAbort = 262144;

    /**
     * File-write-out
     */
    private static FileWriter fwtr; 
    private static File file;

    /**
     * conn-debug-display
     */
    public  static TextStatus _txClient;

    //private ServLookup.

    public static String debugConnct=null;

    static {createFileWriter();}

    public static void setDebug(boolean dbg) {bDbg= dbg; if (file.exists()) file.delete(); createFileWriter();}

    /**
     * 
     * @param s
     * @param title
     * @return RetOk is ok to process, else abort
     */
    public static int confirmMsgBox(String s, String title ) {
        synchronized (msgBoxCount) {
            addDbgMsg("Confirm Msg: "+title+ " \n"+s);
            try{
                // msgBoxCount++;
                int ret= QMessageBox.warning( null, title, s, StandardButton.Ok, StandardButton.Abort);
                // msgBoxCount--; 
                return ret;
            } catch (Exception e) {addDbgMsg("confirmMsgBox: "+ e.getMessage());}
        }
        return RetAbort;
    }

    public static void prtDbg(String msg){
        addDbgMsg(msg);		
    }

    public static void prtErr(String msg){
        addDbgMsg(msg);
        prtMsgBox(" Err-Msg: "+msg);
    }

    public static String  prtException(Exception ee, String msg) {
        String emsg = ee.getClass().getName();
        emsg += "   "+ msg ;
        emsg += " \nMessage: "+ ee.getMessage();

        addDbgMsg(emsg);
        prtMsgBox(emsg);
        return emsg;

    }

    public static  void prtMsgBox (String s) {
        synchronized (msgBoxCount) {
            try{
                QMessageBox mb = new QMessageBox();
                QMessageBox.information( null, "", s);

            } catch (Exception e) {addDbgMsg("prtMsgBox: "+ e.getMessage());}
        }
    }

    public static  void prtMsgBox (QWidget p, String title, String msg) {
        synchronized (msgBoxCount) {
            try{

                QMessageBox mb = new QMessageBox();
                QMessageBox.information(p, title, msg);
            } catch (Exception e) {addDbgMsg("prtMsgBox: "+ e.getMessage());}
        }
    }


    /**
     * utility method to print a Calendar object using SimpleDateFormat.
     * @param calendar calendar object to be printed.
     */
    private static String getCalendarStr(Calendar calendar)
    {
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf.applyPattern("yyyy-M-d HH:mm:ss");
        return sdf.format(calendar.getTime());
    }

    public static void addDbgMsg(String msg) {
        if (_txClient!=null){
            synchronized (_txClient) {       
                if (_txClient!=null) _txClient.show(msg);
            }
        }

        if (debugConnct!=null) { debugConnct=debugConnct+ msg+"\n";}
        if (!bDbg ) {return;}

        try {
            if (fwtr==null) return;
            fwtr.append(msg+"\n");
            fwtr.flush();
        }
        catch (Exception e) {
            System.out.println("Filewriter appending exception...");
        }
    }

    private static void createFileWriter(){

        try {
            file= new File("cockpitDebug.txt");
        } catch (Exception e){System.out.println("opening debug file failed...");}
        try {
            if (file.length()>1) file.delete();
        } catch (Exception e) {}
        try {
            fwtr =  new FileWriter (file, true); //attach to the end
        } catch (Exception e){System.out.println("Creating file writer failed...");}		
    }


    public void finalize() {
        try {fwtr.close();
        } catch (Exception e) {}
    }

} //eof class-util





