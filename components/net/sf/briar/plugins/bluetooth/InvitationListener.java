package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

class InvitationListener extends AbstractListener {

	private static final Logger LOG =
		Logger.getLogger(InvitationListener.class.getName());

	private final String uuid;

	private String url = null; // Locking: this

	InvitationListener(DiscoveryAgent discoveryAgent, String uuid) {
		super(discoveryAgent);
		this.uuid = uuid;
	}

	synchronized String waitForUrl() {
		while(!finished) {
			try {
				wait();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		return url;
	}

	public void deviceDiscovered(RemoteDevice device, DeviceClass deviceClass) {
		UUID[] uuids = new UUID[] { new UUID(uuid, false) };
		// Try to discover the services associated with the UUID
		try {
			discoveryAgent.searchServices(null, uuids, device, this);
			searches.incrementAndGet();
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for(ServiceRecord record : services) {
			// Does this service have a URL?
			String serviceUrl = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if(serviceUrl == null) continue;
			// Does this service have the UUID we're looking for?
			DataElement classIds = record.getAttributeValue(0x1);
			Object o = getDataElementValue(classIds);
			if(o instanceof Enumeration) {
				Enumeration<?> e = (Enumeration<?>) o;
				for(Object o1 : Collections.list(e)) {
					Object o2 = getDataElementValue(o1);
					if(o2 instanceof UUID) {
						UUID serviceUuid = (UUID) o2;
						if(uuid.equalsIgnoreCase(serviceUuid.toString())) {
							// The UUID matches - store the URL
							synchronized(this) {
								url = serviceUrl;
								finished = true;
								notifyAll();
								return;
							}
						}
					}
				}
			}
		}
	}
}
