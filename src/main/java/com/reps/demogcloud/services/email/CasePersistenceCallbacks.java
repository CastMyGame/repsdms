package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import org.bson.Document;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveCallback;
import org.springframework.stereotype.Component;

/** Mapping callbacks must not depend on MongoTemplate or repository-backed services. */
@Component
public class CasePersistenceCallbacks implements AfterConvertCallback<Object>, AfterSaveCallback<Object> {
    @Override public Object onAfterConvert(Object entity, Document source, String collection) {
        return markPersisted(entity);
    }
    @Override public Object onAfterSave(Object entity, Document source, String collection) {
        return markPersisted(entity);
    }
    private Object markPersisted(Object entity) {
        if (entity instanceof Punishment || entity instanceof OfficeReferral || entity instanceof GuidanceReferral)
            new BeanWrapperImpl(entity).setPropertyValue("persisted", true);
        return entity;
    }
}
