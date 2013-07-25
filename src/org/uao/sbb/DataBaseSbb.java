package org.uao.sbb;

import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import org.uao.data.UsersInfoManager;

public abstract class DataBaseSbb implements javax.slee.Sbb {
	
	public String getSipUri(String name){
		System.out.println("Getting user URI from DataBase");
		UsersInfoManager um = new UsersInfoManager();
		String sipUri = um.getSipuri(name);
		String contactIP = null;
		if(sipUri != null){
			contactIP = this.getIp(sipUri);
		}
		
		return contactIP;
	}
	
	public String getState(String name){
		UsersInfoManager im = new UsersInfoManager();
		return im.getState(name);
	}
	
	private String getIp(String uri){
		if(uri.indexOf(';') != -1){
			return uri.substring(uri.indexOf('@')+1, uri.indexOf(';'));
		} else{
			return uri.substring(uri.indexOf('@')+1);
		}
		
	}

	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { this.sbbContext = context; }
    public void unsetSbbContext() { this.sbbContext = null; }
    
    // TODO: Implement the lifecycle methods if required
    public void sbbCreate() throws javax.slee.CreateException {}
    public void sbbPostCreate() throws javax.slee.CreateException {}
    public void sbbActivate() {}
    public void sbbPassivate() {}
    public void sbbRemove() {}
    public void sbbLoad() {}
    public void sbbStore() {}
    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
    public void sbbRolledBack(RolledBackContext context) {}
	

	
	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this 
	 * method, the sbbContext variable and the variable assignment in setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
