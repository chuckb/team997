/**
 * 
 */
package org.usfirst.frc.team997.instrument;

//import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * This class will log current values from an attached PDP, for each channel defined on the PDP,
 * to a file on a USB stick (if plugged into the RoboRio).
 * @author chuck_benedict
 */
public class PDPCurrentLogger {
	private String logFileDirectory = "";
	private int maxChannel = PowerDistributionPanel.kPDPChannels - 1;
	private long pollingFrequencyInMills = 25;
	private BufferedWriter bufferedWriter = null;
	private FileWriter fileWriter = null;
	private Thread thread = null;
	
	public String getLogFileDirectory() {
		return logFileDirectory;
	}

	public int getMaxChannel() {
		return maxChannel;
	}

	public long getPollingFrequencyInMills() {
		return pollingFrequencyInMills;
	}

	public void setPollingFrequencyInMills(long frequency) {
		if (frequency < 25) {
			pollingFrequencyInMills = 25;
		} else {
			pollingFrequencyInMills = frequency;
		}
	}
	
	/**
	 * Initialize with the directory into which log files will be written.
	 * @param directory
	 * @throws IOException 
	 */
	public PDPCurrentLogger(String directory) throws IOException {
		logFileDirectory = directory;
		File file = new File(directory);
		// Directory must exist, otherwise bail...
		if (!file.exists()) {
			throw new IOException("Directory does not exist.");
		}
	}
	
	/**
	 * If you create an instance without a directory location, then 
	 * we assume that you are in a production environment and we will
	 * look for memory stick mountings on the RoboRio.
	 * @throws IOException 
	 */
	public PDPCurrentLogger() throws IOException {
		// NI version of linux symlinks mounted USB drives to 
		// /U and then /V according to page 16 of Roborio user manual
		logFileDirectory = "/U";
		File file = new File(logFileDirectory);
		// If directory does not exist, try next one...
		if (!file.exists()) {
			logFileDirectory = "/V";
			file = new File(logFileDirectory);
			if (!file.exists()) {
				throw new IOException("No USB file system found.");
			}
		}
	}
	
	/**
	 * Flush and close log file and terminate threads if not already done so. 
	 */
	protected void finalize( ) throws Throwable {
		stop();
		// TODO: Clean up...
		super.finalize( );  
	 }
	
	/**
	 * Write the fully formed PDP current record to the already
	 * opened buffered writer.
	 * @param record
	 * @throws IOException 
	 */
	void writePDPCurrentRecord(String record) throws IOException {
		bufferedWriter.write(record);
	}
	
	protected void closeLog() {
		try {
			if (bufferedWriter != null) {				
				bufferedWriter.flush();
				bufferedWriter.close();
				bufferedWriter = null;
			}
			
			if (fileWriter != null) {
				fileWriter.close();
				fileWriter = null;
			}			
		} catch (IOException ioe) {
			// TODO: Deal with this better...
			ioe.printStackTrace();
		}
	}
	
	protected void openLogForWriting() {
		closeLog();
		
		// TODO: Maybe should be configurable?
		int bufferSize = 8 * 1024;
		
	    try {
			//Specify the file name and path here
			File file = new File(getLogFileName());

			/* This logic will make sure that the file 
			 * gets created if it is not present at the
			 * specified location
			 */
			if (!file.exists()) {
			   file.createNewFile();
			}

			fileWriter = new FileWriter(file);
			bufferedWriter = new BufferedWriter(fileWriter, bufferSize);
	      } catch (IOException ioe) {
		   ioe.printStackTrace();
		}
	}
	
	/**
	 * Sample the current values for each channel and return an array.
	 * Length of array defined by getMaxChannel, which is obtained
	 * from the PDP itself.
	 */
	protected double[] getCurrentForAllChannels() {
		//TODO: Error handling...
		double[] current = new double[maxChannel];
		PowerDistributionPanel pdp = new PowerDistributionPanel();
		for(int channel = 0; channel <= maxChannel; channel++) {
			current[channel] = pdp.getCurrent(channel);
		}
		return current;
	}
	
