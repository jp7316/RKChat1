import java.io.*;
import java.net.*;
import java.util.*;
import org.json.simple.*;

public class ChatServer {

	protected int serverPort = 1234;
	protected List<Socket> clients = new ArrayList<Socket>(); // list of clients
	protected Map<String, Socket> clientNames = new HashMap<>();
	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String content, String sender) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				JSONObject msg = new JSONObject();
				msg.put("type", 2);
				msg.put("sender", sender);
				msg.put("receiver", "all");
				msg.put("content", content);
				String message = msg.toJSONString();
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	//send an error msg back to the same socket (false == login, true == user_unknown)
	public void sendErrorMsg(Socket socket, boolean type) {
		try {
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			JSONObject msg = new JSONObject();
			msg.put("type", 4);
			msg.put("sender", "SERVER");
			if (type) {
				msg.put("content", "Error: receiver unknown");
			} else {
				msg.put("content", "Error: user with this username already exists");
			}
			String errMsg = msg.toJSONString();
			out.writeUTF(errMsg);
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}

	//send to a specific receiver
	public void sendPrivateMsg(String name, String content, String sender) {
		if (!clientNames.containtsKey(name)) {
			sendErrorMsg(sender, true);
		} else {
			try {
				Socket socket = clientNames.get(name);
				JSONObject msg = new JSONObject();
				msg.put("type", 1);
				msg.put("sender", sender);
				msg.put("receiver", name);
				msg.put("content", content);
				String message = msg.toJSONString();
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
				out.writeUTF(message); 
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void removeClient(Socket socket) {
		synchronized(this) {
			clients.remove(socket);
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		JSONparser prs = new JSONparser();
		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket);
				return;
			}

			if (msg_received.length() == 0) // invalid message
				continue;

			JSONObject msgJSON = prs.parse(msg_received);
			String receiver = msgJSON.get("receiver");
			String sender = msgJSON.get("sender");
			String content = msgJSON.get("content");
			int type = (int) msgJSON.get("type");

			System.out.println("[RKchat] [" + this.socket.getPort() + "] : " + msg_received); // print the incoming message in the console

			try {
				//login handling
				if (type == 0) {
					if (this.server.clientNames.containsKey(sender)) {
						this.server.sendErrorMsg(this.socket, false);
					} else {
						this.server.clientNames.put(sender, this.socket);
					}
				//private msg
				} else if (type == 1) {
					this.server.sendPrivateMsg(receiver, content, sender);
				//public msg
				} else if (type == 2) {
					this.server.sendToAllClients(content, sender);
				}
			} catch (Exception e) {
				System.err.println("[system] there was a problem while sending the message to all clients");
				e.printStackTrace(System.err);
				continue;
			}
		}
	}
}
