package edu.ucar.nidas.util;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.trolltech.qt.gui.QMessageBox;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QMessageBox.StandardButton;


/**
 * Utility class for printing, and exception handling 
 */
public class Util {

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

}
