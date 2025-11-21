import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.Scanner;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BankService bankService;
    private final Set<String> activeUsers;

    public ClientHandler(Socket socket, BankService bankService, Set<String> activeUsers) {
        this.socket = socket;
        this.bankService = bankService;
        this.activeUsers = activeUsers;
    }


    @Override
    public void run() {
        String currentUser = null;

        try {
            Scanner in = new Scanner(socket.getInputStream());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



    }
}
