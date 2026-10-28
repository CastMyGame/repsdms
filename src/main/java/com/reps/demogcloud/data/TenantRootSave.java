package com.reps.demogcloud.data;

import com.reps.demogcloud.models.school.School;
import org.bson.Document;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.dao.*;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;

/** Protect CURRENT mapped root save paths. Never a migration or authorization API. */
public final class TenantRootSave {
    private TenantRootSave() {}
    @SuppressWarnings("unchecked")
    public static <T> T save(MongoTemplate mongo,T entity) {
        Class<T> type=(Class<T>)entity.getClass();
        var metadata=mongo.getConverter().getMappingContext().getRequiredPersistentEntity(type);
        Document mapped=new Document();mongo.getConverter().write(entity,mapped);
        Object id=mapped.get("_id");String collection=mongo.getCollectionName(type);
        var version=metadata.getVersionProperty();
        Query projection=Query.query(Criteria.where("_id").is(id));projection.fields().include(TenantOwnershipWriteGuard.FIELD);
        if(version!=null)projection.fields().include(version.getFieldName());
        Document before=id==null?null:mongo.findOne(projection,Document.class,collection);
        if(before==null) return insert(mongo,entity);
        if(entity instanceof Persistable<?> p && p.isNew()) throw new DuplicateKeyException("Case already exists; reload before retrying");
        Query query=Query.query(Criteria.where("_id").is(id)).addCriteria(TenantOwnershipWriteGuard.unchangedOwner(before,mapped));
        knownOwner(mongo,before.get(TenantOwnershipWriteGuard.FIELD));
        snapshots(mapped,before.get(TenantOwnershipWriteGuard.FIELD));
        Update update=new Update();
        for(var property:metadata) {
            String field=property.getFieldName();
            if(property.isIdProperty() || property.isTransient() || property.isVersionProperty() || TenantOwnershipWriteGuard.FIELD.equals(field))continue;
            if(mapped.containsKey(field))update.set(field,mapped.get(field));else update.unset(field);
        }
        if(version!=null){
            Object expected=mapped.get(version.getFieldName());
            query.addCriteria(Criteria.where(version.getFieldName()).is(expected));
            update.set(version.getFieldName(),expected==null?0L:((Number)expected).longValue()+1);
        }
        T saved=mongo.findAndModify(query,update,FindAndModifyOptions.options().returnNew(true),type);
        if(saved==null)throw new OptimisticLockingFailureException("Root changed; reload before retrying");
        if(version!=null)new BeanWrapperImpl(entity).setPropertyValue(version.getName(),new BeanWrapperImpl(saved).getPropertyValue(version.getName()));
        mark(entity);mark(saved);return saved;
    }
    public static <T> T insert(MongoTemplate mongo,T entity) {
        var bean=new BeanWrapperImpl(entity);
        if(bean.isReadableProperty(TenantOwnershipWriteGuard.FIELD))knownOwner(mongo,bean.getPropertyValue(TenantOwnershipWriteGuard.FIELD));
        Document mapped=new Document();mongo.getConverter().write(entity,mapped);snapshots(mapped,mapped.get(TenantOwnershipWriteGuard.FIELD));
        T saved=mongo.insert(entity);mark(entity);mark(saved);return saved;
    }
    private static void knownOwner(MongoTemplate mongo,Object owner){
        if(owner==null)return;
        if(!(owner instanceof String id) || id.isBlank() || !id.equals(id.trim())
                || !mongo.exists(Query.query(Criteria.where("_id").is(id)),School.class))throw TenantOwnershipWriteGuard.conflict();
    }
    private static void snapshots(Document mapped,Object owner){
        if(owner==null || !(mapped.get("assignmentWork") instanceof Document work))return;
        snapshot(work.get("template"),owner);
        if(work.get("submissions") instanceof java.util.List<?> submissions)
            for(Object row:submissions)if(row instanceof Document submission)snapshot(submission.get("template"),owner);
    }
    private static void snapshot(Object value,Object owner){
        if(value==null)return;
        if(!(value instanceof Document template))throw TenantOwnershipWriteGuard.conflict();
        boolean global=Boolean.TRUE.equals(template.get("createdBySystem")) && "SYSTEM_DEFAULT".equals(template.get("scope"))
                && !template.containsKey("tenantSchoolId") && empty(template.get("schoolId")) && empty(template.get("createdByUserId"));
        if(!global && !owner.equals(template.get("tenantSchoolId")))throw TenantOwnershipWriteGuard.conflict();
    }
    private static boolean empty(Object value){return value==null || "".equals(value);}
    public static void delete(MongoTemplate mongo,Object entity){
        Document mapped=new Document();mongo.getConverter().write(entity,mapped);
        var metadata=mongo.getConverter().getMappingContext().getRequiredPersistentEntity(entity.getClass());
        var query=Query.query(Criteria.where("_id").is(mapped.get("_id")).and("tenantSchoolId").exists(false));
        var version=metadata.getVersionProperty();if(version!=null)query.addCriteria(Criteria.where(version.getFieldName()).is(mapped.get(version.getFieldName())));
        if(mongo.remove(query,entity.getClass()).getDeletedCount()!=1)throw TenantOwnershipWriteGuard.conflict();
    }
    private static void mark(Object entity){
        var bean=new BeanWrapperImpl(entity);if(bean.isWritableProperty("persisted"))bean.setPropertyValue("persisted",true);
    }
}
