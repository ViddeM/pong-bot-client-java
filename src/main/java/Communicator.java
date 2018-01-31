import java.io.*;
import java.net.Socket;

import java.net.InetAddress;

import java.lang.System;

import communication.ClientId;
import communication.ClientName;
import communication.SendablePaddleState;

import com.google.gson.Gson;

import pong.GameState;

public class Communicator extends Thread {
	
	private final Gson gson = new Gson();

	private final Store store;
	private final Thread collectorThread, pusherThread;
	private final Socket socket;

	private final String NAME;
	private int clientId;

	public Communicator(Store store, int port, InetAddress inetAddress, String name) throws IOException {
		this.store = store;
		this.socket = new Socket(inetAddress, port);
		this.NAME = name;
		setup();
		this.collectorThread = new Thread(new Collector(socket.getInputStream()));
		this.pusherThread = new Thread(new Pusher(socket.getOutputStream(), this.clientId));
	}

	public void start(){
		collectorThread.start();
		pusherThread.start();
	}
	
	private void setup() {
		try {
			BufferedReader bis = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

			// Receive client id
			StringBuilder sb = new StringBuilder();
			sb.append(bis.readLine());
			System.out.println("Received: " + sb.toString());
			ClientId clientId = gson.fromJson(sb.toString(), ClientId.class);
			this.clientId = clientId.id;
			
			// Respond with client name
			dos.writeBytes(gson.toJson(new ClientName(NAME)) + "\n");
			System.out.println("Sent: " + gson.toJson(new ClientName(NAME)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private class Collector implements Runnable {

		private final BufferedReader INPUT_STREAM;

		private Collector(InputStream stream) {
			this.INPUT_STREAM = new BufferedReader(new InputStreamReader(stream));
		}

		@Override
		public void run() {
			GameState newGameState;
			while (true) {
				String input;
				try {
					if(INPUT_STREAM.ready()) {
						input = read();
						newGameState = gson.fromJson(input, GameState.class);
						store.setGameState(newGameState);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		/**
		 * Reads data from the server.
		 * 
		 * @return a String of read data.
		 */
		private String read() {
			StringBuilder sb = new StringBuilder();
			INPUT_STREAM.lines().forEach(sb::append);
			return sb.toString();
		}
		
	}

	private class Pusher implements Runnable {

		private final DataOutputStream OUTPUT_STREAM;
		private final int ID;

		private Pusher(OutputStream stream) {
			this.OUTPUT_STREAM = new DataOutputStream(stream);
			this.ID = id;
		}

		@Override
		public void run() {
			String data;
			while(true) {
				data = gson.toJson(new SendablePaddleState(store.getDesiredPaddleState(), new ClientId(ID)));
				send(data + "\n");
				try{
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
			
		}
		
		/**
		 * Sends data to the server.
		 * 
		 * @param data
		 * @return true if the data was sent correctly, else false.
		 */
		private boolean send(String data) {
			try {
				OUTPUT_STREAM.writeBytes(data);
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
	}
}
