package com.reps.demogcloud.services.punishment;


import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.punishment.StudentAnswer;

import jakarta.mail.MessagingException;
import java.util.List;

public interface PunishmentClosureService {

    PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws MessagingException;

    PunishmentResponse closeByPunishmentId(String punishmentId) throws MessagingException;

    Punishment rejectLevelThree(String punishmentId) throws MessagingException;

    Punishment archiveRecord(String punishmentId, String userId, String explanation) throws MessagingException;

    Punishment restoreRecord(String punishmentId) throws MessagingException;
}
