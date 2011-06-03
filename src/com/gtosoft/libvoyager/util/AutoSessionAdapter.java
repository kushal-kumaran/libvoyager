package com.gtosoft.libvoyager.util;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.util.Log;

import com.gtosoft.libvoyager.android.ActivityHelper;
import com.gtosoft.libvoyager.android.ServiceHelper;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.session.HybridSession;

/**
 * 
 * @author Brad Hein / GTOSoft LLC
 *
 * This class will manage a HybridSession in a way that requires no user intervention. 
 * For example, right off the bat we'll automatically detect the hardware type, and then 
 * try to detect the CAN network if one is present.  We'll also support muultiple interfaces
 * to the outside world such as TCP sockets (Command or Events). 
 *  
 */


public class AutoSessionAdapter {
	HybridSession	 hs; 					// hybrid session is the top of the libVoyager pyramid. it manages everything else. 
	Context			 mctxParentService;		// a reference to the parent context so that we can do things like work with Bluetooth. 
	BluetoothAdapter mbtAdapter;
	DashDB 			 ddb;
	GeneralStats	 mgStats = new GeneralStats();
	ServiceHelper 	 msHelper;
	
	
	/**
	 * Default Constructor. 
	 * This method gathers necessary objects from parent class and pulls the show together. 
	 * @param serviceContext
	 * @param btAdapter
	 */
	public AutoSessionAdapter(Context serviceContext, BluetoothAdapter btAdapter) {
		mctxParentService = serviceContext;
		mbtAdapter 		  = btAdapter;
		
		msg ("Spinning up DB");
		ddb = new DashDB(mctxParentService);
		msg ("DB Ready.");

		// TODO: Kick off a BT Discovery or other way to "choose" a peer.
		// TODO: The discovery process should choose a single device and run setupHSession against it. 
		
		choosePeerDevice();
	}

	/**
	 * Choose a device, whether by discovery or other means, and then set up the hybrid session for it. 
	 */
	private void choosePeerDevice () {
        msHelper = new ServiceHelper(mctxParentService);
        msHelper.registerChosenDeviceCallback(chosenCallback);
        msHelper.startDiscovering();
	}
	
	/**
	 * - Sets up the hybridSession. The life of a hybridSession starts when we find the peer MAC. And it ends when we are done being connected.
	 * - run this method upon choosing a peer.
	 * - we synchronize this method to prevent any thread pileups for things like bt discovery. 
	 */
	private synchronized boolean setupHSession (String btAddr) {
		
		// Sanity checks. 
		if (hs != null) {
			msg ("WARNING: hs already set up. not setting up again. ");
			return false;
		}
		
		// Instantiate the hybridsession. It will start by trying to connect ot the bluetooth peer. 
		hs = new HybridSession(mbtAdapter, btAddr, ddb, mOOBEventCallback);
		
		// Info/debug message handler.
		hs.registerMsgCallback(mecbMsg);

		// OOB messages coming from lower level classes
		hs.registerOOBHandler(mOOBEventCallback);
		
		// Register to be notified any time a datapoint is decoded. 
		hs.registerDPArrivedCallback(mDPArrivedCallback);
		
		
		return true;
	}
	

	/**
	 * This eventcallback will get executed (by Hybridsession) any time an out-of-band message is generated from any classes below us in the Voyager stack.
	 */
	EventCallback mOOBEventCallback = new EventCallback () {
		@Override
		public void onOOBDataArrived(String dataName, String dataValue) {
			msg ("(event) OOB Message: " + dataName + "=" + dataValue);
			
			// TODO: If the OOB message is that of the I/O layer just having connected, then kick off a Hybrid Session detection routine. 
			//		If that is successful then move forward with setup
			//		if unsuccessful, then continuously re-try as long as bt remains connected.
			if (dataName.equals(HybridSession.OOBMessageTypes.IO_STATE_CHANGE)) {
				// TODO: Kick off hardware-type detection. Hopefully it can use cached data as necessary to speed up successive executions. 
			}
			
			if (dataName.equals(HybridSession.OOBMessageTypes.AUTODETECT_SUMMARY)) {
				// TODO: Make sure autodetect was successful. If so, then move forward with the next step of being autonomous. 
			}
		}
	};

	/**
	 * This eventcallback will get executed (by Hybridsession) any time a debug/info message is generated by the code.   
	 */
	EventCallback mecbMsg = new EventCallback () {
		@Override
		public void onNewMessageArrived(String message) {
			msg ("(event)ASA: " + message);
		}
	};
	
	/**
	 * This eventcallback will get executed (by Hybridsession) any time a DP is decoded. 
	 */
	EventCallback mDPArrivedCallback = new EventCallback () {
		@Override
		public void onDPArrived(String DPN, String sDecodedData, int iDecodedData) {
			msg ("(event)DP: " + DPN + "=" + sDecodedData);
		}
	};
	
	/**
	 * - getStats returns the current generalStats object. AutoSessionAdapter is at the top of the pyramid, just above HybridSession.
	 * - getStats also gathers all stats from lower in the libvoyager stack and 
	 * @return - returns a generalStats object.
	 */
	public GeneralStats getStats () {
		
		// TODO: Add any necessary last-minute parameters now.
		
		// Merge stats from hybrid session. 
		if (hs != null) mgStats.merge("hs", hs.getStats());
		
		// TODO: Merge stats from any connectors such as a command socket or an events socket. 

		return mgStats;
	}
	
	
	/**
	 * Passes a message to the android log by default. 
	 * @param m - the message to send.
	 */
	private void msg (String m) {
		Log.d("AutoSessionAdapter",m);
	}
	
}
