package com.example.ai.config;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class SlackConfig {

    @Value("${slack.bot-token}")
    private String botToken;

    @Value("${slack.channel}")
    private String channel;

    @Bean
    public MethodsClient slackMethodsClient() {
        return Slack.getInstance().methods(botToken);
    }
}
