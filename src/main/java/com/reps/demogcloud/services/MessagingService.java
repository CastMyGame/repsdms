package com.reps.demogcloud.services;

import com.reps.demogcloud.data.MessagingRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.messaging.MessageModel;
import com.reps.demogcloud.models.messaging.MessagesResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.AuthService;
import com.reps.demogcloud.security.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class MessagingService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MessagingRepository messagingRepository;
    private final AuthService authService;

    private final UserService userService;


    public MessagingService(MessagingRepository messagingRepository, AuthService authService, UserService userService) {
        this.messagingRepository = messagingRepository;
        this.authService = authService;
        this.userService = userService;
    }

    public MessagesResponse getActiveMessageByLoggedInUser() throws ResourceNotFoundException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            UserModel userModel = userService.loadUserModelByUsername(authentication.getName());
            List<MessageModel> receivedMessages =  messagingRepository.findAllByRecipientIgnoreCase(userModel.getUsername());
            List<MessageModel> sentMessages =  messagingRepository.findAllBySenderIgnoreCase(userModel.getUsername());

            MessagesResponse messagesResponse = new MessagesResponse();
            messagesResponse.setReceivedMessages(receivedMessages.stream().filter(message->!message.getStatus().equalsIgnoreCase("archived")).toList());
            messagesResponse.setSentMessages(sentMessages.stream().filter(message->!message.getStatus().equalsIgnoreCase("archived")).toList());

            return messagesResponse;

        }
        return null;


    }

    public  MessageModel createNewMessage( MessageModel message){
        MessageModel newMessage = new MessageModel(
                message.getMessageId(),
                message.getSender(),
                message.getRecipient(),
                message.getMessageTitle(),
                message.getMessageContent(),
                message.getTimeCreated(),
                message.getStatus()
        );
        messagingRepository.save(newMessage);
        return newMessage;

    }

    public MessageModel updateStatus(String id, String status) {
        Optional<MessageModel> message = messagingRepository.findById(id);
        if(message.isPresent()){
            MessageModel messageModel = message.get();
            messageModel.setStatus(status);
            messagingRepository.save(messageModel);
            return messageModel;
        }
        return null;
    }
}
