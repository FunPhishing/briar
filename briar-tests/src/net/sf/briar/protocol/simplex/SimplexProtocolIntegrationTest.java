package net.sf.briar.protocol.simplex;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageVerifier;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.lifecycle.LifecycleModule;
import net.sf.briar.plugins.ImmediateExecutor;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.duplex.DuplexProtocolModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SimplexProtocolIntegrationTest extends BriarTestCase {

	private static final long CLOCK_DIFFERENCE = 60 * 1000L;
	private static final long LATENCY = 60 * 1000L;

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final TransportId transportId;
	private final byte[] initialSecret;
	private final long epoch;

	private Injector alice, bob;

	public SimplexProtocolIntegrationTest() throws Exception {
		super();
		transportId = new TransportId(TestUtils.getRandomId());
		// Create matching secrets for Alice and Bob
		initialSecret = new byte[32];
		new Random().nextBytes(initialSecret);
		long rotationPeriod = 2 * CLOCK_DIFFERENCE + LATENCY;
		epoch = System.currentTimeMillis() - 2 * rotationPeriod;
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
		alice = createInjector(aliceDir);
		bob = createInjector(bobDir);
	}

	private Injector createInjector(File dir) {
		return Guice.createInjector(new ClockModule(), new CryptoModule(),
				new DatabaseModule(), new LifecycleModule(),
				new ProtocolModule(), new SerialModule(),
				new TestDatabaseModule(dir), new SimplexProtocolModule(),
				new TransportModule(), new DuplexProtocolModule());
	}

	@Test
	public void testInjection() {
		DatabaseComponent aliceDb = alice.getInstance(DatabaseComponent.class);
		DatabaseComponent bobDb = bob.getInstance(DatabaseComponent.class);
		assertFalse(aliceDb == bobDb);
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		// Open Alice's database
		DatabaseComponent db = alice.getInstance(DatabaseComponent.class);
		db.open(false);
		// Start Alice's key manager
		KeyManager km = alice.getInstance(KeyManager.class);
		km.start();
		// Add Bob as a contact
		ContactId contactId = db.addContact();
		Endpoint ct = new Endpoint(contactId, transportId,
				epoch, CLOCK_DIFFERENCE, LATENCY, true);
		db.addEndpoint(ct);
		km.endpointAdded(ct, initialSecret.clone());
		// Send Bob a message
		String subject = "Hello";
		byte[] body = "Hi Bob!".getBytes("UTF-8");
		MessageFactory messageFactory = alice.getInstance(MessageFactory.class);
		Message message = messageFactory.createMessage(null, subject, body);
		db.addLocalPrivateMessage(message, contactId);
		// Create an outgoing simplex connection
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionRegistry connRegistry =
				alice.getInstance(ConnectionRegistry.class);
		ConnectionWriterFactory connFactory =
				alice.getInstance(ConnectionWriterFactory.class);
		ProtocolWriterFactory protoFactory =
				alice.getInstance(ProtocolWriterFactory.class);
		TestSimplexTransportWriter transport = new TestSimplexTransportWriter(
				out, Long.MAX_VALUE, false);
		ConnectionContext ctx = km.getConnectionContext(contactId, transportId);
		assertNotNull(ctx);
		OutgoingSimplexConnection simplex = new OutgoingSimplexConnection(db,
				connRegistry, connFactory, protoFactory, ctx, transport);
		// Write whatever needs to be written
		simplex.write();
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		// Clean up
		km.stop();
		db.close();
		// Return the contents of the simplex connection
		return out.toByteArray();
	}

	private void read(byte[] b) throws Exception {
		// Open Bob's database
		DatabaseComponent db = bob.getInstance(DatabaseComponent.class);
		db.open(false);
		// Start Bob's key manager
		KeyManager km = bob.getInstance(KeyManager.class);
		km.start();
		// Add Alice as a contact
		ContactId contactId = db.addContact();
		Endpoint ct = new Endpoint(contactId, transportId,
				epoch, CLOCK_DIFFERENCE, LATENCY, false);
		db.addEndpoint(ct);
		km.endpointAdded(ct, initialSecret.clone());
		// Set up a database listener
		MessageListener listener = new MessageListener();
		db.addListener(listener);
		// Fake a transport update from Alice
		Transport t = new Transport(transportId);
		TransportUpdate transportUpdate = new TransportUpdate(t,
				transportVersion);
		db.receiveTransportUpdate(contactId, transportUpdate);
		// Create a connection recogniser and recognise the connection
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		ConnectionRecogniser rec = bob.getInstance(ConnectionRecogniser.class);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		ConnectionContext ctx = rec.acceptConnection(transportId, tag);
		assertNotNull(ctx);
		// Create an incoming simplex connection
		MessageVerifier messageVerifier =
				bob.getInstance(MessageVerifier.class);
		ConnectionRegistry connRegistry =
				bob.getInstance(ConnectionRegistry.class);
		ConnectionReaderFactory connFactory =
				bob.getInstance(ConnectionReaderFactory.class);
		ProtocolReaderFactory protoFactory =
				bob.getInstance(ProtocolReaderFactory.class);
		TestSimplexTransportReader transport =
				new TestSimplexTransportReader(in);
		IncomingSimplexConnection simplex = new IncomingSimplexConnection(
				new ImmediateExecutor(), new ImmediateExecutor(),
				messageVerifier, db, connRegistry, connFactory, protoFactory,
				ctx, transport);
		// No messages should have been added yet
		assertFalse(listener.messagesAdded);
		// Read whatever needs to be read
		simplex.read();
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		assertTrue(transport.getRecognised());
		// The private message from Alice should have been added
		assertTrue(listener.messagesAdded);
		// Clean up
		km.stop();
		db.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static class MessageListener implements DatabaseListener {

		private boolean messagesAdded = false;

		public void eventOccurred(DatabaseEvent e) {
			if(e instanceof MessageAddedEvent) messagesAdded = true;
		}
	}
}
