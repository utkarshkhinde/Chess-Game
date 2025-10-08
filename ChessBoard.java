import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ChessBoard extends JFrame {
    private static final int SIZE = 8;
    private static final Color LIGHT_BROWN = new Color(222, 184, 135);
    private static final Color DARK_BROWN = new Color(139, 69, 19);
    private Socket socket;
    private PrintWriter out;
    private boolean isServer;
    private JButton[][] board = new JButton[SIZE][SIZE];
    private String[][] pieces;
    private int[] selected = null;
    private boolean whiteTurn = true;
    private boolean wKingMoved = false, bKingMoved = false;
    private boolean[] wRookMoved = {false, false};
    private boolean[] bRookMoved = {false, false};
    private JLabel whiteTimerLabel = new JLabel("White: 20s", SwingConstants.CENTER);
    private JLabel blackTimerLabel = new JLabel("Black: 20s", SwingConstants.CENTER);
    private int whiteTime = 20, blackTime = 20;
    private boolean timerRunning = true;

    public ChessBoard(boolean isServer, Socket socket) {
        this.isServer = isServer;
        this.socket = socket;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        initializeBoard();
        startTimerThread();
        new Thread(() -> listenForMoves()).start();
    }

    private void initializeBoard() {

        pieces = new String[][]{
            {"br.png","bn.png","bb.png","bq.png","bk.png","bb.png","bn.png","br.png"},
            {"bp.png","bp.png","bp.png","bp.png","bp.png","bp.png","bp.png","bp.png"},
            {null,null,null,null,null,null,null,null},
            {null,null,null,null,null,null,null,null},
            {null,null,null,null,null,null,null,null},
            {null,null,null,null,null,null,null,null},
            {"wp.png","wp.png","wp.png","wp.png","wp.png","wp.png","wp.png","wp.png"},
            {"wr.png","wn.png","wb.png","wq.png","wk.png","wb.png","wn.png","wr.png"}
        };

        String role = isServer ? "White" : "Black";
        JFrame frame = new JFrame("Chess - " + role + " Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 650);
        frame.setLayout(new BorderLayout());

        JPanel timerPanel = new JPanel(new GridLayout(1, 2));
        whiteTimerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        blackTimerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        timerPanel.add(whiteTimerLabel);
        timerPanel.add(blackTimerLabel);
        frame.add(timerPanel, BorderLayout.NORTH);

        JLabel statusLabel = new JLabel("You are playing as: " + role + " | Current turn: White", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        frame.add(statusLabel, BorderLayout.SOUTH);

        JPanel boardPanel = new JPanel(new GridLayout(SIZE, SIZE));
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                JButton button = new JButton();
                button.setOpaque(true);
                button.setBackground((i + j) % 2 == 0 ? LIGHT_BROWN : DARK_BROWN);
                setIcon(button, pieces[i][j]);
                final int row = i, col = j;
                button.addActionListener(e -> handleClick(row, col));
                board[i][j] = button;
                boardPanel.add(button);
            }
        }
        frame.add(boardPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void listenForMoves() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split(",");
                int fr = Integer.parseInt(p[0]);
                int fc = Integer.parseInt(p[1]);
                int tr = Integer.parseInt(p[2]);
                int tc = Integer.parseInt(p[3]);
                SwingUtilities.invokeLater(() -> applyNetworkMove(fr, fc, tr, tc));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Host (Server) - White", "Join (Client) - Black"};
            int choice = JOptionPane.showOptionDialog(null, "Select Game Mode:\nServer plays as White\nClient plays as Black", "Chess Network",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, options, options[0]);

            if (choice == 0) {
                ChessServer.startServer();
            } else {
                ChessClient.startClient();
            }
        });
    }

    private void startTimerThread() {
        new Thread(() -> {
            while (timerRunning) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { }
                
                if (whiteTurn) {
                    whiteTime--;
                } else {
                    blackTime--;
                }
                
                SwingUtilities.invokeLater(() -> {
                    whiteTimerLabel.setText("White: " + whiteTime + "s");
                    blackTimerLabel.setText("Black: " + blackTime + "s");
                });
                
                // Handle time up
                if (whiteTime <= 0) {
                    timerRunning = false;
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "White's time is up! Black wins!");
                    });
                    break;
                }
                if (blackTime <= 0) {
                    timerRunning = false;
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Black's time is up! White wins!");
                    });
                    break;
                }
            }
        }).start();
    }

    private void resetTurnTimer() {
        if (whiteTurn) {
            whiteTime = 20; 
        } else {
            blackTime = 20; 
        }
        SwingUtilities.invokeLater(() -> {
            whiteTimerLabel.setText("White: " + whiteTime + "s");
            blackTimerLabel.setText("Black: " + blackTime + "s");
        });
    }

    private void setIcon(JButton button, String piece) {
        if (piece != null) {
            ImageIcon icon = new ImageIcon("images/" + piece);
            Image scaled = icon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
            button.setIcon(new ImageIcon(scaled));
        } else {
            button.setIcon(null);
        }
    }

    private void handleClick(int row, int col) {
        if (isServer && !whiteTurn) {
            JOptionPane.showMessageDialog(null, "Not your turn! Wait for Black to move.");
            return;
        }
        if (!isServer && whiteTurn) {
            JOptionPane.showMessageDialog(null, "Not your turn! Wait for White to move.");
            return;
        }

        resetHighlights();
        if (selected == null) {
            if (pieces[row][col] != null && isCorrectTurn(pieces[row][col])) {
                selected = new int[]{row, col};
                board[row][col].setBackground(Color.YELLOW);
                highlightMoves(row, col);
            }
        } else {
            int fromRow = selected[0], fromCol = selected[1];
            String movedPiece = pieces[fromRow][fromCol];
            if (fromRow == row && fromCol == col) { selected = null; resetHighlights(); return; }
            if (pieces[row][col] != null && pieces[row][col].startsWith(movedPiece.substring(0,1))) {
                selected = null; resetHighlights(); return;
            }
            if (isValidMove(fromRow, fromCol, row, col)) {
                String[][] backup = copyBoard(pieces);
                pieces[row][col] = movedPiece; pieces[fromRow][fromCol] = null;
                if (isInCheck(whiteTurn ? "w" : "b")) { 
                    pieces = backup; 
                    JOptionPane.showMessageDialog(null, "Cannot move into check!"); 
                } else {
                    setIcon(board[row][col], movedPiece); 
                    setIcon(board[fromRow][fromCol], null);
                    handleCastling(movedPiece, fromRow, fromCol, row, col);
                    handlePromotion(movedPiece, row, col);
                    whiteTurn = !whiteTurn;
                    resetTurnTimer();  
                    
                    if (isCheckmate(whiteTurn ? "w" : "b")) {
                        timerRunning = false;
                        JOptionPane.showMessageDialog(null, "Checkmate! " + (whiteTurn ? "Black" : "White") + " wins!");
                    } else if (isInCheck(whiteTurn ? "w" : "b")) {
                        JOptionPane.showMessageDialog(null, "Check to " + (whiteTurn ? "White" : "Black") + "!");
                    }
                    String move = fromRow + "," + fromCol + "," + row + "," + col;
                    if (out != null) {
                        out.println(move);
                    }
                }
            }
            selected = null;
            resetHighlights();
        }
    }

    private String[][] copyBoard(String[][] src) {
        String[][] copy = new String[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) System.arraycopy(src[i], 0, copy[i], 0, SIZE);
        return copy;
    }

    private void resetHighlights() {
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++)
            board[i][j].setBackground((i+j)%2==0 ? LIGHT_BROWN : DARK_BROWN);
    }

    private void highlightMoves(int row, int col) {
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++)
            if (isValidMove(row,col,i,j)) board[i][j].setBackground(Color.GREEN);
    }

    private boolean isCorrectTurn(String piece) { 
        return (whiteTurn && piece.startsWith("w")) || (!whiteTurn && piece.startsWith("b")); 
    }

    private boolean isValidMove(int fr, int fc, int tr, int tc) {
        String piece = pieces[fr][fc];
        if (piece == null) return false;

        switch (piece.charAt(1)) {
            case 'p': return validatePawn(fr, fc, tr, tc, piece.startsWith("w"));
            case 'r': return validateRook(fr, fc, tr, tc);
            case 'n': return validateKnight(fr, fc, tr, tc);
            case 'b': return validateBishop(fr, fc, tr, tc);
            case 'q': return validateQueen(fr, fc, tr, tc);
            case 'k': return validateKing(fr, fc, tr, tc);
        }
        return false;
    }

    private boolean validatePawn(int fr, int fc, int tr, int tc, boolean white) {
        int dir = white ? -1 : 1;
        if (fc == tc && pieces[tr][tc] == null) {
            if (tr - fr == dir) return true;
            if ((white && fr == 6 || !white && fr == 1) && tr - fr == 2 * dir && pieces[fr + dir][fc] == null)
                return true;
        }
        if (Math.abs(tc - fc) == 1 && tr - fr == dir && pieces[tr][tc] != null)
            return true;
        return false;
    }

    private boolean validateRook(int fr, int fc, int tr, int tc) {
        if (fr != tr && fc != tc) return false;
        return pathClear(fr, fc, tr, tc);
    }

    private boolean validateBishop(int fr, int fc, int tr, int tc) {
        if (Math.abs(fr - tr) != Math.abs(fc - tc)) return false;
        return pathClear(fr, fc, tr, tc);
    }

    private boolean validateQueen(int fr, int fc, int tr, int tc) {
        if (fr == tr || fc == tc || Math.abs(fr - tr) == Math.abs(fc - tc))
            return pathClear(fr, fc, tr, tc);
        return false;
    }

    private boolean validateKnight(int fr, int fc, int tr, int tc) {
        int dr = Math.abs(fr - tr), dc = Math.abs(fc - tc);
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
    }

    private boolean validateKing(int fr, int fc, int tr, int tc) {
        return Math.abs(fr - tr) <= 1 && Math.abs(fc - tc) <= 1;
    }

    private boolean pathClear(int fr, int fc, int tr, int tc) {
        int dr = Integer.compare(tr, fr), dc = Integer.compare(tc, fc);
        int r = fr + dr, c = fc + dc;
        while (r != tr || c != tc) {
            if (pieces[r][c] != null) return false;
            r += dr; c += dc;
        }
        return true;
    }

    private void handleCastling(String piece, int fr, int fc, int tr, int tc) {
        if (piece.equals("wk.png")) {
            wKingMoved = true;
            if (fc == 4 && tc == 6) {
                pieces[7][5] = pieces[7][7];
                pieces[7][7] = null;
                setIcon(board[7][5], pieces[7][5]);
                setIcon(board[7][7], null);
                wRookMoved[1] = true;
            }
            if (fc == 4 && tc == 2) {
                pieces[7][3] = pieces[7][0];
                pieces[7][0] = null;
                setIcon(board[7][3], pieces[7][3]);
                setIcon(board[7][0], null);
                wRookMoved[0] = true;
            }
        }
        if (piece.equals("bk.png")) {
            bKingMoved = true;
            if (fc == 4 && tc == 6) {
                pieces[0][5] = pieces[0][7];
                pieces[0][7] = null;
                setIcon(board[0][5], pieces[0][5]);
                setIcon(board[0][7], null);
                bRookMoved[1] = true;
            }
            if (fc == 4 && tc == 2) {
                pieces[0][3] = pieces[0][0];
                pieces[0][0] = null;
                setIcon(board[0][3], pieces[0][3]);
                setIcon(board[0][0], null);
                bRookMoved[0] = true;
            }
        }
    }

    private void handlePromotion(String piece, int row, int col) {
        if (piece.equals("wp.png") && row == 0) {
            pieces[row][col] = "wq.png";
            setIcon(board[row][col], "wq.png");
        }
        if (piece.equals("bp.png") && row == 7) {
            pieces[row][col] = "bq.png";
            setIcon(board[row][col], "bq.png");
        }
    }

    private boolean isInCheck(String color) {
        int kingRow = -1, kingCol = -1;
        String king = color + "k.png";
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                if (king.equals(pieces[i][j])) { kingRow = i; kingCol = j; }

        if (kingRow == -1) return true;
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++) {
                String p = pieces[i][j];
                if (p != null && !p.startsWith(color)) {
                    if (isValidMove(i, j, kingRow, kingCol))
                        return true;
                }
            }
        return false;
    }

    private boolean isCheckmate(String color) {
        if (!isInCheck(color)) return false;

        for (int fr = 0; fr < SIZE; fr++) {
            for (int fc = 0; fc < SIZE; fc++) {
                String piece = pieces[fr][fc];
                if (piece == null || !piece.startsWith(color)) continue;

                for (int tr = 0; tr < SIZE; tr++) {
                    for (int tc = 0; tc < SIZE; tc++) {
                        if (isValidMove(fr, fc, tr, tc)) {
                            String[][] backup = copyBoard(pieces);
                            pieces[tr][tc] = piece;
                            pieces[fr][fc] = null;
                            boolean stillCheck = isInCheck(color);
                            pieces = backup;
                            if (!stillCheck) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public void applyNetworkMove(int fr, int fc, int tr, int tc) {
        String movedPiece = pieces[fr][fc];
        pieces[tr][tc] = movedPiece;
        pieces[fr][fc] = null;
        setIcon(board[tr][tc], movedPiece);
        setIcon(board[fr][fc], null);
        whiteTurn = !whiteTurn;
        resetTurnTimer(); 
    }
}