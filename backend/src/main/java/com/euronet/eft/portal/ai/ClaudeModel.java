package com.euronet.eft.portal.ai;

import com.anthropic.bedrock.backends.BedrockMantleBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.foundry.backends.FoundryBackend;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Calls Claude through the official Anthropic Java SDK: direct API, Amazon Bedrock or Microsoft Foundry, chosen by
 * {@code AI_PROVIDER}. Only used in LIVE mode; the client is created on first use so the portal starts without
 * credentials. Answers are constrained to the JSON schema of the requested record (structured outputs).
 */
@Component
public class ClaudeModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeModel.class);

    private final AiProperties props;
    private volatile AnthropicClient client;

    public ClaudeModel(AiProperties props) {
        this.props = props;
    }

    public record Reply<T>(T value, String model, long inputTokens, long outputTokens, long cacheReadTokens) {
    }

    public <T> Reply<T> structured(String system, String input, Class<T> type, AiTask.Effort effort, int maxTokens) {
        String model = modelId();
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                // stable instructions first and cacheable; the masked data goes in the user turn
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(input)
                .outputConfig(type)
                .outputConfig(StructuredOutputConfig.<T>builder().format(type).effort(effort(effort)).build())
                .build();
        StructuredMessage<T> response;
        try {
            response = client().messages().create(params);
        } catch (RateLimitException | OverloadedException e) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS, "The AI service is busy. Try again in a minute.", e);
        } catch (UnauthorizedException | PermissionDeniedException | NoCredentialsException e) {
            log.warn("AI provider rejected the credentials: {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is not configured correctly (credentials). Tell an administrator.", e);
        } catch (BadRequestException e) {
            log.warn("AI request rejected: {}", e.getMessage());
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI service rejected the request. Tell an administrator.", e);
        } catch (AnthropicInvalidDataException e) {
            log.warn("AI answer did not match the expected format: {}", e.getMessage());
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI answer could not be read. Try again.", e);
        } catch (AnthropicServiceException e) {
            log.warn("AI service error {}: {}", e.statusCode(), e.getMessage());
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI service returned an error. Try again later.", e);
        } catch (AnthropicIoException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new AiException(HttpStatus.GATEWAY_TIMEOUT, "The AI service could not be reached. Try again later.", e);
        }

        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            String category = response.stopDetails().flatMap(d -> d.category()).map(Object::toString).orElse("unspecified");
            log.info("AI model declined the request (category {})", category);
            throw new AiException(HttpStatus.UNPROCESSABLE_CONTENT, "The AI model declined to analyse this content.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI answer was too long and was cut off. Try again.");
        }
        T value = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new AiException(HttpStatus.BAD_GATEWAY, "The AI service returned an empty answer."));
        var usage = response.usage();
        return new Reply<>(value, props.model(), usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L));
    }

    /** Model id as the provider expects it. */
    String modelId() {
        String model = props.model();
        if (props.provider() == AiProperties.Provider.BEDROCK && !model.contains(".")) {
            return "anthropic." + model;
        }
        return model;
    }

    private static OutputConfig.Effort effort(AiTask.Effort effort) {
        return switch (effort) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    /** True when LIVE mode has what it needs to try a call (Bedrock/Foundry credentials are checked on first use). */
    public boolean configured() {
        return props.provider() != AiProperties.Provider.ANTHROPIC || (props.apiKey() != null && !props.apiKey().isBlank());
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = build();
                }
                c = client;
            }
        }
        return c;
    }

    private AnthropicClient build() {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .timeout(Duration.ofSeconds(100))
                .maxRetries(2);
        try {
            switch (props.provider()) {
                case ANTHROPIC -> {
                    if (!configured()) {
                        throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                                "AI is in live mode but ANTHROPIC_API_KEY is not set. Tell an administrator.");
                    }
                    builder.apiKey(props.apiKey());
                }
                case BEDROCK -> builder.backend(BedrockMantleBackend.fromEnv());
                case FOUNDRY -> builder.backend(FoundryBackend.fromEnv());
            }
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Could not set up the {} AI client: {}", props.provider(), e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service is not configured correctly (" + props.provider().name().toLowerCase() + "). Tell an administrator.", e);
        }
        return builder.build();
    }
}
