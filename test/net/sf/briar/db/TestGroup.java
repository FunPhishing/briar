package net.sf.briar.db;

import java.io.IOException;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.serial.Writer;

public class TestGroup implements Group {

	private final GroupId id;
	private final String name;
	private final byte[] publicKey;

	public TestGroup(GroupId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	public GroupId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public void writeTo(Writer w) throws IOException {
		throw new UnsupportedOperationException();
	}
}
