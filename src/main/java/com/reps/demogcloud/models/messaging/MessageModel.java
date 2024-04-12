package com.reps.demogcloud.models.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "messages")

public class MessageModel {
    @Id
    private String messageId;

    @Indexed
    private String sender;

    @Indexed
    private String recipient;

    private String messageTitle;
    private String messageContent;

    @Indexed
    private Date timeCreated;

    @Indexed
    private String status;
}

