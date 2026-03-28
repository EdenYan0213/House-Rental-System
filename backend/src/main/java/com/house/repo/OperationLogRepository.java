package com.house.repo;

import com.house.model.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository
        extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    @Query("select distinct o.module from OperationLog o where o.module is not null and o.module <> '' order by o.module asc")
    List<String> findDistinctModules();

    @Query("select distinct o.action from OperationLog o where o.action is not null and o.action <> '' order by o.action asc")
    List<String> findDistinctActions();

    @Query("select distinct coalesce(o.username, o.operator) from OperationLog o where coalesce(o.username, o.operator) is not null and coalesce(o.username, o.operator) <> '' order by coalesce(o.username, o.operator) asc")
    List<String> findDistinctOperators();

    @Query(value = """
            select date_format(operation_time, '%Y-%m-%d') as d, count(*) as c
            from operation_log
            where operation_time >= ?1 and operation_time <= ?2
            group by d
            order by d
            """, nativeQuery = true)
    List<Object[]> trend(LocalDateTime start, LocalDateTime end);

    @Query(value = """
            select module, count(*) as c
            from operation_log
            where operation_time >= ?1 and operation_time <= ?2
            group by module
            order by c desc
            """, nativeQuery = true)
    List<Object[]> moduleDistribution(LocalDateTime start, LocalDateTime end);

    @Query(value = """
            select coalesce(username, operator) as op, count(*) as c
            from operation_log
            where operation_time >= ?1 and operation_time <= ?2
            group by op
            order by c desc
            limit ?3
            """, nativeQuery = true)
    List<Object[]> topOperators(LocalDateTime start, LocalDateTime end, int limit);

    @Query(value = """
            select module, action, coalesce(change_summary, action) as summary, max(duration_ms) as max_duration
            from operation_log
            where operation_time >= ?1 and operation_time <= ?2 and duration_ms is not null
            group by module, action, summary
            order by max_duration desc
            limit ?3
            """, nativeQuery = true)
    List<Object[]> slowTop(LocalDateTime start, LocalDateTime end, int limit);

    @Query(value = """
            select module, action, count(*) as c
            from operation_log
            where operation_time >= ?1 and operation_time <= ?2 and result = 0
            group by module, action
            order by c desc
            """, nativeQuery = true)
    List<Object[]> failStats(LocalDateTime start, LocalDateTime end);
}
