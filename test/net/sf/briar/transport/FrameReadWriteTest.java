package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FrameReadWriteTest extends TestCase {

	private final CryptoComponent crypto;
	private final Cipher ivCipher, frameCipher;
	private final SecretKey ivKey, frameKey, macKey;
	private final Mac mac;
	private final Random random;
	private final byte[] secret = new byte[100];
	private final int transportId = 999;
	private final long connection = 1234L;

	public FrameReadWriteTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		ivCipher = crypto.getIvCipher();
		frameCipher = crypto.getFrameCipher();
		// Since we're sending frames to ourselves, we only need outgoing keys
		ivKey = crypto.deriveOutgoingIvKey(secret);
		frameKey = crypto.deriveOutgoingFrameKey(secret);
		macKey = crypto.deriveOutgoingMacKey(secret);
		mac = crypto.getMac();
		random = new Random();
	}

	@Test
	public void testInitiatorWriteAndRead() throws Exception {
		testWriteAndRead(true);
	}

	@Test
	public void testResponderWriteAndRead() throws Exception {
		testWriteAndRead(false);
	}

	private void testWriteAndRead(boolean initiator) throws Exception {
		// Calculate the expected ciphertext for the IV
		byte[] iv = IvEncoder.encodeIv(initiator, transportId, connection);
		assertEquals(IV_LENGTH, iv.length);
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] encryptedIv = ivCipher.doFinal(iv);
		assertEquals(IV_LENGTH, encryptedIv.length);
		// Generate two random frames
		byte[] frame = new byte[12345];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				initiator, transportId, connection, ivCipher, frameCipher,
				ivKey, frameKey);
		mac.init(macKey);
		ConnectionWriter writer = new ConnectionWriterImpl(encrypter, mac);
		OutputStream out1 = writer.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		// Read the frames back
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		byte[] recoveredIv = new byte[IV_LENGTH];
		assertEquals(IV_LENGTH, in.read(recoveredIv));
		assertTrue(Arrays.equals(encryptedIv, recoveredIv));
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in,
				initiator, transportId, connection, frameCipher, frameKey);
		ConnectionReader reader = new ConnectionReaderImpl(decrypter, mac);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[frame.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		assertTrue(Arrays.equals(frame, recovered));
		byte[] recovered1 = new byte[frame1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		assertTrue(Arrays.equals(frame1, recovered1));
	}
}
