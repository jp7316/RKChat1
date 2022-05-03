import java.io.*;
import java.net.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import netscape.javascript.JSObject;

import org.json.simple.*;

public class ChatClient extends Thread
{
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
		String user = null;
		while (user == "" || user == null || user == "\n") {
			System.out.println("Enter your username:");
			user = std_in.readLine();
		}
		new ChatClient(user);
	}

	public ChatClient(String name) throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;
		JSONObject login = new JSONObject();
		login.put("type", 0);
		login.put("sender", name);
		login.put("receiver", "server");
		login.put("content", "login");

		//DO NOT TOUCH
		// connect to the chat server
		try {
			System.out.println("[system] connecting to chat server ...");
			socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			System.out.println("[system] connected");

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
		//

		//send login info
		this.sendMessage(login.toJSONString(), out);

		// read from STDIN and send messages to the chat server
		BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
		/*String userInput;
		while ((userInput = std_in.readLine()) != null) { // read a line from the console
			this.sendMessage(userInput, out); // send the message to the chat server
		}*/

		//msg generator
		while (true) {
			String type;
			while (type != "1" || type != "2") {
				System.out.println("If the message is private enter 1 and enter 2 if it is public.");
				type = std_in.readLine();
			}
			String receiver;
			while (receiver == null || receiver == "" || receiver == "\n") {
				if (type == "1") {
					System.out.println("Enter the receiver's name.");
					receiver = std_in.readLine();
				} else {
					receiver = "all";
				}
			}
			String content;
			while (content == null || content == "" || content == "\n") {
				System.out.println("Enter your message.");
				content = std_in.readLine();
			}

			JSONObject jazon = new JSONObject();
			jazon.put("type", type);
			jazon.put("sender", name);
			jazon.put("receiver", receiver);
			jazon.put("content", content);

			this.sendMessage(jazon.toJSONString(), out);
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private void sendMessage(String message, DataOutputStream out) {
		try {
			out.writeUTF(message); // send the message to the chat server
			out.flush(); // ensure the message has been sent
		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		JSONparser prs = new JSONparser();
		try {
			String message;
			while ((message = this.in.readUTF()) != null) { // read new message
				JSONObject json = prs.parse(message);
				String bc;
				String type = json.get("type");
				if (type == "0") {
					bc = "login";
				} else if (type == "1") {
					bc = "private";
				} else if (type == "2") {
					bc = "public";
				} else {
					bc = "error";
				}
				System.out.printf("[%s](%s) %s\n", json.get("sender"), bc, json.get("content"));
				//System.out.println("[RKchat] " + message); // print the message to the console
			}
		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
