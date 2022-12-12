package fi.utu.tech.telephonegame;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TransferQueue;
import fi.utu.tech.telephonegame.network.Network;
import fi.utu.tech.telephonegame.network.NetworkService;
import fi.utu.tech.telephonegame.network.Resolver;
import fi.utu.tech.telephonegame.network.Resolver.NetworkType;
import fi.utu.tech.telephonegame.network.Resolver.PeerConfiguration;
import fi.utu.tech.telephonegame.util.ConcurrentExpiringHashSet;

public class MessageBroker extends Thread {

	/*
	 * No need to change these variables
	 */
	private TransferQueue<Object> procQueue;
	private Network network;
	private Resolver resolver;
	private GuiIO gui_io;
	public Set<UUID> processedMessage = new HashSet<UUID>();
	// Defualt listening port
	private final int rootServerPort = 8050;
	// This might come into use
	private ConcurrentExpiringHashSet<UUID> prevMessages = new ConcurrentExpiringHashSet<UUID>(1000, 5000);

	/*
	 * No need to edit the constructor
	 */
	public MessageBroker(GuiIO gui_io) {
		this.gui_io = gui_io;
		network = new NetworkService();
		procQueue = network.getInputQueue();
	}

	/*
	 * In the process method you need to:
	 * 1. Test the type of the incoming object
	 * 2. Keep track of messages that are already processed by this node
	 * 3. Show the incoming message in the received message text area
	 * 4. Change the text and the color using the Refiner class
	 * 5. Set the new color to the color area
	 * 6. Show the refined message in the refined message text area
	 * 7. Return the processed message
	 */
	private Message process(Object procMessage) {
		if (!(procMessage instanceof Message)) {
			throw new IllegalArgumentException("incomingMessage cannot be null");
		}
		
		prevMessages.getId();
		gui_io.setReceivedMessage(((Throwable) procMessage).getMessage());
		Refiner refiner = new Refiner();
		Message refinedMessage = new Message(((Throwable) procMessage).getMessage(), ((Message) procMessage).getColor());



		refinedMessage.setMessage(refiner.refineText((((Throwable) procMessage).getMessage())));
		refinedMessage.setColor(refiner.refineColor(((Message) procMessage).getColor()));
		gui_io.setSignal(refinedMessage.getColor());
		gui_io.setRefinedMessage(refinedMessage.getMessage());

		return refinedMessage;
	}

	/*
	 * This run method will be executed in a separate thread automatically by the template
	 * 
	 * In the run method you need to check:
	 * 
	 * 1. If there are any incoming objects
	 * 2. When a new object is available it has to be processed using the process
	 * method
	 * 3. Send the processed message back to network
	 * 
	 */
	public void run() {
		synchronized(this) {
		while(true) {
			Object obj = network.getInputQueue();
			if (obj == null) {
				continue;
			}
			Message processedMessage = process(this);
			send(processedMessage);
		}
		}
	}

	/**
	 * Adds Message object to the sending queue to be processed by the network component
	 * You might need to make changes here
	 * @param message The Message object to be sent
	 */
	public void send(Message message) {
		network.postMessage(message); 
	}

	/**
	 * Wraps the String into a new Message object
	 * and adds it to the sending queue to be processed by the network component
	 * Called when sending a new message
	 * @param text The text to be wrapped and sent
	 */
	public void send(String text) {
		Message message = new Message(text, 0);
		this.send(message);
	}

	/*
	 * Do not edit anything below this point.
	 */

	/**
	 * Determines which peer to connect to (or if none)
	 * Called when user wants to "Discover and connect" or "Start waiting for peers"
	 * Calls the appropriate methods in Network object with correct arguments
	 * 
	 * @param netType
	 * @param rootNode
	 * @param rootIPAddress
	 */
	public void connect(NetworkType netType, boolean rootNode, String rootIPAddress) {
		resolver = new Resolver(netType, rootServerPort, rootIPAddress);
		if (rootNode) {
			System.out.println("Root node");
			// Use the default port for the server and start listening for peers
			network.startListening(rootServerPort);
			// As a root node, we are responsible for answering resolving requests - start resolver server
			resolver.startResolverServer();
			// No need to connect to anybody since we are the first node, the "root node"
		} else {
			System.out.println("Leaf node");
			try {
				// Broadcast a resolve request and wait for a resolver server (root node) to send peer configuration
				PeerConfiguration addresses = resolver.resolve();
				// Start listening for new peers on the port sent by the resolver server
				network.startListening(addresses.listeningPort);
				// Connect to a peer using addresses sent by the resolver server
				network.connect(addresses.peerAddr, addresses.peerPort);
			} catch (UnknownHostException | NumberFormatException e) {
				System.err.println("Peer discovery failed (maybe there are no root nodes or broadcast messages are not supported on current network)");
				gui_io.enableConnect();
			} catch (IOException e) {
				System.err.println("Error connecting to the peer");
				gui_io.enableConnect();
			}
		}
	}

}
