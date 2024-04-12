package com.reps.demogcloud.data;

import com.reps.demogcloud.models.messaging.MessageModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessagingRepository extends MongoRepository<MessageModel, String> {
    List<MessageModel> findAllByRecipientIgnoreCase(String username);

    List<MessageModel> findAllBySenderIgnoreCase(String username);


    List<MessageModel> findAllByRecipientAndStatus(String username,String status);

    List<MessageModel> findAllBySenderAndStatus(String username,String status);

}
