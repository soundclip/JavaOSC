/*
 * Copyright (C) 2003, C. Ramakrishnan / Auracle.
 * All rights reserved.
 *
 * This code is licensed under the BSD 3-Clause license.
 * See file LICENSE (or LICENSE.html) for more information.
 */

package com.illposed.osc;

import java.util.Date;
import java.util.List;

import com.illposed.osc.utility.OSCByteArrayToJavaConverter;

/**
 * @author Chandrasekhar Ramakrishnan
 */
public class OSCByteArrayToJavaConverterTest extends junit.framework.TestCase {

	OSCByteArrayToJavaConverter converter;

	/**
	 * Run the OSCByteArrayToJavaConverter through its paces
	 */
	public OSCByteArrayToJavaConverterTest() {
	}

	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		converter = new OSCByteArrayToJavaConverter();
	}

	/**
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	protected void tearDown() throws Exception {

	}

	public void testReadSimplePacket() throws Exception {
		byte[] bytes = {47, 115, 99, 47, 114, 117, 110, 0, 44, 0, 0, 0};
		OSCMessage packet = (OSCMessage) converter.convert(bytes, bytes.length);
		if (!packet.getAddress().equals("/sc/run"))
			fail("Address should be /sc/run, but is " + packet.getAddress());
	}

	public void testReadComplexPacket() throws Exception {
		byte[] bytes = {0x2F, 0x73, 0x5F, 0x6E, 0x65, 0x77, 0, 0, 0x2C, 0x69, 0x73, 0x66, 0, 0, 0, 0, 0, 0, 0x3, (byte) 0xE9, 0x66, 0x72, 0x65, 0x71, 0, 0, 0, 0, 0x43, (byte) 0xDC, 0, 0};

		OSCMessage packet = (OSCMessage) converter.convert(bytes, bytes.length);
		if (!packet.getAddress().equals("/s_new"))
			fail("Address should be /s_new, but is " + packet.getAddress());
		List<Object> arguments = packet.getArguments();
		if (arguments.size() != 3)
			fail("Num arguments should be 3, but is " + arguments.size());
		if (!(new Integer(1001).equals(arguments.get(0))))
			fail("Argument 1 should be 1001, but is " + arguments.get(0));
		if (!("freq".equals(arguments.get(1))))
			fail("Argument 2 should be freq, but is " + arguments.get(1));
		if (!(new Float(440.0).equals(arguments.get(2))))
			fail("Argument 3 should be 440.0, but is " + arguments.get(2));
	}

	public void testReadBundle() throws Exception {
		byte[] bytes = {0x23, 0x62, 0x75, 0x6E, 0x64, 0x6C, 0x65, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0x0C, 0X2F, 0x74, 0x65, 0x73, 0x74, 0, 0, 0, 0x2C, 0, 0, 0};

		OSCBundle bundle = (OSCBundle) converter.convert(bytes, bytes.length);
		if (!bundle.getTimestamp().equals(new Date(0)))
			fail("Timestamp should be 0, but is " + bundle.getTimestamp());
		List<OSCPacket> packets = bundle.getPackets();
		if (packets.size() != 1)
			fail("Num packets should be 1, but is " + packets.size());
		OSCMessage message = (OSCMessage) packets.get(0);
		if (!("/test".equals(message.getAddress())))
			fail("Address of message should be /test, but is " + message.getAddress());
	}
}
