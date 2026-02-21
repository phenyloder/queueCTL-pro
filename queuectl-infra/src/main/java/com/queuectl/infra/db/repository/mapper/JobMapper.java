package com.queuectl.infra.db.repository.mapper;

import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import com.queuectl.infra.db.entity.Job;
import com.queuectl.infra.db.repository.support.JobStateCodec;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JobMapper {

  public Job toNewEntity(
      String queue, String command, List<String> args, int maxRetries, Instant runAt, Instant now) {
    Job entity = new Job();
    entity.setId(UUID.randomUUID());
    entity.setQueueName(queue);
    entity.setCommand(command);
    entity.setArgs(args.toArray(String[]::new));
    entity.setState(JobStateCodec.toDbState(JobState.PENDING));
    entity.setAttempts(0);
    entity.setMaxRetries(maxRetries);
    entity.setRunAt(runAt);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  public JobDto toDomain(Job entity) {
    String[] args = entity.getArgs();
    List<String> safeArgs = args == null ? List.of() : List.of(args.clone());
    return new JobDto(
        entity.getId(),
        entity.getQueueName(),
        entity.getCommand(),
        safeArgs,
        JobStateCodec.fromDbState(entity.getState()),
        entity.getAttempts(),
        entity.getMaxRetries(),
        entity.getRunAt(),
        entity.getLeaseId(),
        entity.getLeaseExpiresAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getLastError(),
        entity.getOutputRef());
  }

  public JobDto fromResultSet(ResultSet resultSet) throws SQLException {
    return new JobDto(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("queue"),
        resultSet.getString("command"),
        getTextArray(resultSet, "args"),
        JobStateCodec.fromDbState(resultSet.getString("state")),
        resultSet.getInt("attempts"),
        resultSet.getInt("max_retries"),
        toInstant(resultSet.getObject("run_at", OffsetDateTime.class)),
        resultSet.getObject("lease_id", UUID.class),
        toInstant(resultSet.getObject("lease_expires_at", OffsetDateTime.class)),
        toInstant(resultSet.getObject("created_at", OffsetDateTime.class)),
        toInstant(resultSet.getObject("updated_at", OffsetDateTime.class)),
        resultSet.getString("last_error"),
        resultSet.getString("output_ref"));
  }

  public static OffsetDateTime asOffset(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static List<String> getTextArray(ResultSet resultSet, String column) throws SQLException {
    Array array = resultSet.getArray(column);
    if (array == null) {
      return List.of();
    }

    try {
      Object raw = array.getArray();
      if (raw instanceof String[] strings) {
        return List.of(strings.clone());
      }
      if (raw instanceof Object[] objects) {
        List<String> values = new ArrayList<>(objects.length);
        for (Object object : objects) {
          values.add(object == null ? null : object.toString());
        }
        return values;
      }
      return List.of();
    } finally {
      array.free();
    }
  }

  private static Instant toInstant(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : offsetDateTime.toInstant();
  }
}
