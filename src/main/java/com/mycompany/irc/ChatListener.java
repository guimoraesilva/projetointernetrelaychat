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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Raony
 */
public class ChatListener extends ListenerAdapter{
    
    private final DynamoDbClient ddbClient;
    private static String DYNAMODB_TABLE_NAME = "ChatHistory";
    private final IrcDao ircDao;
    private final String password;
    private final String email = "raonysps@gmail.com"; // Use a real email!

    public ChatListener(DynamoDbClient ddbClient, IrcDao ircDao, String password) {
        this.ddbClient = ddbClient;
        this.ircDao = ircDao;
        this.password = password;
    }

    @Override
    public void onConnect(ConnectEvent event) {
        // This event fires as soon as the bot connects to the server
        if (!password.isEmpty()) {
            System.out.println("Attempting to identify with NickServ...");
            event.getBot().sendIRC().message("NickServ", "IDENTIFY " + password);
        } else {
            System.out.println("No password provided. Assuming unregistered user.");
        }
    }

    @Override
    public void onNotice(NoticeEvent event) {
        String sender = event.getUser().getNick();
        String noticeMessage = event.getMessage();

        System.out.println(String.format("--- NOTICE from %s: %s ---", sender, noticeMessage));

        // Listen for NickServ's failure notices
        if (sender.equalsIgnoreCase("NickServ") && noticeMessage.contains("not a registered nickname")) {
            System.out.println("Nickname not registered. Registering with NickServ now...");
            event.getBot().sendIRC().message("NickServ", "REGISTER " + password + " " + email);
        }
    }
    
    @Override
    public void onMessage(MessageEvent event) throws Exception {
        String channel = event.getChannel().getName();
        String user = event.getUser().getNick();
        String message = event.getMessage();
        String timestamp = Instant.now().toString();

        System.out.println(String.format("[%s] %s: %s", channel, user, message));

        // Create the item to be stored in DynamoDB
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ChannelName", AttributeValue.builder().s(channel).build());
        item.put("Timestamp", AttributeValue.builder().s(timestamp).build());
        item.put("Username", AttributeValue.builder().s(user).build());
        item.put("MessageContent", AttributeValue.builder().s(message).build());

        // Create the PutItem request
        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(DYNAMODB_TABLE_NAME)
                .item(item)
                .build();

        // Asynchronously put the item to DynamoDB to avoid blocking the IRC thread
        new Thread(() -> {
            try {
                ddbClient.putItem(putItemRequest);
                System.out.println("Successfully stored message to DynamoDB.");
            } catch (Exception e) {
                System.err.println("Error storing message to DynamoDB: " + e.getMessage());
            }
        }).start();
    }
    
}
