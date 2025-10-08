import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
public class ChessServer {
    private static ServerSocket serverSocket;
    private static Socket socket;

    public static void startServer() {
        try {
            serverSocket = new ServerSocket(3000);
            System.out.println("Server started. Waiting for a client...");
            socket = serverSocket.accept();
            System.out.println("Client connected!");
            
            SwingUtilities.invokeLater(() -> new ChessBoard(true, socket));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}