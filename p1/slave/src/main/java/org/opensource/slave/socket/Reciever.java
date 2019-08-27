package org.opensource.slave.socket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensource.master.format.DataContainer;
import org.opensource.slave.common.Constant;
import org.opensource.slave.config.AppProperties;
import org.opensource.slave.repository.AbstractRepositoryManager;

public class Reciever {
	private final static Logger logger = LogManager.getLogger(Reciever.class);

	private int PORT = 20000;
	private String IP = "127.0.0.1";
	private String ID = "TEST";
	
	AbstractRepositoryManager repositoryManager;
	
	private String back_data_table = "myData";
	
	public Reciever(AppProperties props, AbstractRepositoryManager repositoryManager) {
		this.repositoryManager = repositoryManager;
		if(props.getPropsMap().get(Constant.MASTERPORT) != null){
			PORT = Integer.valueOf(props.getPropsMap().get(Constant.MASTERPORT));
		}else{
			PORT = 20000;
		}
		if(props.getPropsMap().get(Constant.MASTERIP) != null){
			IP = props.getPropsMap().get(Constant.MASTERIP);
		}else{
			IP = "127.0.0.1";
		}
		if(props.getPropsMap().get(Constant.SLAVEID) != null){
			ID = props.getPropsMap().get(Constant.SLAVEID);
		}else{
			ID = "Slave";
		}
		
		if(props.getPropsMap().get(Constant.DB_BACKUP_TABLE_NAME) != null){
			back_data_table = props.getPropsMap().get(Constant.DB_BACKUP_TABLE_NAME);
    	}
	}
	
	public void startReciving(){
		Socket socket = null;
		ObjectOutputStream oos =null;
		ObjectInputStream ois = null;
		try {
			socket = new Socket(IP,PORT);
		
			oos = new ObjectOutputStream(socket.getOutputStream());
			oos.writeObject(ID);
			logger.info("Sending request to Socket Server");
			ois = new ObjectInputStream(socket.getInputStream());
			
			while(true){
				if(!socket.isConnected()) break;
		        DataContainer dataContainer = (DataContainer) ois.readObject();
		        if(dataContainer.getSlaveID().equals(ID)){
		        	logger.info("Reciever Data Count: " + dataContainer.getRowCount());
	            	backUpData(dataContainer);
		        }
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}finally{
			 try {
				ois.close();
				oos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void backUpData(DataContainer dataContainer) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
		Connection con = null;
		try {
			con = repositoryManager.getConnection();
			
			Statement stmt = con.createStatement();
            con.setAutoCommit(false);
			PreparedStatement pstmt = con.prepareStatement(
                    "INSERT INTO "+back_data_table+"(value, created) VALUES(?,?)");
			
			for (List<String> row : dataContainer.getRowList()) {
                // Add each parameter to the row.
                pstmt.setInt(1, Integer.parseInt(row.get(0)));
                Date d = format.parse(row.get(1));
                pstmt.setTimestamp(2,  new Timestamp(d.getTime()));
                pstmt.addBatch();
            }
	     
            try {
                pstmt.executeBatch();
            } catch (Exception e) {
                System.out.println("Error message: " + e.getMessage());
                return; 
            }
            
            con.commit();
		} catch(Exception e) {
			logger.error(e);
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
}
