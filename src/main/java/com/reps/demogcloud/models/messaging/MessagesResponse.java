package com.reps.demogcloud.models.messaging;

import lombok.Data;

import java.util.List;
@Data
public class MessagesResponse {
    List<MessageModel> sentMessages;
    List<MessageModel> receivedMessages;
}