	/**
	 * Take in an array of current values, one per PDP channel, and spit out a log record
	 * @return
	 */
	protected String assembleLogRecord(double[] current) {
		// Code up a time stamp
		// Docs say RoboRio is supposed to get good time when joined to driver station.
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");
		Date dateobj = new Date();
		String date = df.format(dateobj);
		
		String[] fields = new String[current.length + 1];
		fields[0] = date;

		// Convert doubles into strings
		for (int i = 1; i < current.length; i++)
		    fields[i] = String.valueOf(current[i]);

		String[][] toMarshall = new String[0][];
		toMarshall[0] = fields;
		return marshallCsv(toMarshall, true);
	}
	
	private String[] concat(String[] a, String[] b) {
	   int aLen = a.length;
	   int bLen = b.length;
	   String[] c= new String[aLen+bLen];
	   System.arraycopy(a, 0, c, 0, aLen);
	   System.arraycopy(b, 0, c, aLen, bLen);
	   return c;
	}
	
	/**
	 * This function takes a 2-dim string array and returns a string of
	 * encoded CSV values, multi-line if required.  It probably should be 
	 * in its own class, but included here because of laziness.
	 * @param data
	 * @param includeFirstLine
	 * @return
	 */
	private String marshallCsv(String[][] data,boolean includeFirstLine){
	    String delimeter = "\"";
	    String separator = ",";
	    StringBuilder csv = new StringBuilder("");
	    int i =0;
	    if(!includeFirstLine){
	        i=1;
	    }
	    for(;i<data.length;i++){//if includeFirstLine is true, start from index 1
	         for(int j=0;j<data[i].length;j++){
	             csv.append(delimeter+data[i][j]+delimeter);
	             if(j<data[i].length){
	                  csv.append(separator);
	             }
	             csv.append("\n");
	         }
	    }
	    return csv.toString();
	}
	
	/**
	 * No easy way to get the team number from the driver console
	 * or the image downloaded to the RoboRio...really?
	 * This code to get the hostname, which has the team number, graciously borrowed from 
	 * http://wpilib.screenstepslive.com/s/4485/m/13809/l/599750-archive-c-java-porting-guide-2014-to-2015
	 */
	private String getHostName() {
		Runtime run = Runtime.getRuntime();
		Process proc;
		String hostname = "";
		try {
			// I am not sure what this will return running sim on Windows, as hostname is a unix thing
			proc = run.exec("hostname");
			BufferedInputStream in = new BufferedInputStream(proc.getInputStream());
			byte [] b = new byte[256];
			in.read(b, 0, 256);
			hostname = new String(b).trim();
		} catch(IOException e1) {
			// I am not really sure how I want to handle these kinds of exceptions at this point
			e1.printStackTrace();
		}
		return hostname;
	}
	
	/**
	 * The format of the log file name is as follows:
	 *   {hostname}.{YYYY-MM-DD-HH-MM-SS}.current.csv
	 */
	protected String getLogFileName() {
		String fileName = "";
		String date = "";
				
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		Date dateobj = new Date();
		date = df.format(dateobj);
		fileName = logFileDirectory + "/" + getHostName() + "." + date + "current.csv";
		return fileName;
		/*
		DriverStation ds = DriverStation.getInstance();
		ds.isFMSAttached()
		ds.isDSAttached()
		*/
	}
	
	/**
	 * Non-blocking function to start current data collection.
	 */
    public void start() {
    	// Pseudo code
    	// Get match info from driver station
    	// Construct file name to write to
    	// Open output file writer
    	// Open buffered writer with enough space to get decent I/O performance (say 8K)
    	// Create thread
    	// Run thread

    	// Check if we are running...
    	if (thread != null)
    		return;
    	
    	// Open log file to collect current records
    	openLogForWriting();
    	
    	// Spin up new thread
        thread = new Thread(() -> {
            while (!Thread.interrupted()) {
            	double [] current = getCurrentForAllChannels();
            	String record = assembleLogRecord(current);
            	try {
            		writePDPCurrentRecord(record);
            	} catch (IOException io) {
            		// if we get here, bail out...
            		stop();
            	}
            	try {
            		Thread.sleep(pollingFrequencyInMills);
            	} catch (InterruptedException e) {
            		// its ok, swallow and go on...
            	}
            }
        });
        
        // GO!
        thread.start();
    }

    /**
     * Stop data collection and close log files.
     */
    public void stop() {
    	if (thread != null) {
    		thread.interrupt();
    		thread = null;
    	}
    	closeLog();
    }
}
