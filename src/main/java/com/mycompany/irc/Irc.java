package com.mycompany.irc;

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

public class Irc extends javax.swing.JFrame {

    private static final String IRC_SERVER = "server.ircraony.click";
    private static final int IRC_TLS_PORT = 6697;
    private static String NICKNAME = "Nickname";
    private static String CHANNEL_TO_JOIN = "#linux";
    private String lastResult;
    private String base = "";
    public static PircBotX bot;
    private boolean acessoinicial;
    public static String canalativo;
    public static volatile boolean isBotReady = false;
    public static volatile String nextCommand = "";
    public static final String MODE_WAITING_FOR_EMAIL = "WAITING_FOR_EMAIL";

    public Irc() {
        initComponents();
        lastResult = "";
        canalativo = "";
        this.acessoinicial = true;
        if (acessoinicial) {
            txtCommand.setText("Nickname: ");
        }
    }

    @SuppressWarnings("unchecked")
    
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        textArea = new javax.swing.JTextArea();
        txtCommand = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtAreaCanais = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        textArea.setEditable(false);
        textArea.setBackground(new java.awt.Color(0, 0, 0));
        textArea.setColumns(20);
        textArea.setFont(new java.awt.Font("Courier New", 0, 14)); 
        textArea.setForeground(new java.awt.Color(255, 255, 255));
        textArea.setRows(5);
        textArea.setText("Bem Vindo!");
        textArea.setToolTipText("");
        jScrollPane1.setViewportView(textArea);

        txtCommand.setBackground(new java.awt.Color(0, 0, 0));
        txtCommand.setFont(new java.awt.Font("Courier New", 0, 14)); 
        txtCommand.setForeground(new java.awt.Color(255, 255, 255));
        txtCommand.setText(">");
        txtCommand.setToolTipText("");
        txtCommand.setCaretColor(new java.awt.Color(255, 255, 255));
        txtCommand.setDisabledTextColor(new java.awt.Color(0, 0, 0));
        txtCommand.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtCommandKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtCommandKeyReleased(evt);
            }
        });

        txtAreaCanais.setEditable(false);
        txtAreaCanais.setBackground(new java.awt.Color(0, 0, 0));
        txtAreaCanais.setColumns(20);
        txtAreaCanais.setForeground(new java.awt.Color(255, 255, 255));
        txtAreaCanais.setRows(5);
        txtAreaCanais.setToolTipText("");
        jScrollPane2.setViewportView(txtAreaCanais);

        jLabel1.setText("Canais:");

        jLabel2.setText("Irc Console");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtCommand)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 373, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(13, 13, 13)
                                .addComponent(jLabel2)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 364, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addGap(281, 281, 281)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 259, Short.MAX_VALUE)
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(txtCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }
    private void txtCommandKeyPressed(java.awt.event.KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            String commandline = this.txtCommand.getText();
            String[] partscmdln = commandline.split(" "); 
            String command = commandline.substring(partscmdln[0].length()+1);
            if (acessoinicial) {
                String nickname = command;
                NICKNAME = nickname.toLowerCase();
                if (!NICKNAME.isEmpty()) {
                    IrcConnectionWorker worker = new IrcConnectionWorker(NICKNAME, this.textArea, this.txtCommand, IRC_SERVER, IRC_TLS_PORT, this.txtAreaCanais);
                    worker.execute();
                    acessoinicial = false;
                }
            } else {
                if (isBotReady) {
                    if (command.startsWith("/")) {
                        handleCommand(command, bot);
                    } else {
                        handleCommand("PRIVMSG " + canalativo + " :" + command, bot);
                        }
                 } else {
                    System.out.println("Bot is not ready. Please wait a moment.");
                }
            }
            this.txtCommand.setText(NICKNAME + ": ");
        }

    }
    private void txtCommandKeyReleased(java.awt.event.KeyEvent evt) {
        this.atualizaAreaTexto();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Irc.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Irc.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Irc.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Irc.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
       
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Irc().setVisible(true);
            }
        });
    }

   
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextArea textArea;
    private javax.swing.JTextArea txtAreaCanais;
    private javax.swing.JTextField txtCommand;
    

    private void handleCommand(String line, PircBotX bot) {
        String[] parts = line.substring(1).split(" ");
        String command = parts[0];
        switch (command) {
            case "PART":
                if (parts.length >= 2) {
                    bot.sendRaw().rawLine(line.substring(1));
                } else {
                    lastResult = "Usage: /PART <#channel> :<message>";
                }
                break;

            case "PING":
                if (parts.length < 2) {
                    String password = parts[1];
                    
                    bot.sendRaw().rawLine(line);
                    lastResult = "PING sent to IRC Server.";
                } else {
                    lastResult = "Usage: /PING";
                }
                break;

            case "NICK":
                if (parts.length > 1) {
                    bot.sendIRC().changeNick(parts[1]);
                } else {
                    lastResult = "Usage: /NICK <new nick>";
                }
                break;

            case "JOIN":
                if (parts.length >= 2) {
                    String channelToJoin = parts[1];
                    bot.sendIRC().joinChannel(channelToJoin);
                    lastResult = "Attempting to join channel " + channelToJoin;
                } else {
                    lastResult = "Usage: /JOIN <#channel_name>";
                }
                break;

            case "PRIVMSG":
                if (parts.length >= 3) {
                    if (parts[2].startsWith(":")) {
                        bot.sendRaw().rawLine(line);
                    } else {
                        lastResult = "Usage: /PRIVMSG <target> :<message>";
                    }
                } else {
                    lastResult = "Usage: /PRIVMSG <target> :<message>";
                }
                break;
            case "QUIT":
                if (parts.length == 1) {
                    bot.sendIRC().quitServer("Leaving IRC. Goodbye!");
                    System.exit(0);
                } else {
                    lastResult = "Usage: /QUIT";
                }
                break;
            default:
                lastResult = "Unknown command: " + command;
                break;
        }
    }

    private void escrever() {
        this.txtCommand.setText(this.base);
    }

    private void atualizaAreaTexto() {
        if (!this.lastResult.equals("")) {
            this.textArea.append(this.lastResult);
        }
        this.lastResult = "";
    }

}
