package org.uao.sbb;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.FactoryException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.UnrecognizedActivityException;

import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

public abstract class B2BSbb implements javax.slee.Sbb {
	
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipFactoryProvider;
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	
	// Initial request
	public void onInviteEvent(RequestEvent event, ActivityContextInterface aci) {
		// ACI is the server transaction activity
		ServerTransaction st = event.getServerTransaction();
		try {
			// Create the dialogs representing the incoming and outgoing call legs
			DialogActivity incomingDialog = (DialogActivity) sipFactoryProvider.getNewDialog(st);
			//Set false the property of getNewDialog(DialogActivity, boolean) to create a new
			//dialog with a different dialog ID.
			DialogActivity outgoingDialog = sipFactoryProvider.getNewDialog(incomingDialog, false);
			// Obtain the dialog activity contexts and attach to them
			ActivityContextInterface outgoingDialogACI = sipActivityContextInterfaceFactory.getActivityContextInterface(outgoingDialog);
			ActivityContextInterface incomingDialogACI = sipActivityContextInterfaceFactory.getActivityContextInterface(incomingDialog);
			incomingDialogACI.attach(this.sbbContext.getSbbLocalObject());
			outgoingDialogACI.attach(this.sbbContext.getSbbLocalObject());
			// Record which dialog is which, so we can find the peer dialog
			// when forwarding messages between dialogs.
			setIncomingDialog(incomingDialogACI);
			setOutgoingDialog(outgoingDialogACI);
			forwardRequest(st, outgoingDialog, aci);
		} catch (SipException e) {
			e.printStackTrace();
			sendErrorResponse(st, Response.SERVICE_UNAVAILABLE);
		} catch (FactoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnrecognizedActivityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// Responses
	public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		if(event.getResponse().getStatusCode() != 101){
			processResponse(event, aci);
		}
	}

	public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		processResponse(event, aci);
	}
	
