package io.github.aimi.rag.workflow.log.mybatis.mapper;

import io.github.aimi.rag.workflow.log.mybatis.entity.StepExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StepExecutionMapper {

    void insert(StepExecution stepExecution);

    List<StepExecution> selectByExecutionId(@Param("executionId") String executionId);
}