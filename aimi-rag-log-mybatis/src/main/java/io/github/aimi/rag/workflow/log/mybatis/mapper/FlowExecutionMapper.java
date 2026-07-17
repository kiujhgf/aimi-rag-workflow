package io.github.aimi.rag.workflow.log.mybatis.mapper;

import io.github.aimi.rag.workflow.log.mybatis.entity.FlowExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlowExecutionMapper {

    void insert(FlowExecution flowExecution);

    FlowExecution selectById(@Param("id") String id);

    void updateStatus(@Param("id") String id, @Param("status") String status, @Param("endTime") java.time.Instant endTime);
}