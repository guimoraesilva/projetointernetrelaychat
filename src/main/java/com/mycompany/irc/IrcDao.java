/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.irc;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Raony
 */
public class IrcDao {

    private final DynamoDbClient ddbClient;

    public IrcDao(DynamoDbClient ddbClient) {
        this.ddbClient = ddbClient;
    }

    // --- User Operations ---
    public void createUser(String username, String email) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("Username", AttributeValue.builder().s(username).build());
        item.put("Email", AttributeValue.builder().s(email).build());
        item.put("FirstLoginTimestamp", AttributeValue.builder().s(Instant.now().toString()).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName("IrcUsers")
                .item(item)
                .build();

        ddbClient.putItem(request);
        System.out.println("User " + username + " stored in DynamoDB.");
    }

    // New method: Retrieve a single user by their username
    public Map<String, AttributeValue> getUser(String username) {
        Map<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("Username", AttributeValue.builder().s(username).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName("IrcUsers")
                .key(keyToGet)
                .build();

        try {
            Map<String, AttributeValue> item = ddbClient.getItem(request).item();
            if (item != null && !item.isEmpty()) {
                System.out.println("Successfully retrieved user: " + item.get("Username").s());
            } else {
                System.out.println("User not found: " + username);
            }
            return item;
        } catch (DynamoDbException e) {
            System.err.println("Error retrieving user from DynamoDB: " + e.getMessage());
            return null;
        }
    }

    // --- Channel Operations ---
    public void createChannel(String channelName, String creatorUsername) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ChannelName", AttributeValue.builder().s(channelName).build());
        item.put("RegisteredBy", AttributeValue.builder().s(creatorUsername).build());
        item.put("CreationTimestamp", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("Topic", AttributeValue.builder().s("No topic set.").build());
        item.put("MemberCount", AttributeValue.builder().n("0").build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName("IrcChannels")
                .item(item)
                .build();

        ddbClient.putItem(request);
        System.out.println("Channel " + channelName + " stored in DynamoDB.");
    }
    
    // New method: Retrieve a single channel by its name
    public Map<String, AttributeValue> getChannel(String channelName) {
        Map<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("ChannelName", AttributeValue.builder().s(channelName).build());
        
        GetItemRequest request = GetItemRequest.builder()
                .tableName("IrcChannels")
                .key(keyToGet)
                .build();
        
        try {
            Map<String, AttributeValue> item = ddbClient.getItem(request).item();
            if (item != null && !item.isEmpty()) {
                System.out.println("Successfully retrieved channel: " + item.get("ChannelName").s());
            } else {
                System.out.println("Channel not found: " + channelName);
            }
            return item;
        } catch (DynamoDbException e) {
            System.err.println("Error retrieving channel from DynamoDB: " + e.getMessage());
            return null;
        }
    }

    // New method: Query messages for a specific channel (uses the 'Query' operation)
    public List<Map<String, AttributeValue>> getChatHistoryForChannel(String channelName) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().s(channelName).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName("ChatHistory")
                .keyConditionExpression("ChannelName = :val")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        try {
            QueryResponse response = ddbClient.query(queryRequest);
            List<Map<String, AttributeValue>> items = response.items();
            System.out.println("Found " + items.size() + " messages for channel " + channelName);
            return items;
        } catch (DynamoDbException e) {
            System.err.println("Error querying chat history: " + e.getMessage());
            return null;
        }
    }    
    
}
