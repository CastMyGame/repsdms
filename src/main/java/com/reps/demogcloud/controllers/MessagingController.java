package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.messaging.MessageModel;
import com.reps.demogcloud.models.messaging.MessagesResponse;
import com.reps.demogcloud.services.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {
"http//localhost:3000",
"http://localhost:3000/"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/messaging/v1")
public class MessagingController {

    private MessagingService messagingService;

    @Autowired
    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    //---------------------------------GET Controllers--------------------------------

    @GetMapping("/")
    public ResponseEntity<MessagesResponse> getActiveMessagesByUser() {
        MessagesResponse message =  messagingService.getActiveMessageByLoggedInUser();

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/")
    public ResponseEntity<MessageModel> sendNewMessage(@RequestBody MessageModel message){
        System.out.println(message);
        MessageModel newMessage = messagingService.createNewMessage(message);
        return ResponseEntity
                .accepted()
                .body(newMessage);
    }


    @PutMapping("/{id}/{status}")
    public ResponseEntity<MessageModel> updateMessageStatus(@PathVariable String id,@PathVariable String status){
        MessageModel newMessage = messagingService.updateStatus(id,status);
        return ResponseEntity
                .accepted()
                .body(newMessage);
    }

}
