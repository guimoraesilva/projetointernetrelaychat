/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.irc;

import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.JoinEvent; // Add this import
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.pircbotx.hooks.events.UnknownEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.PartEvent;

public class ChatListener extends ListenerAdapter {

    private final String nickname;
    private final JTextArea chatArea;
    private final JTextField txtCommand;
    private final JTextArea channelsArea;
    private String lastChannelJoined = null;

    public ChatListener(String nickname, JTextArea chatArea, JTextField command_line, JTextArea channelsArea) {
        this.nickname = nickname;
        this.chatArea = chatArea;
        this.channelsArea = channelsArea;
        this.txtCommand = command_line;
    }

    @Override
    public void onUnknown(UnknownEvent event) throws Exception {
        System.out.println("RAW INCOMING: " + event.getLine());
    }

    @Override
    public void onConnect(ConnectEvent event) {
        
        Irc.isBotReady = true;
        Irc.bot.sendIRC().listChannels();
    }

    @Override
    public void onJoin(JoinEvent event) throws Exception {
        
        if (event.getUser().getNick().equals(event.getBot().getNick())) {
            
            this.lastChannelJoined = event.getChannel().getName(); 
        }
        Irc.canalativo = this.lastChannelJoined;
        chatArea.append("\n\n\n");
        String timestamp = Instant.now().toString();
        chatArea.append(timestamp);
        chatArea.append("\nNow logged in channel " + this.lastChannelJoined + "\n\n");
    }

    public String getLastChannelJoined() {
        return this.lastChannelJoined;
    }
    
    @Override
    public void onNotice(NoticeEvent event) {
        String sender;
        if (event.getUser() != null) {
            sender = event.getUser().getNick();
        } else {
            sender = event.getUserHostmask().getNick();
        }
        String noticeMessage = event.getMessage();
        System.out.println(String.format("--- NOTICE from %s: %s ---", sender, noticeMessage));
        if (sender.equalsIgnoreCase("NickServ") && noticeMessage.contains("not a registered nickname")) {
            System.out.println("Nickname not registered. Registering with NickServ now...");
            Irc.nextCommand = Irc.MODE_WAITING_FOR_EMAIL;

        
            SwingUtilities.invokeLater(() -> {
                chatArea.append("\n[NickServ] Your nickname is not registered. \n");
                chatArea.append("[NickServ] Please type your email address in the command box to register.\n");
                txtCommand.setText("Email: ");
            });
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        System.out.println("Disconnected from IRC server.");
        chatArea.append("\nDisconnected from IRC server. \n");
    }

    @Override
    public void onServerResponse(ServerResponseEvent event) {
        if (event.getCode() == 322) {
            String rawMessage = event.getRawLine();
            String[] parts = rawMessage.split(" ", 6); 

            if (parts.length >= 6) {
                String userOnline = parts[2];
                String channelName = parts[3];
                String userCount = parts[4];
                
                String topic = parts[5].startsWith(":") ? parts[5].substring(1) : parts[5];

                String formattedEntry = String.format("Canal: %s (%s usuarios) - Topico: %s",
                        channelName, userCount, topic);
                SwingUtilities.invokeLater(() -> {
                    channelsArea.append(formattedEntry + "\n");
                });
            }
        }else{
            chatArea.append("\nServer response: " + event.getCode() + " - " + event.getParsedResponse());
            System.out.println("\nServer response: " + event.getCode() + " - " + event.getParsedResponse());
        }
    }
    
@Override
    public void onPart(PartEvent event) throws Exception {
        
        String userNick = event.getUser().getNick();
        
        String channelName = event.getChannel().getName();
        
        String partReason = event.getReason();

        if (userNick.equals(event.getBot().getNick())) {
            Irc.canalativo="";
            chatArea.append("Bot has left channel: " + channelName);
        } else {
            Irc.canalativo="";
            chatArea.append(userNick + " has parted " + channelName + ". Reason: " + partReason);
        }
    }
    
    @Override
    public void onPrivateMessage(PrivateMessageEvent event) throws Exception {
        String senderNick = event.getUser().getNick();
        String messageContent = event.getMessage();
        chatArea.append("\n<<PRIVATE MESSAGE FROM " + senderNick + ">>: " + messageContent);
}

    @Override
    public void onMessage(MessageEvent event) throws Exception {
        String user;
        if (event.getUser() != null) {
            user = event.getUser().getNick();
        } else {
            user = event.getUserHostmask().getNick();
        }
        String message = event.getMessage();
        String timestamp = Instant.now().toString();
        chatArea.append("\n");
        chatArea.append(String.format("%s - %s: %s", timestamp,user, message));

    }

}
