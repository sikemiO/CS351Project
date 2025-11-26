import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

// connect to local host then connect to client and port
public class Client {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port, using default 5000.");
                port = 5000;
            }
        }

        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port);
             Scanner serverIn = new Scanner(socket.getInputStream());
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("input option");

            // Thread to read from server and print to console
            Thread readerThread = new Thread(() -> {
                try {
                    while (serverIn.hasNextLine()) {
                        String line = serverIn.nextLine();
                        System.out.println(line);
                    }
                } catch (Exception e) {
                    // connection probably closed
                }
                System.out.println("Disconnected from server.");
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // main thread to read from keyboard and send to server
            Scanner userIn = new Scanner(System.in);
            while (true) {
                if (!userIn.hasNextLine()) {
                    break;
                }
                String line = userIn.nextLine();

                if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("/exit")) {
                    System.out.println("Closing connection...");
                    break;
                }

                serverOut.println(line);
            }

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
}
