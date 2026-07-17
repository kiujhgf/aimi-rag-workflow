package io.github.aimi.rag.workflow.log.mybatis.mapper;

import io.github.aimi.rag.workflow.log.mybatis.entity.StepFailure;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StepFailureMapper {

    void insert(StepFailure stepFailure);
}