	public void on4xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		processResponse(event, aci);
	}
	
	public void on6xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		processResponse(event, aci);
	}

	// other responses handled the same way as above
	// Mid-dialog requests
	public void onAck(RequestEvent event, ActivityContextInterface aci) {
		processMidDialogRequest(event, aci);
	}

	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		processMidDialogRequest(event, aci);
	}

	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		this.sipFactoryProvider.acceptCancel(event, false);
		processMidDialogRequest(event, aci);
	}
	
	// Other mid-dialog requests handled the same way as above
	// Helpers
	private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
		try {
			// Find the dialog to forward the request on
			ActivityContextInterface peerACI = getPeerDialog(dialogACI); 
			forwardRequest(event.getServerTransaction(), (DialogActivity) peerACI.getActivity(), dialogACI);
		} catch (SipException e) {
			System.out.println("processMidDialog error because: "+e.getMessage());
			sendErrorResponse(event.getServerTransaction(), Response.SERVICE_UNAVAILABLE);
		}
	}

	private void processResponse(ResponseEvent event, ActivityContextInterface aci) {
		try {
			// Find the dialog to forward the response on
			ActivityContextInterface peerACI = getPeerDialog(aci);
			forwardResponse((DialogActivity) aci.getActivity(), (DialogActivity) peerACI.getActivity(), event.getClientTransaction(), event.getResponse());
		} catch (SipException e) {
			System.out.println(e.getMessage());
		}
	}

	private ActivityContextInterface getPeerDialog(ActivityContextInterface aci) throws SipException {
		if (aci.getActivity().equals(getIncomingDialog().getActivity())){
			System.out.println("incomingDialog --> outgoingDialog");
			return getOutgoingDialog();
		}	
		if (aci.getActivity().equals(getOutgoingDialog().getActivity())){
			System.out.println("outgoingDialog --> incomingDialog");
			return getIncomingDialog();
		}	
		throw new SipException("could not find peer dialog");
	}

	private void forwardRequest(ServerTransaction st, DialogActivity out, ActivityContextInterface aci) throws SipException {
		// Copies the request, setting the appropriate headers for the dialog.
		Request incomingRequest = st.getRequest();
		
		if(incomingRequest.getMethod().toString().equals("ACK")){
			CSeqHeader Cseq = (CSeqHeader) incomingRequest.getHeader(CSeqHeader.NAME);
			try {
				Request outgoingRequest = out.createAck(Cseq.getSeqNumber());
				outgoingRequest.setHeader(getContactHeader());
				out.sendAck(outgoingRequest);
			} catch (InvalidArgumentException e) {
				System.out.println(e.getMessage());
			} catch (SipException e){
				System.out.println(e.getMessage());
			} catch (ParseException e) {
				System.out.println(e.getMessage());
			}
		} 
		
		else if(incomingRequest.getMethod().toString().equals("CANCEL")){
			try{
				ClientTransaction ct = out.sendCancel();
				out.associateServerTransaction(ct, st);
			} catch(SipException e){
				System.out.println(e.getMessage());
			}
		} 
		
		else if(incomingRequest.getMethod().toString().equals("BYE")){
			Request outgoingRequest = out.createRequest(incomingRequest);
			try {
				outgoingRequest.setHeader(getContactHeader());
				ToHeader toHeader = (ToHeader) outgoingRequest.getHeader(ToHeader.NAME);
				String name = this.getName(toHeader.getAddress().toString());
				ChildRelation childRelation = this.getDataBaseSbb();
	        	DataBaseSbbLocalObject dataBaseSbb = (DataBaseSbbLocalObject) childRelation.create();
	        	String uri = dataBaseSbb.getSipUri(name);
	        	aci.detach(dataBaseSbb);
				SipURI urit = addressFactory.createSipURI(name, uri);
				outgoingRequest.setRequestURI(urit);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			} 
			ClientTransaction ct = this.sipFactoryProvider.getNewClientTransaction(outgoingRequest);
			out.sendRequest(ct);
			out.associateServerTransaction(ct, st);
		}
		
		else {
			Request outgoingRequest = out.createRequest(incomingRequest);
			try {
				System.out.println("Initial Request URI: "+outgoingRequest.getRequestURI());
				ToHeader toHeader = (ToHeader) outgoingRequest.getHeader(ToHeader.NAME);
				String name = this.getName(toHeader.getAddress().toString());
				ChildRelation childRelation = this.getDataBaseSbb();
	        	DataBaseSbbLocalObject dataBaseSbb = (DataBaseSbbLocalObject) childRelation.create();
	        	String uri = dataBaseSbb.getSipUri(name);
	        	if(uri == null){
	        		this.sendErrorResponse(st, Response.NOT_FOUND);
					out.delete();
	        	} else {
	        		//Checking if user is available
		        	if(dataBaseSbb.getState(name).equals("offline")){
		        		this.sendErrorResponse(st, Response.NOT_FOUND);
		        		out.delete();
		        		aci.detach(dataBaseSbb);
		        	} else{
		        		aci.detach(dataBaseSbb);
		        		SipURI urit = addressFactory.createSipURI(name, uri);
						outgoingRequest.setRequestURI(urit);
						outgoingRequest.setHeader(getContactHeader());
						System.out.println("Final Request URI: "+urit.toString());
						// Send the request on the dialog activity
						ClientTransaction ct = out.sendRequest(outgoingRequest);
						// Record an association with the original server transaction,
						// so we can retrieve it when forwarding the response.
						out.associateServerTransaction(ct, st);
		        	}
	        	}
			} catch (Exception e) {
				System.out.println("Can not change URI because: "+e.getMessage().toString());
			}
		}
	}

	private void forwardResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse) throws SipException {
		// Find the original server transaction that this response
		// should be forwarded on.
		
		ServerTransaction st = in.getAssociatedServerTransaction(ct);
		// could be null
		if (st == null) {throw new SipException("could not find associated server transaction");}
		// Copy the response across, setting the appropriate headers for the dialog
		
		Response outgoingResponse = out.createResponse(st, receivedResponse);
		// Forward response upstream.
		try {
			outgoingResponse.setHeader(getContactHeader());
			st.sendResponse(outgoingResponse);
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
			throw new SipException("invalid response", e);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
		}
	}
	
	private ContactHeader getContactHeader() throws ParseException {
		ContactHeader contactHeader;
		ListeningPoint listeningPoint = sipFactoryProvider.getListeningPoint("udp");
		Address address = addressFactory.createAddress("Mobicents JSLEE AS <sip:"+ listeningPoint.getIPAddress() + ">");
		((SipURI) address.getURI()).setPort(listeningPoint.getPort());
		contactHeader = headerFactory.createContactHeader(address);
		return contactHeader;
	}

	private void sendErrorResponse(ServerTransaction st, int statusCode) {
		try {
			Response response = sipFactoryProvider.getMessageFactory().createResponse(statusCode, st.getRequest());
			st.sendResponse(response);
		} catch (Exception e) {
			System.out.println("Could not send error response because: "+e.getMessage().toString());
		}
	}
	
	//Get User name from URI
	private String getName(String prevName){
		return prevName.substring(prevName.indexOf(':')+1, prevName.indexOf('@'));
	}


	// CMP field accessors for each Dialogs ACI
	public abstract void setIncomingDialog(ActivityContextInterface aci);
	public abstract ActivityContextInterface getIncomingDialog();
	public abstract void setOutgoingDialog(ActivityContextInterface aci);
	public abstract ActivityContextInterface getOutgoingDialog();
	public abstract ChildRelation getDataBaseSbb();

	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipFactoryProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory = sipFactoryProvider.getAddressFactory();
			headerFactory = sipFactoryProvider.getHeaderFactory();
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
	
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
