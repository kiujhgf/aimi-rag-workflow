package io.github.aimi.rag.workflow.openai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual configuration for OpenAI embedding. Uses only {@code spring-ai-openai}
 * core library (no autoconfigure starter), so no unrelated auto-configurations
 * (Chat, Audio, Image, Moderation) are pulled in.
 *
 * Import this class explicitly in your Spring Boot application:
 * <pre>
 * &#064;Import(OpenAiEmbeddingConfig.class)
 * </pre>
 */
@Configuration
public class OpenAiEmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.api-key}") String apiKey) {
        return OpenAiApi.builder().apiKey(apiKey).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi);
    }
}
