import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;

public class ChessClient {
    private static Socket socket;

    public static void startClient() {
        try {
            socket = new Socket("localhost", 3000);
            System.out.println("Connected to server!");
            
            SwingUtilities.invokeLater(() -> new ChessBoard(false, socket));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}