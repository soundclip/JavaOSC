/*
 * Copyright (C) 2001, C. Ramakrishnan / Illposed Software.
 * All rights reserved.
 *
 * This code is licensed under the BSD 3-Clause license.
 * See file LICENSE (or LICENSE.html) for more information.
 */

package com.illposed.osc.transport.udp;

import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCPacketDispatcher;
import com.illposed.osc.OSCParserFactory;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.OSCSerializerFactory;
import com.illposed.osc.SimpleOSCMessageListener;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class OSCPortTest {

	private static final long WAIT_FOR_SOCKET_CLOSE = 30;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	private OSCPortOut sender;
	private OSCPortIn  receiver;

	private void reSetUp(
			final int portSenderOut,
			final int portSenderIn,
			final int portReceiverOut,
			final int portReceiverIn)
			throws Exception
	{
		final SocketAddress senderOutAddress = new InetSocketAddress(portSenderOut);
		final SocketAddress senderInAddress = new InetSocketAddress(portSenderIn);
		final SocketAddress receiverOutAddress = new InetSocketAddress(portReceiverOut);
		final SocketAddress receiverInAddress = new InetSocketAddress(portReceiverIn);

		if (receiver != null) {
			receiver.close();
		}
		receiver = new OSCPortIn(
				OSCParserFactory.createDefaultFactory(),
				new OSCPacketDispatcher(),
				receiverInAddress,
				senderInAddress);

		if (sender != null) {
			sender.close();
		}
		sender = new OSCPortOut(
				OSCSerializerFactory.createDefaultFactory(),
				receiverOutAddress,
				senderOutAddress);
	}

	private void reSetUp(final int portSender, final int portReceiver) throws Exception {
		reSetUp(portSender, portSender, portReceiver, portReceiver);
	}

	private void reSetUp(final int portReceiver) throws Exception {
		reSetUp(0, portReceiver);
	}

	@Before
	public void setUp() throws Exception {
		reSetUp(OSCPort.defaultSCOSCPort());
	}

	private void reSetUpDifferentPorts() throws Exception {
		reSetUp(OSCPort.defaultSCOSCPort(), OSCPort.defaultSCOSCPort() + 1);
	}

	private void reSetUpDifferentSender() throws Exception {
		reSetUp(
				OSCPort.defaultSCOSCPort(),
				OSCPort.defaultSCOSCPort() + 1,
				OSCPort.defaultSCOSCPort() + 2,
				OSCPort.defaultSCOSCPort() + 2);
	}

	private void reSetUpDifferentReceiver() throws Exception {
		reSetUp(
				OSCPort.defaultSCOSCPort(),
				OSCPort.defaultSCOSCPort(),
				OSCPort.defaultSCOSCPort() + 1,
				OSCPort.defaultSCOSCPort() + 2);
	}

	@After
	public void tearDown() throws Exception {

		try {
			if (receiver.isConnected()) { // HACK This should not be required, as DatagramChannel#disconnect() is supposed to have no effect if a it is not connected, but in certain tests, removing this if clause makes the disconnect cal hang forever; coudl even be a JVM bug -> we should report that (requires a minimal example first, though)
				receiver.disconnect();
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
		try {
			sender.disconnect();
		} catch (final IOException ex) {
			ex.printStackTrace();
		}

		try {
			receiver.close();
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
		try {
			sender.close();
		} catch (final IOException ex) {
			ex.printStackTrace();
		}

		// wait a bit after closing the receiver,
		// because (some) operating systems need some time
		// to actually close the underlying socket
		Thread.sleep(WAIT_FOR_SOCKET_CLOSE);
	}

	@Test
	public void testSocketClose() throws Exception {

		// close the underlying sockets
		receiver.close();
		sender.close();

		// make sure the old receiver is gone for good
		Thread.sleep(WAIT_FOR_SOCKET_CLOSE);

		// check if the underlying sockets were closed
		// NOTE We can have many (out-)sockets sending
		//   on the same address and port,
		//   but only one receiving per each such tuple.
		sender = new OSCPortOut();
		receiver = new OSCPortIn(OSCPort.defaultSCOSCPort());
	}

	@Test
	public void testSocketAutoClose() throws Exception {

		// DANGEROUS! here we forget to close the underlying sockets!
		receiver = null;
		sender = null;

		// make sure the old receiver is gone for good
		System.gc();
		Thread.sleep(WAIT_FOR_SOCKET_CLOSE);

		// check if the underlying sockets were closed
		// NOTE We can have many (out-)sockets sending
		//   on the same address and port,
		//   but only one receiving per each such tuple.
		sender = new OSCPortOut();
		receiver = new OSCPortIn(OSCPort.defaultSCOSCPort());
	}

	@Test
	public void testPorts() throws Exception {

		Assert.assertEquals("Bad default SuperCollider OSC port",
				57110, OSCPort.defaultSCOSCPort());
		Assert.assertEquals("Bad default SuperCollider Language OSC port",
				57120, OSCPort.defaultSCLangOSCPort());

		InetSocketAddress remoteAddress = ((InetSocketAddress) sender.getRemoteAddress());
		Assert.assertEquals("Bad default port with ctor()",
				57110, remoteAddress.getPort());

		sender.close();
		sender = new OSCPortOut(InetAddress.getLocalHost());
		remoteAddress = ((InetSocketAddress) sender.getRemoteAddress());
		Assert.assertEquals("Bad default port with ctor(address)",
				57110, remoteAddress.getPort());

		sender.close();
		sender = new OSCPortOut(InetAddress.getLocalHost(), 12345);
		remoteAddress = ((InetSocketAddress) sender.getRemoteAddress());
		Assert.assertEquals("Bad port with ctor(address, port)",
				12345, remoteAddress.getPort());
	}

	@Test
	public void readWriteReadData400() throws Exception {
		readWriteReadData(400);
	}

	@Test
	public void readWriteReadData600() throws Exception {
		// common minimal maximum UDP buffer size (MTU) is 5xx Bytes
		readWriteReadData(600);
	}

	@Test
	public void readWriteReadData1400() throws Exception {
		readWriteReadData(1400);
	}

	@Test
	public void readWriteReadData2000() throws Exception {
		// default maximum UDP buffer size (MTU) is ~1500 Bytes
		readWriteReadData(2000);
	}

	@Test
	public void readWriteReadData50000() throws Exception {
		readWriteReadData(50000);
	}

	@Test(expected=IOException.class)
	public void readWriteReadData70000() throws Exception {
		// theoretical maximum UDP buffer size (MTU) is 2^16 - 1 = 65535 Bytes
		readWriteReadData(70000);
	}

	private void readWriteReadData(final int sizeInBytes)
			throws Exception
	{
		tearDown();

		final int portSender = 6666;
		final int portReceiver = 7777;

//		final SocketAddress senderSocket = new InetSocketAddress(0);
		final SocketAddress senderSocket = new InetSocketAddress(InetAddress.getLocalHost(), portSender);
//		final SocketAddress receiverSocket = new InetSocketAddress(0);
		final SocketAddress receiverSocket = new InetSocketAddress(InetAddress.getLocalHost(), portReceiver);


		DatagramChannel senderChannel = null;
		DatagramChannel receiverChannel = null;
		try {
			senderChannel = DatagramChannel.open();
			senderChannel.socket().bind(senderSocket);
			senderChannel.socket().setReuseAddress(true);

			receiverChannel = DatagramChannel.open();
			receiverChannel.socket().bind(receiverSocket);
			receiverChannel.socket().setReuseAddress(true);

			senderChannel.connect(receiverSocket);
			receiverChannel.connect(senderSocket);

			final byte[] sourceArray = new byte[sizeInBytes];
			final byte[] targetArray = new byte[sizeInBytes];

			new Random().nextBytes(sourceArray);

			readWriteReadData(senderChannel, sourceArray, receiverChannel, targetArray, sizeInBytes, null);
		} finally {
			senderChannel.close();
			receiverChannel.close();
		}
	}

	private void readWriteReadData(
			final DatagramChannel sender,
			final byte[] sourceArray,
			final DatagramChannel receiver,
			byte[] targetArray,
			final int dataSize,
			final String methodName)
			throws IOException
	{
		// write
		final ByteBuffer sourceBuf = ByteBuffer.wrap(sourceArray);
		Assert.assertEquals(dataSize, sender.write(sourceBuf));

		// read
		final ByteBuffer targetBuf = ByteBuffer.wrap(targetArray);

		int count = 0;
		int total = 0;
		final long beginTime = System.currentTimeMillis();
		while (total < dataSize && (count = receiver.read(targetBuf)) != -1) {
			total = total + count;
			// 3s timeout to avoid dead loop
			if (System.currentTimeMillis() - beginTime > 3000) {
				break;
			}
		}

		Assert.assertEquals(dataSize, total);
		Assert.assertEquals(targetBuf.position(), total);
		targetBuf.flip();
		targetArray = targetBuf.array();
		for (int i = 0; i < targetArray.length; i++) {
			Assert.assertEquals(sourceArray[i], targetArray[i]);
		}
	}

	/**
	 * This test would always fail (tested on 64bit Ubuntu Linux),
	 * because we try to bind to an IPv4 address, but after successful binding,
	 * the local address is an IPv6 one.
	 * This seems to be some Linux oddity.
	 * @throws Exception if anything goes wrong
	 */
//	@Test
	public void testBindChannel() throws Exception {

		final SocketAddress bindAddress = new InetSocketAddress(OSCPort.defaultSCOSCPort());

		final DatagramChannel channel = DatagramChannel.open();
		channel.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true); // XXX This is only available since Java 1.7
		channel.socket().bind(bindAddress);

		Assert.assertEquals(bindAddress, channel.getLocalAddress());
	}

	@Test
	public void testStart() throws Exception {

		OSCMessage mesg = new OSCMessage("/sc/stop");
		sender.send(mesg);
	}

	@Test
	public void testMessageWithArgs() throws Exception {

		List<Object> args = new ArrayList<Object>(2);
		args.add(3);
		args.add("hello");
		OSCMessage mesg = new OSCMessage("/foo/bar", args);
		sender.send(mesg);
	}

	@Test
	public void testBundle() throws Exception {

		List<Object> args = new ArrayList<Object>(2);
		args.add(3);
		args.add("hello");
		List<OSCPacket> msgs = new ArrayList<OSCPacket>(1);
		msgs.add(new OSCMessage("/foo/bar", args));
		OSCBundle bundle = new OSCBundle(msgs);
		sender.send(bundle);
	}

	@Test
	public void testBundle2() throws Exception {

		final List<Object> arguments = new ArrayList<Object>(2);
		arguments.add(3);
		arguments.add("hello");
		final OSCMessage mesg = new OSCMessage("/foo/bar", arguments);
		OSCBundle bundle = new OSCBundle();
		bundle.addPacket(mesg);
		sender.send(bundle);
	}

	@Test
	public void testReceiving() throws Exception {

		OSCMessage mesg = new OSCMessage("/message/receiving");
		SimpleOSCMessageListener listener = new SimpleOSCMessageListener();
		receiver.getDispatcher().addListener(new OSCPatternAddressMessageSelector("/message/receiving"),
				listener);
		receiver.startListening();
		sender.send(mesg);
		Thread.sleep(100); // wait a bit
		receiver.stopListening();
		if (!listener.isMessageReceived()) {
			Assert.fail("Message was not received");
		}
	}

	@Test
	public void testBundleReceiving() throws Exception {

		OSCBundle bundle = new OSCBundle();
		bundle.addPacket(new OSCMessage("/bundle/receiving"));
		SimpleOSCMessageListener listener = new SimpleOSCMessageListener();
		receiver.getDispatcher().addListener(new OSCPatternAddressMessageSelector("/bundle/receiving"),
				listener);
		receiver.startListening();
		sender.send(bundle);
		Thread.sleep(100); // wait a bit
		receiver.stopListening();
		if (!listener.isMessageReceived()) {
			Assert.fail("Message was not received");
		}
		if (!listener.getReceivedTimestamp().equals(bundle.getTimestamp())) {
			Assert.fail("Message should have timestamp " + bundle.getTimestamp()
					+ " but has " + listener.getReceivedTimestamp());
		}
	}

	/**
	 * @param size the approximate size of the resulting, serialized OSC packet in bytes
	 */
	private void testReceivingBySize(final int size) throws Exception {

		final String address = "/message/sized";
		final int numIntegerArgs = (size - (((address.length() + 3 + 1) / 4) * 4)) / 5;
		final List<Object> args = new ArrayList<Object>(numIntegerArgs);
		final Random random = new Random();
		for (int ai = 0; ai < numIntegerArgs; ai++) {
			args.add(random.nextInt());
		}
		final OSCMessage message = new OSCMessage(address, args);
		final SimpleOSCMessageListener listener = new SimpleOSCMessageListener();
		receiver.getDispatcher().addListener(
				new OSCPatternAddressMessageSelector(message.getAddress()),
				listener);
		receiver.startListening();
		sender.send(message);
		Thread.sleep(100); // wait a bit
		receiver.stopListening();
		if (!listener.isMessageReceived()) {
			Assert.fail("Message was not received");
		}
		if (message.getArguments().size() != listener.getMessage().getArguments().size()) {
			Assert.fail("Message received #arguments differs from #arguments sent");
		}
		if (!message.getArguments().get(numIntegerArgs - 1).equals(
				listener.getMessage().getArguments().get(numIntegerArgs - 1)))
		{
			Assert.fail("Message received last argument '"
					+ message.getArguments().get(numIntegerArgs - 1)
					+ "' differs from the sent one '"
					+ listener.getMessage().getArguments().get(numIntegerArgs - 1)
					+ "'");
		}
	}

	@Test
	public void testReceivingBig() throws Exception {

		// Create a list of arguments of size 1500 bytes,
		// so the resulting UDP packet size is sure to be bigger then the default maximum,
		// which is 1500 bytes (including headers).
		testReceivingBySize(1500);
	}

	@Test(expected=OSCSerializeException.class)
	public void testReceivingHuge() throws Exception {

		// Create a list of arguments of size 66000 bytes,
		// so the resulting UDP packet size is sure to be bigger then the theoretical maximum,
		// which is 65k bytes (including headers).
		testReceivingBySize(66000);
	}

	@Test(expected=OSCSerializeException.class)
	public void testReceivingHugeConnectedOut() throws Exception {

		// Create a list of arguments of size 66000 bytes,
		// so the resulting UDP packet size is sure to be bigger then the theoretical maximum,
		// which is 65k bytes (including headers).
		sender.connect();
		testReceivingBySize(66000);
	}

	private void testBundleReceiving(final boolean shouldReceive) throws Exception {

		OSCBundle bundle = new OSCBundle();
		bundle.addPacket(new OSCMessage("/bundle/receiving"));
		SimpleOSCMessageListener listener = new SimpleOSCMessageListener();
		receiver.getDispatcher().addListener(new OSCPatternAddressMessageSelector("/bundle/receiving"),
				listener);
		receiver.startListening();

		sender.send(bundle);
		Thread.sleep(100); // wait a bit
		receiver.stopListening();

//		receiver.disconnect();

		if (shouldReceive && !listener.isMessageReceived()) {
			Assert.fail("Message was not received");
		} else if (!shouldReceive && listener.isMessageReceived()) {
			Assert.fail("Message was received while it should not have!");
		}
	}

	@Test
	public void testBundleReceivingConnectedOut() throws Exception {
//		reSetUpDifferentPorts();

		sender.connect();
		testBundleReceiving(true);
	}

	@Test
	public void testBundleReceivingConnectedOutDifferentSender() throws Exception {
		reSetUpDifferentSender();

		sender.connect();
		testBundleReceiving(true);
	}

	@Test
	public void testBundleReceivingConnectedOutDifferentReceiver() throws Exception {
		reSetUpDifferentReceiver();

		sender.connect();
		testBundleReceiving(false);
	}

	@Test
	public void testBundleReceivingConnectedIn() throws Exception {
		reSetUpDifferentPorts();

		receiver.connect();
		testBundleReceiving(true);
	}

	@Test
	public void testBundleReceivingConnectedInDifferentSender() throws Exception {
		reSetUpDifferentSender();

		receiver.connect();
		testBundleReceiving(false);
	}

	@Test
	public void testBundleReceivingConnectedInDifferentReceiver() throws Exception {
		reSetUpDifferentReceiver();

		receiver.connect();
		testBundleReceiving(false);
	}

	@Test
	public void testBundleReceivingConnectedBoth() throws Exception {
		reSetUpDifferentPorts();

		receiver.connect();
		sender.connect();
		testBundleReceiving(true);
	}
}
