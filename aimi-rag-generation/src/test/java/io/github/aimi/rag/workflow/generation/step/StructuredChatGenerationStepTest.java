package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredChatGenerationStepTest {

    public static class Person {
        private String name;
        private int age;

        public Person() {}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    private ChatModel mockModelReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = streamChunk(text);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));
        return chatModel;
    }

    @Test
    void shouldConvertModelOutputToDto() throws Exception {
        ChatModel chatModel = mockModelReturning("{\"name\":\"张三\",\"age\":30}");
        StructuredChatGenerationStep<Person> step =
                StructuredChatGenerationStep.builder(ChatClient.create(chatModel), Person.class).build();

        List<Message> messages = List.of(new UserMessage("给我一个示例人物"));
        FlowContext context = FlowContext.create().set("messages", messages);

        StepStatus status = step.process(context);

        assertEquals(StepStatus.FINISHED, status);
        Person person = context.get("structured", Person.class);
        assertEquals("张三", person.getName());
        assertEquals(30, person.getAge());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals(1, instructions.size());
        assertInstanceOf(UserMessage.class, instructions.get(0));
        assertTrue(instructions.get(0).getText().contains("给我一个示例人物"));
        assertTrue(instructions.get(0).getText().contains("JSON"));
    }

    @Test
    void shouldAssemblePromptsAndAppendFormatInstruction() throws Exception {
        ChatModel chatModel = mockModelReturning("{\"name\":\"李四\",\"age\":18}");
        StructuredChatGenerationStep<Person> step = StructuredChatGenerationStep
                .builder(ChatClient.create(chatModel), Person.class)
                .systemPrompt("你是{role}")
                .userPrompt("{question}")
                .build();

        FlowContext context = FlowContext.create()
                .set("role", "数据生成器")
                .set("question", "生成一个人物");

        step.process(context);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals(2, instructions.size());
        assertInstanceOf(SystemMessage.class, instructions.get(0));
        assertEquals("你是数据生成器", instructions.get(0).getText());
        assertInstanceOf(UserMessage.class, instructions.get(1));
        assertTrue(instructions.get(1).getText().contains("生成一个人物"));
        assertTrue(instructions.get(1).getText().contains("JSON"));

        Person person = context.get("structured", Person.class);
        assertEquals("李四", person.getName());
    }

    @Test
    void shouldSupportGenericListOutput() throws Exception {
        ChatModel chatModel = mockModelReturning("[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]");
        StructuredChatGenerationStep<List<Person>> step = StructuredChatGenerationStep.builder(
                ChatClient.create(chatModel), new ParameterizedTypeReference<List<Person>>() {}).build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("给两个人物")));

        step.process(context);

        @SuppressWarnings("unchecked")
        List<Person> people = (List<Person>) context.get("structured");
        assertEquals(2, people.size());
        assertEquals("a", people.get(0).getName());
        assertEquals(2, people.get(1).getAge());
    }

    @Test
    void shouldFailWhenOutputNotConvertible() {
        ChatModel chatModel = mockModelReturning("这不是 JSON");
        StructuredChatGenerationStep<Person> step =
                StructuredChatGenerationStep.builder(ChatClient.create(chatModel), Person.class).build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("你好")));

        assertThrows(StepExecuteException.class, () -> step.process(context));
    }

    @Test
    void shouldFailWhenNoMessagesAndNoPrompts() {
        ChatModel chatModel = mockModelReturning("{}");
        StructuredChatGenerationStep<Person> step =
                StructuredChatGenerationStep.builder(ChatClient.create(chatModel), Person.class).build();

        assertThrows(StepExecuteException.class, () -> step.process(FlowContext.create()));
        verify(chatModel, never()).stream(any(Prompt.class));
    }

    @Test
    void shouldExposeMetadata() {
        ChatModel chatModel = mockModelReturning("{}");
        StructuredChatGenerationStep<Person> step =
                StructuredChatGenerationStep.builder(ChatClient.create(chatModel), Person.class).build();

        assertEquals("structured-chat-generation", step.getName());
        assertEquals(StepType.GENERATION, step.getType());
        assertEquals(FlowCategory.QUERY, step.getCategory());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldUseCustomOutputConverter() throws Exception {
        StructuredOutputConverter<String> converter = mock(StructuredOutputConverter.class);
        when(converter.getFormat()).thenReturn("CUSTOM SCHEMA");
        when(converter.convert("raw")).thenReturn("parsed");
        ChatModel chatModel = mockModelReturning("raw");

        StructuredChatGenerationStep<String> step =
                StructuredChatGenerationStep.builder(ChatClient.create(chatModel), converter).build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("hi")));
        step.process(context);

        assertEquals("parsed", context.get("structured"));
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertTrue(captor.getValue().getInstructions().get(0).getText().contains("CUSTOM SCHEMA"));
    }

    @Test
    void shouldUseCustomOutputResolver() throws Exception {
        ChatModel chatModel = mockModelReturning("{\"name\":\"钱七\",\"age\":3}");
        OutputResolver<Person> resolver = (value, ctx) -> ctx.set("my-result", value);

        StructuredChatGenerationStep<Person> step = StructuredChatGenerationStep
                .builder(ChatClient.create(chatModel), Person.class)
                .outputResolver(resolver)
                .build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("hi")));
        step.process(context);

        assertNotNull(context.get("my-result"));
        assertNull(context.get("structured"));
    }

    @Test
    void shouldStreamChunksThenConvertToDto() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse first = streamChunk("{\"name\":\"张三\",");
        ChatResponse second = streamChunk("\"age\":30}");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(first, second));

        StructuredChatGenerationStep<Person> step = StructuredChatGenerationStep
                .builder(ChatClient.create(chatModel), Person.class)
                .build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("给我一个示例人物")));

        step.process(context);

        Person person = context.get("structured", Person.class);
        assertEquals("张三", person.getName());
        assertEquals(30, person.getAge());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        String text = captor.getValue().getInstructions().get(0).getText();
        assertTrue(text.contains("给我一个示例人物"));
        assertTrue(text.contains("JSON"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private static ChatResponse streamChunk(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(text));
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
