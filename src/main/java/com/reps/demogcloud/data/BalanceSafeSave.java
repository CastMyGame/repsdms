package com.reps.demogcloud.data;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import java.util.*;

final class BalanceSafeSave {
    private BalanceSafeSave() {}
    static <T> T save(MongoTemplate mongo,T entity,Class<T> type) {
        Document mapped=new Document();mongo.getConverter().write(entity,mapped);
        Object id=mapped.get("_id");
        String collection=mongo.getCollectionName(type);
        Query identity=Query.query(Criteria.where("_id").is(id));
        Query ownership=Query.query(Criteria.where("_id").is(id));
        ownership.fields().include(TenantOwnershipWriteGuard.FIELD);
        Document persisted=id==null?null:mongo.findOne(ownership,Document.class,collection);
        if(persisted==null) {
            TenantOwnershipWriteGuard.requireUnownedInsert(mapped);
            // New profiles start empty. Wallet grants only go through the transaction service.
            mapped.put("currency",0);
            mongo.insert(mapped,collection);
            return mongo.getConverter().read(type,mapped);
        }
        identity.addCriteria(TenantOwnershipWriteGuard.unchangedOwner(persisted,mapped));
        if(persisted.containsKey(TenantOwnershipWriteGuard.FIELD)
                && !mongo.exists(Query.query(Criteria.where("_id").is(persisted.get(TenantOwnershipWriteGuard.FIELD))),
                    com.reps.demogcloud.models.school.School.class)) throw TenantOwnershipWriteGuard.conflict();
        Update update=new Update();
        var metadata=mongo.getConverter().getMappingContext().getRequiredPersistentEntity(type);
        for(var property:metadata) {
            String field=property.getFieldName();
            if(property.isIdProperty() || property.isTransient() || "currency".equals(field)
                    || TenantOwnershipWriteGuard.FIELD.equals(field)) continue;
            if(mapped.containsKey(field)) update.set(field,mapped.get(field)); else update.unset(field);
        }
        T saved=mongo.findAndModify(identity,update,
                FindAndModifyOptions.options().returnNew(true),type);
        if(saved==null) throw TenantOwnershipWriteGuard.conflict();
        return saved;
    }
}
