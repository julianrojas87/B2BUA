package org.uao.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsersInfoManager {
	
	private Usersinfo u;
	private UsersinfoDao uDao;
    private Connection cx;

    public UsersInfoManager(){
    	u = new Usersinfo();
        uDao = new UsersinfoDao();
        getConnection();
    }
    
    public String getSipuri(String identification){
    	u.setIdentification(identification);
    	try {
			uDao.load(cx, u);
			cx.close();
		} catch (NotFoundException e) {
			return null;
		} catch (SQLException e) {
			e.printStackTrace();
		}
    	return u.getSipuri();
    }
    
    public String getState(String identification){
    	u.setIdentification(identification);
    	try {
			uDao.load(cx, u);
			cx.close();
		} catch (NotFoundException e) {
			return null;
		} catch (SQLException e) {
			e.printStackTrace();
		}
    	return u.getState();
    }
    
    private void getConnection(){
        try {
            String driver = "org.postgresql.Driver";
            String url = "jdbc:postgresql://localhost:5432/TelcompUsers";
            String user = "postgres";
            String passwd = "postgres";

            Class.forName(driver);
            cx = DriverManager.getConnection(url, user, passwd);

        } catch (SQLException ex) {
            Logger.getLogger(UsersInfoManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(UsersInfoManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
