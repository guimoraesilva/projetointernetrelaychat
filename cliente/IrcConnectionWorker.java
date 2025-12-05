package com.mycompany.irc;

import static com.mycompany.irc.Irc.bot;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import java.awt.event.KeyEvent;
import java.io.IOException;
import org.pircbotx.exception.IrcException;
import java.util.List;
import javax.swing.SwingWorker;
import java.io.IOException;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;
import java.net.Socket;

public class IrcConnectionWorker extends SwingWorker<Void, Void> {

    private final String nickname;
    private final JTextArea chatArea; 
    private final JTextField txtCommand;
    private final String server;
    private final int port;
    private final JTextArea channelsArea;

    public IrcConnectionWorker(String nickname, JTextArea chatArea, JTextField command_line, String server, int port, JTextArea channelsArea) {
        this.nickname = nickname;
        this.chatArea = chatArea;
        this.txtCommand = command_line;
        this.server = server;
        this.port = port;
        this.channelsArea = channelsArea;
    }
    
    private boolean isServerReachable(String host, int port, int timeoutMillis){
        try(Socket socket = new Socket()){
            socket.connect(new java.net.InetSocketAddress(host,port),timeoutMillis);
            return true;
        } catch (IOException e){
            System.err.println("Server not reachable: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            if(!isServerReachable(server,port,5000)){
                chatArea.append("IRC server is nor reachable at " + server + ":" + port + "\n");
            }
            System.out.println("AWS clients ready.");
            System.out.println("Configuring IRC client...");
            Configuration configuration = new Configuration.Builder()
                    .setName(nickname)
                    .setLogin(nickname) // This sets the IDENT (first part of the USER command)
                    .setRealName(nickname)
                    .addServer(server, port)
                    .setServerPassword(null)
                    .setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates())
                    .addListener(new ChatListener(nickname, chatArea,txtCommand,channelsArea))
                    .buildConfiguration();
            Irc.bot = new PircBotX(configuration);
            System.out.println("Starting IRC bot...");
            new Thread(() -> {
                try {
                    Irc.bot.startBot();
                    System.out.println("Bot started.");
                } catch (IOException | IrcException e) {
                    System.err.println("Error starting the bot due to an I/O issue: " + e.getMessage());
                    e.printStackTrace(); // Optional: prints the full stack trace for debugging
                }
            }
            ).start();
            
        } catch (Exception e) {
            System.err.println("Network connection failed: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void done() {
        try {
            get();
            chatArea.append("\nConnection successful!\n\n");
        } catch (Exception e) {
            chatArea.append("Connection failed: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
