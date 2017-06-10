import Models.*;
import java.io.*;
import java.net.*;
import java.util.Vector;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class ClientGUI extends JFrame {

    private JTextArea text, logs;
    private JPanel panelGraczy, panelGry, panelBoczny, panelLogowania, panelDodatkowy;
    private JTextField host, input;
    private JButton btnHowToPlay, btnAddToServer, btnReady, btnLogon;
    private JLabel lbStatus;

    private String hostname = "localhost:2345";
    private int status = 0; // 0 - not connected, 1 - connected

    private PanelPlayer[] panelGracza = new PanelPlayer[Consts.MAX_PLAYERS];
    private int playerId;

    private Zadanie zadanie;
    private int typedChars = 0;

    private Client client;

    public ClientGUI() {
        super("Klient " + Consts.VERSION);
        setSize(880, 600);
        setMinimumSize(new Dimension(640, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // -- panel z graczami
        panelGraczy = new JPanel(new GridLayout(Consts.MAX_PLAYERS, 0));
        for (int i = 0; i < Consts.MAX_PLAYERS; i++) {
            panelGracza[i] = new PanelPlayer(i);
            panelGracza[i].setPreferredSize(new Dimension(40, 40));
            panelGraczy.add(panelGracza[i]);
        }

        // -- panel gry
        panelGry = new JPanel(new BorderLayout());
        panelGry.setBackground(Color.LIGHT_GRAY);

        text = new JTextArea();
        text.setText("");
        text.setWrapStyleWord(true);
        text.setLineWrap(true);
        text.setOpaque(false);
        text.setEditable(false);
        text.setFocusable(false);
        text.setFont(new Font("Verdana", Font.PLAIN, 22));
        text.setBorder(new EmptyBorder(8, 12, 8, 8));

        input = new JTextField();
        input.setFont(new Font("Verdana", Font.PLAIN, 26));
        input.setPreferredSize(new Dimension(450, 42));
        input.setEnabled(false);
        // ustawienie funkcji klawisza spacja, gdy pole tekstowe jest aktywne
        input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "space");
        input.getActionMap().put("space", new AbstractAction() {
            public void actionPerformed(ActionEvent evt) {

                // usunięcie pierwszego wyrazu z tekstu, gdy jest zgodny z napisanym w polu tekstowym
                String stringToCut = input.getText().trim();
                if (zadanie.ifEqualsGoNext(stringToCut)) {

                    // aktualizacja progresu i wysłanie go do serwera (docelowo do pozostałych klientów)
                    panelGracza[playerId].setProgressValue(zadanie.getProgress());
                    try {
                        client.sendToServer.writeObject(new Packet(Command.PROGRESS, playerId, zadanie.getProgress()));
                        client.sendToServer.flush();
                    } catch (IOException ex) {
                        addLog(ex.toString());
                    }

                    // aktywacja dostępności umiejętności, gdy osiągnięta została odpowiednia ilość punktów
                    boolean isChanged = false;
                    int idSkill = -1, ap = panelGracza[playerId].addActionPointAndGet();
                    if (ap == Variety.HIDE_INPUT_CONTENT.getCost()) {
                        idSkill = 0;
                        isChanged = true;
                    } else if (ap == Variety.REVERSE_WORDS_IN_TEXT.getCost()) {
                        idSkill = 1;
                        isChanged = true;
                    } else if (ap == Variety.SHUFFLE_CHARS_IN_WORDS.getCost()) {
                        idSkill = 2;
                        isChanged = true;
                    }

                    if (isChanged)
                        for (PanelPlayer pp : panelGracza)
                            if (pp.panelId != playerId && !pp.getNick().isEmpty())
                                pp.setSkillAvailability(idSkill, true);

                    // sprawdzenie czy użytkownik ukończył zadanie
                    if (zadanie.isSuccess) {
                        input.setEnabled(false);
                        try {
                            client.sendToServer.writeObject(new Packet(Command.WIN, playerId));
                            client.sendToServer.flush();
                        } catch (IOException ex) {
                            addLog(ex.toString());
                        }
                        text.setText("");
                        input.setText("");
                    } else {
                        typedChars += stringToCut.length() + 1;
                        text.setText(text.getText().substring(stringToCut.length() + 1));
                        input.setText("");
                    }
                }
            }
        });

        logs = new JTextArea();
        logs.setText("");
        logs.setWrapStyleWord(true);
        logs.setLineWrap(true);
        logs.setOpaque(false);
        logs.setEditable(false);
        logs.setFocusable(false);
        logs.setBorder(new EmptyBorder(4, 4, 4, 4));

        panelGry.add(text, BorderLayout.CENTER);
        panelGry.add(input, BorderLayout.SOUTH);

        // -- panel po prawej stronie
        panelBoczny = new JPanel(new BorderLayout());

        // ---- panel dane do logowania + informacje
        panelLogowania = new JPanel(new GridLayout(2, 2));
        panelLogowania.setBorder(new EmptyBorder(12, 10, 12, 10));

        host = new JTextField(hostname);
        host.setFont(new Font("Verdana", Font.PLAIN, 12));
        lbStatus = new JLabel("Status: niepołączony", SwingConstants.RIGHT);
        lbStatus.setForeground(Color.RED);

        panelLogowania.add(new JLabel("Serwer (host:port)"));
        panelLogowania.add(new JLabel(Consts.VERSION, SwingConstants.RIGHT));
        panelLogowania.add(host);
        panelLogowania.add(lbStatus);

        // ---- panel z przyciskami (prawy dolny róg)
        panelDodatkowy = new JPanel(new GridLayout(2, 2));

        Obsluga obsluga = new Obsluga();
        btnHowToPlay = new JButton("Jak grać?");
        btnHowToPlay.setEnabled(false);
        btnAddToServer = new JButton("Dodaj tekst do gry");
        btnAddToServer.addActionListener(obsluga);
        btnAddToServer.setEnabled(false);
        btnReady = new JButton("Gotowość");
        btnReady.addActionListener(obsluga);
        btnReady.setEnabled(false);
        btnLogon = new JButton("Połącz");
        btnLogon.setPreferredSize(new Dimension(42, 42));
        btnLogon.addActionListener(obsluga);

        panelDodatkowy.add(btnLogon);
        panelDodatkowy.add(btnAddToServer);
        panelDodatkowy.add(btnReady);
        panelDodatkowy.add(btnHowToPlay);

        // ---- panel (opakowanie) dla panelu z informacjami i przyciskami 
        JPanel opakowanie = new JPanel();
        opakowanie.setLayout(new BoxLayout(opakowanie, BoxLayout.Y_AXIS));

        opakowanie.add(panelLogowania);
        opakowanie.add(panelDodatkowy);

        panelBoczny.add(new JScrollPane(logs), BorderLayout.CENTER);
        panelBoczny.add(opakowanie, BorderLayout.SOUTH);

        // -- rozmieszczenie paneli
        add(panelGraczy, BorderLayout.NORTH);
        add(panelGry, BorderLayout.CENTER);
        add(panelBoczny, BorderLayout.EAST);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if ((status & Consts.CONNECTED) == Consts.CONNECTED) {
                    try {
                        client.sendToServer.writeObject(new Packet(Command.LOGOUT));
                        client.sendToServer.flush();
                    } catch (IOException ex) {
                        addLog(ex.toString());
                    }
                }
                setVisible(false);
                System.exit(0);
            }
        });
        setVisible(true);
    }

    // wyświetlenie popupa z możliwością dodania tekstu do aplikacji
    private void displayRequestWithTextPopup() {

        JTextArea ta = new JTextArea();
        ta.setLineWrap(true);
        ta.setPreferredSize(new Dimension(600, 300));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Treść zadania:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(ta), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(null, panel, "Dodaj prośbę z tekstem do serwera",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                client.sendToServer.writeObject(new Packet(Command.SEND_TEXT, ta.getText()));
                client.sendToServer.flush();
            } catch (IOException ex) {
                addLog(ex.toString());
            }
        } else {
            try {
                client.sendToServer.writeObject(new Packet(Command.SEND_TEXT, ""));
                client.sendToServer.flush();
            } catch (IOException ex) {
                addLog(ex.toString());
            }
        }
    }

    private class Obsluga implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            if (e.getSource() == btnAddToServer) {
                try {
                    client.sendToServer.writeObject(new Packet(Command.SEND_TEXT_REQUEST));
                    client.sendToServer.flush();
                } catch (IOException ex) {
                    addLog(ex.toString());
                }
            } else if (e.getSource() == btnReady) {
                try {
                    client.sendToServer.writeObject(new Packet(Command.CHANGE_READY));
                    client.sendToServer.flush();
                } catch (IOException ex) {
                    addLog(ex.toString());
                }
            } else if (e.getSource() == btnLogon) {
                if ((status & Consts.CONNECTED) == Consts.CONNECTED) {
                    try {
                        client.sendToServer.writeObject(new Packet(Command.LOGOUT));
                        client.sendToServer.flush();
                    } catch (IOException ex) {
                        addLog(ex.toString());
                    }
                } else {
                    client = new Client();
                    client.start();
                }
            }
        }
    }

    // aktualizacja wyglądu UI aplikacji w zależności od stanu połączenia
    private void updateUI() {
        if ((status & Consts.CONNECTED) == Consts.CONNECTED) {
            host.setEnabled(false);
            btnAddToServer.setEnabled(true);

            btnLogon.setText("Rozłącz");
            lbStatus.setText("Status: połączony");
            lbStatus.setForeground(Color.decode("#006600"));
        } else {
            input.setEnabled(false);
            input.setText("");
            text.setText("");

            host.setEnabled(true);
            btnAddToServer.setEnabled(false);
            btnLogon.setEnabled(true);
            btnReady.setEnabled(false);

            btnLogon.setText("Połącz");
            lbStatus.setText("Status: niepołączony");
            lbStatus.setForeground(Color.RED);

            for (PanelPlayer pp : panelGracza)
                pp.leave();
        }
    }

    private class Client extends Thread {

        private Socket socket;
        private ObjectInputStream receiveFromServer;
        private ObjectOutputStream sendToServer;

        public void run() {
            try {
                String[] hostParameters = host.getText().split(":", 2);
                socket = new Socket(hostParameters[0], new Integer(hostParameters[1]));
                sendToServer = new ObjectOutputStream(socket.getOutputStream());
                receiveFromServer = new ObjectInputStream(socket.getInputStream());

                status = status | Consts.CONNECTED;
                updateUI();

                sendToServer.writeObject(new Packet(Command.LOGIN_REQUEST));
                sendToServer.flush();

                Packet packet = null;
                while ((status & Consts.CONNECTED) == Consts.CONNECTED) {

                    try {
                        packet = (Packet) receiveFromServer.readObject();
                        if (packet != null) {

                            Command command = packet.getCommand();
                            if (command == Command.LOGIN_RESPONSE) {

                                // podanie nazwy użytkownika
                                String nick = JOptionPane.showInputDialog(null, "Podaj nick (max. 6 znaków): ");
                                if (nick != null) {
                                    nick = nick.trim().toUpperCase();
                                    if (nick.equals("")) {
                                        sendToServer.writeObject(new Packet(Command.LOGOUT));
                                        addLog("Niepoprawny nick, zostałeś rozłączony.");
                                    } else {
                                        // jeżeli nazwa użytkownika spełnia wymagania to.. poinformuj serwer
                                        if (nick.length() > 6)
                                            nick = nick.substring(0, 6);

                                        sendToServer.writeObject(new Packet(Command.NICK_SET, nick));

                                        playerId = packet.getPlayerId();
                                        panelGracza[playerId].resetActionPoints();

                                        btnReady.setEnabled(true);
                                    }
                                } else {
                                    sendToServer.writeObject(new Packet(Command.LOGOUT));
                                    addLog("Wylogowano.");
                                }

                            } else if (command == Command.LOGOUT) {

                                // ustawienia ui klienta po wylogowaniu
                                String message = packet.getString();
                                if (message != null && !message.isEmpty())
                                    addLog(message);

                                status = status & ~Consts.CONNECTED;
                                updateUI();

                            } else if (command == Command.LOGOUT_PLAYER_NOTIFY) {

                                // ustawienia ui panela gracza, który się wylogował
                                int deserterId = packet.getPlayerId();

                                status = packet.getBool() ? status | Consts.STARTED : status & ~Consts.STARTED;
                                if ((status & Consts.STARTED) == Consts.STARTED) {

                                    addLog("Gracz " + panelGracza[deserterId].getNick() + " uciekł!");

                                    btnLogon.setEnabled(true);
                                    btnReady.setEnabled(true);
                                    for (PanelPlayer pp : panelGracza) {
                                        pp.setReadiness(false);
                                        pp.setProgressValue(0);
                                        pp.setPlace("");
                                        pp.setSkillAvailability(0, false);
                                        pp.setSkillAvailability(1, false);
                                        pp.setSkillAvailability(2, false);
                                    }
                                    input.setEnabled(false);
                                    input.setText("");
                                    text.setText("");
                                }
                                panelGracza[deserterId].join("");
                                panelGracza[deserterId].setProgressValue(0);
                                panelGracza[playerId].resetActionPoints();

                            } else if (command == Command.UPDATE_PLAYERS_LIST) {

                                // wczytanie nazw graczy do paneli
                                ExtendedPacket extendedPacket = (ExtendedPacket) packet;
                                for (Player player : extendedPacket.getPlayers())
                                    panelGracza[player.getPlayerId()].join(player.getNick());

                            } else if (command == Command.CHANGE_READY) {

                                // przełącznik koloru gotowości danego użytkownika
                                panelGracza[packet.getPlayerId()].setReadiness(packet.getBool());

                            } else if (command == Command.START_GAME) {

                                // rozpoczęcie gry
                                ExtendedPacket extendedPacket = (ExtendedPacket) packet;
                                zadanie = extendedPacket.getZadanie();

                                btnReady.setEnabled(false);
                                btnLogon.setEnabled(false);

                                typedChars = 0;

                                text.setText(zadanie.getText());
                                input.setEnabled(true);
                                input.requestFocus();

                            } else if (command == Command.PROGRESS) {

                                // zmiana wartości progresu danego użytkownika
                                panelGracza[packet.getPlayerId()].setProgressValue(packet.getInt());

                            } else if (command == Command.WIN) {

                                int senderId = packet.getPlayerId();
                                // poinformowanie o ukończeniu zadania przez danego użytkownika
                                panelGracza[senderId].setPlace(packet.getInt());
                                addLog("Gracz " + panelGracza[senderId].getNick() + " juz skończył!");

                            } else if (command == Command.SEND_TEXT_RESPONSE) {

                                // odpowiedź serwera na prośbę o pozwolenie na przesłanie tekstu do serwera
                                if (packet.getBool())
                                    displayRequestWithTextPopup();
                                else
                                    addLog("Serwer odmówił żądanie o pozwolenie na przesłanie pliku.");

                            } else if (command == Command.RESET) {

                                // ogłoszenie wyników użytkowników
                                String content = "Tablica wyników:";
                                int counter = 1;
                                ExtendedPacket extendedPacket = (ExtendedPacket) packet;

                                for (Player player : extendedPacket.getPlayers())
                                    content += "\n" + (counter++) + ". " + player.getNick();

                                addLog(content);

                                typedChars = 0;

                                // zresetowanie ui paneli graczy
                                btnLogon.setEnabled(true);
                                btnReady.setEnabled(true);
                                for (PanelPlayer pp : panelGracza) {
                                    pp.setReadiness(false);
                                    pp.setProgressValue(0);
                                    pp.setPlace("");
                                    pp.setSkillAvailability(0, false);
                                    pp.setSkillAvailability(1, false);
                                    pp.setSkillAvailability(2, false);
                                }
                                panelGracza[playerId].resetActionPoints();

                            } else if (command == Command.DEBUFF_CAST) {

                                // aktywacja działania urozmaicenia rozgrywki
                                int targetId = packet.getInt();
                                Debuff debuff = packet.getDebuff();
                                int durationTime;
                                if (debuff == Debuff.INVISIBILITY) {
                                    panelGracza[targetId].setSkillActivity(0, true);
                                    durationTime = Variety.HIDE_INPUT_CONTENT.getDurationTime();
                                } else if (debuff == Debuff.REVERSE) {
                                    panelGracza[targetId].setSkillActivity(1, true);
                                    durationTime = Variety.REVERSE_WORDS_IN_TEXT.getDurationTime();
                                } else if (debuff == Debuff.SHUFFLE) {
                                    panelGracza[targetId].setSkillActivity(2, true);
                                    durationTime = Variety.SHUFFLE_CHARS_IN_WORDS.getDurationTime();
                                } else
                                    durationTime = 5;

                                if (playerId == targetId) {
                                    castDebuff(debuff);
                                    new java.util.Timer().schedule(new java.util.TimerTask() {
                                        public void run() {

                                            try {
                                                client.sendToServer.writeObject(
                                                        new Packet(Command.DEBUFF_CLEAR, debuff, targetId));
                                                client.sendToServer.flush();
                                            } catch (IOException ex) {
                                                addLog(ex.toString());
                                            }
                                            clearDebuff(debuff);
                                        }
                                    }, durationTime * 1000);
                                }

                            } else if (command == Command.DEBUFF_CLEAR) {

                                // przywrócenie skutków urozmaicenia rozgrywki
                                int targetId = packet.getInt();
                                Debuff debuff = packet.getDebuff();
                                if (debuff == Debuff.INVISIBILITY)
                                    panelGracza[targetId].setSkillActivity(0, false);
                                else if (debuff == Debuff.REVERSE)
                                    panelGracza[targetId].setSkillActivity(1, false);
                                else if (debuff == Debuff.SHUFFLE)
                                    panelGracza[targetId].setSkillActivity(2, false);
                            }
                            sendToServer.flush();
                        }
                    } catch (ClassNotFoundException ex) {
                    }
                }
            } catch (UnknownHostException e) {
                addLog("Błąd połączenia!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Prawdopodobnie serwer nie jest włączony.", "Informacja",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            } catch (NullPointerException e) {
                addLog(e.toString());
            } finally {
                try {
                    receiveFromServer.close();
                    sendToServer.close();
                    socket.close();
                } catch (IOException e) {
                } catch (NullPointerException e) {
                }
            }
        }
    }

    public void castDebuff(Debuff d) {
        if (d == Debuff.INVISIBILITY)
            this.castInvisibilityDebuff();
        else if (d == Debuff.REVERSE)
            this.castReverseDebuff();
        else if (d == Debuff.SHUFFLE)
            this.castShuffleDebuff();
    }

    public void clearDebuff(Debuff d) {
        if (d == Debuff.INVISIBILITY)
            this.clearInvisibilityDebuff();
        else if (d == Debuff.REVERSE)
            this.clearReverseDebuff();
        else if (d == Debuff.SHUFFLE)
            this.clearShuffleDebuff();
    }

    private void castInvisibilityDebuff() {
        input.setForeground(Color.WHITE);
    }

    private void clearInvisibilityDebuff() {
        input.setForeground(Color.BLACK);
    }

    private void castReverseDebuff() {
        text.setText(this.mirror(text.getText()));
    }

    private void clearReverseDebuff() {
        text.setText(zadanie.getText().substring(typedChars));
    }

    private void castShuffleDebuff() {
        text.setText(this.shuffle(text.getText()));
    }

    private void clearShuffleDebuff() {
        text.setText(zadanie.getText().substring(typedChars));
    }

    // odwrócenie wyrazów w tekście
    private String mirror(String content) {
        String reversedContent = new StringBuilder(content).reverse().toString();
        String[] words = reversedContent.split(" ");

        StringBuilder result = new StringBuilder("");
        for (int i = words.length - 1; i >= 0; i--)
            result.append(words[i] + " ");
        return result.toString();
    }

    // pomieszanie środkowych liter w wyrazach tekstu
    private String shuffle(String content) {

        Random random = new Random();
        String badChars = ".,:;!?()";

        String[] words = content.split(" ");
        String result = "";
        for (int i = 0; i < words.length; i++) {

            char[] chars = words[i].toCharArray();
            int charsCount = chars.length;

            int start = 0, stop = 0;
            if (charsCount > 2) {

                for (int j = 0; j < charsCount - 1; j++) {
                    if (!badChars.contains(Character.toString(chars[j]))) {
                        start = j + 1;
                        break;
                    }
                }

                for (int j = charsCount - 1; j > 0; j--) {
                    if (!badChars.contains(Character.toString(chars[j]))) {
                        stop = j - 1;
                        break;
                    }
                }
            }

            if (start < stop) {
                for (int j = start; j < stop; j++) {
                    int zmiana = j + random.nextInt(stop - j);
                    char temp = chars[j];
                    if (zmiana > 0) {
                        chars[j] = chars[zmiana];
                        chars[zmiana] = temp;
                    }
                }
            }

            result = result + String.valueOf(chars) + " ";
        }
        return result;
    }

    private class PanelPlayer extends JPanel {

        JProgressBar progress;
        JPanel color, panelWithNick;
        JLabel labelWithNick, labelWithPlace, labelWithAP;
        JButton[] btns = new JButton[3];
        int panelId;
        int actionPoints;

        public PanelPlayer(int idColor) {
            super(new BorderLayout());
            this.panelId = idColor;

            color = new JPanel();
            color.setBackground(this.getColorById(idColor));
            color.setPreferredSize(new Dimension(40, 40));

            labelWithPlace = new JLabel("", SwingConstants.CENTER);
            labelWithPlace.setFont(new Font("Consolas", Font.PLAIN, 22));

            color.add(labelWithPlace);

            panelWithNick = new JPanel();
            panelWithNick.setBackground(Color.decode("#ffcccc"));

            labelWithNick = new JLabel("", SwingConstants.CENTER);
            labelWithNick.setFont(new Font("Consolas", Font.PLAIN, 22));
            labelWithNick.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

            panelWithNick.add(labelWithNick);

            JPanel btnsBox = new JPanel(new GridLayout(0, 4));

            labelWithAP = new JLabel("", SwingConstants.CENTER);
            labelWithAP.setFont(new Font("Consolas", Font.PLAIN, 22));
            labelWithAP.setPreferredSize(new Dimension(40, 40));

            btnsBox.add(labelWithAP, BorderLayout.LINE_START);

            int counter = 0;
            ObslugaUmiejetnosci obslugaUmiejetnosci = new ObslugaUmiejetnosci();
            for (int i = 0, k = btns.length; i < k; i++) {
                btns[i] = new JButton();
                btns[i].setPreferredSize(new Dimension(40, 40));
                btns[i].setFont(new Font("Consolas", Font.PLAIN, 11));
                btns[i].setOpaque(true);
                btns[i].setEnabled(false);
                btns[i].addActionListener(obslugaUmiejetnosci);
                btnsBox.add(btns[i], BorderLayout.LINE_START);
                counter++;
            }
            btns[0].setText(Character.toString(Variety.HIDE_INPUT_CONTENT.getShortcut()));
            btns[1].setText(Character.toString(Variety.REVERSE_WORDS_IN_TEXT.getShortcut()));
            btns[2].setText(Character.toString(Variety.SHUFFLE_CHARS_IN_WORDS.getShortcut()));

            JPanel left = new JPanel(new BorderLayout());
            left.setPreferredSize(new Dimension(340, 340));

            progress = new JProgressBar();

            // -- rozmieszczenie paneli (z wylaczeniem progressbara)
            left.add(color, BorderLayout.LINE_START);
            left.add(panelWithNick, BorderLayout.CENTER);
            left.add(btnsBox, BorderLayout.LINE_END);

            // -- rozmieszczenie paneli
            add(left, BorderLayout.LINE_START);
            add(progress, BorderLayout.CENTER);
        }

        private Color getColorById(int id) {
            switch (id) {
            case 0:
                return Color.decode("#6077E0"); // niebieski
            case 1:
                return Color.decode("#ED094A"); // czerwony
            case 2:
                return Color.decode("#4DBD02"); // zielony
            case 3:
                return Color.decode("#E231E2"); // fioletowy
            case 4:
                return Color.decode("#37C6C7"); // błękitny
            case 5:
                return Color.decode("#FF8B17"); // pomaranczowy
            default:
                return Color.GRAY;
            }
        }

        public void setProgressValue(int value) {
            this.progress.setValue(value);
        }

        public void setReadiness(boolean isReady) {
            this.panelWithNick.setBackground(Color.decode(isReady ? "#ccffcc" : "#ffcccc"));
        }

        public void join(String nick) {
            this.labelWithNick.setText(nick);
        }

        public String getNick() {
            return this.labelWithNick.getText();
        }

        public void leave() {
            this.setProgressValue(0);
            this.labelWithNick.setText("");
            this.panelWithNick.setBackground(Color.decode("#ffcccc"));
            this.labelWithAP.setText("");
        }

        public void setPlace(int place) {
            this.labelWithPlace.setText(Integer.toString(place));
        }

        public void setPlace(String text) {
            this.labelWithPlace.setText(text);
        }

        private void updateActionPointsInfo(int value) {
            this.labelWithAP.setText(Integer.toString(value));
        }

        public int getActionPoints() {
            return this.actionPoints;
        }

        public void resetActionPoints() {
            this.actionPoints = 0;
            this.updateActionPointsInfo(0);
        }

        private void setActionPoints(int value) {
            this.actionPoints = value;
            this.updateActionPointsInfo(value);
        }

        public void addActionPoint() {
            this.updateActionPointsInfo(++this.actionPoints);
        }

        public int addActionPointAndGet() {
            int currentAP = ++this.actionPoints;
            this.updateActionPointsInfo(currentAP);
            return currentAP;
        }

        public boolean tryChangeActionPoints(int changeValue) {
            int newValue = this.getActionPoints() - changeValue;
            if (newValue >= 0) {
                this.setActionPoints(newValue);
                return true;
            } else
                return false;
        }

        private void setSkillAvailability(int idSkill, boolean availability) {
            this.btns[idSkill].setEnabled(availability);
        }

        private void setSkillActivity(int idSkill, boolean activity) {
            if (activity) {
                if (idSkill == 0)
                    this.btns[idSkill].setBackground(Variety.HIDE_INPUT_CONTENT.getColor());
                else if (idSkill == 1)
                    this.btns[idSkill].setBackground(Variety.REVERSE_WORDS_IN_TEXT.getColor());
                else if (idSkill == 2)
                    this.btns[idSkill].setBackground(Variety.SHUFFLE_CHARS_IN_WORDS.getColor());
            } else
                this.btns[idSkill].setBackground(null);
        }

        private class ObslugaUmiejetnosci implements ActionListener {

            public void actionPerformed(ActionEvent e) {

                if (e.getSource() == btns[0]) {
                    if (updateSkillsAvailability(Debuff.INVISIBILITY)) {
                        try {
                            client.sendToServer
                                    .writeObject(new Packet(Command.DEBUFF_CAST, Debuff.INVISIBILITY, panelId));
                            client.sendToServer.flush();
                        } catch (IOException ex) {
                            addLog(ex.toString());
                        }
                    }
                } else if (e.getSource() == btns[1]) {
                    if (updateSkillsAvailability(Debuff.REVERSE)) {
                        try {
                            client.sendToServer.writeObject(new Packet(Command.DEBUFF_CAST, Debuff.REVERSE, panelId));
                            client.sendToServer.flush();
                        } catch (IOException ex) {
                            addLog(ex.toString());
                        }
                    }
                } else if (e.getSource() == btns[2]) {
                    if (updateSkillsAvailability(Debuff.SHUFFLE)) {
                        try {
                            client.sendToServer.writeObject(new Packet(Command.DEBUFF_CAST, Debuff.SHUFFLE, panelId));
                            client.sendToServer.flush();
                        } catch (IOException ex) {
                            addLog(ex.toString());
                        }
                    }
                }
            }
        }
    }

    private boolean updateSkillsAvailability(Debuff c) {

        int cost = 0;
        if (c == Debuff.INVISIBILITY)
            cost = Variety.HIDE_INPUT_CONTENT.getCost();
        else if (c == Debuff.REVERSE)
            cost = Variety.REVERSE_WORDS_IN_TEXT.getCost();
        else if (c == Debuff.SHUFFLE)
            cost = Variety.SHUFFLE_CHARS_IN_WORDS.getCost();

        if (panelGracza[playerId].tryChangeActionPoints(cost)) {

            int currentActionPoints = panelGracza[playerId].getActionPoints();
            if (currentActionPoints < Variety.SHUFFLE_CHARS_IN_WORDS.getCost()) {
                for (PanelPlayer pp : panelGracza)
                    pp.setSkillAvailability(2, false);
                if (currentActionPoints < Variety.REVERSE_WORDS_IN_TEXT.getCost()) {
                    for (PanelPlayer pp : panelGracza)
                        pp.setSkillAvailability(1, false);
                    if (currentActionPoints < Variety.HIDE_INPUT_CONTENT.getCost()) {
                        for (PanelPlayer pp : panelGracza)
                            pp.setSkillAvailability(0, false);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void addLog(String content) {
        logs.append(content + "\n");
        logs.setCaretPosition(logs.getDocument().getLength());
    }

    public static void main(String[] args) {
        new ClientGUI();
    }
}