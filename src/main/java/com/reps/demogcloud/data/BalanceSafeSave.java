package com.reps.demogcloud.data;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.*;

final class BalanceSafeSave {
    private BalanceSafeSave() {}
    static <T> T save(MongoTemplate mongo,T entity,Class<T> type) {
        Document mapped=new Document();mongo.getConverter().write(entity,mapped);
        Object id=mapped.get("_id");
        if(id==null || !mongo.exists(Query.query(Criteria.where("_id").is(id)),type)) {
            // New profiles start empty. Wallet grants only go through the transaction service.
            mapped.put("currency",0);
            mongo.insert(mapped,mongo.getCollectionName(type));
            return mongo.getConverter().read(type,mapped);
        }
        Update update=new Update();
        var metadata=mongo.getConverter().getMappingContext().getRequiredPersistentEntity(type);
        for(var property:metadata) {
            String field=property.getFieldName();
            if(property.isIdProperty() || property.isTransient() || "currency".equals(field)) continue;
            if(mapped.containsKey(field)) update.set(field,mapped.get(field)); else update.unset(field);
        }
        T saved=mongo.findAndModify(Query.query(Criteria.where("_id").is(id)),update,
                FindAndModifyOptions.options().returnNew(true),type);
        if(saved==null) throw new OptimisticLockingFailureException("Profile disappeared during update");
        return saved;
    }
}
