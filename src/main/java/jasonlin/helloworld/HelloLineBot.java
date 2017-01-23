package jasonlin.helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linecorp.bot.client.LineMessagingService;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.CallbackRequest;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.*;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by noteb on 2017/1/21.
 */
public class HelloLineBot implements RequestHandler<InputStream, Object> {
    private static LambdaLogger logger;
    private static LineMessagingService lineMessagingService;

    @Override
    public String handleRequest(InputStream input, Context context) {
        logger = context.getLogger();
        try {
            buildLineMessagingService();
            Event e = getLineCallbackEvent(input);
            if (e instanceof MessageEvent){
                MessageEvent event = (MessageEvent) e;
                MessageContent messageContent = event.getMessage();
                if (messageContent instanceof TextMessageContent) {
                    MessageEvent<TextMessageContent> messageEvent = (MessageEvent<TextMessageContent>) e;
                    handleTextMessageEvent(messageEvent);
                } else {
                    String replyToken = event.getReplyToken();
                    String replyMessage = "Sorry, I can only reply text messages now.";
                    this.reply(replyToken, new TextMessage(replyMessage));
                }
            } else {
                logger.log("It isn't a MessageEvent instance...\n");
            }
        } catch (Exception e) {
            logger.log("It isn't a Line Message...\n");
            e.printStackTrace();
        }
        return "Success to call the Lambda function.";
    }

    private void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws IOException{
        logger.log("Got text event: " + event + "\n");
        handleTextContent(event.getReplyToken(), event, event.getMessage());
    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws IOException {
        String text = content.getText();
        String helpText = "(I am an echo bot now!)";
        logger.log(String.format("Returns echo message : %s", text) + "\n");

        this.reply(
                replyToken,
                Arrays.asList(
                        new TextMessage("You said : " + text),
                        new TextMessage(helpText)
                )
        );
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) throws IOException {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) throws IOException {
        try {
            Response<BotApiResponse> apiResponse = lineMessagingService
                    .replyMessage(new ReplyMessage(replyToken, messages))
                    .execute();
            logger.log(String.format("Sent messages: %s %s", apiResponse.message(), apiResponse.code()) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void buildLineMessagingService() {
        lineMessagingService = LineMessagingServiceBuilder.create(System.getenv("LINE_BOT_CHANNEL_TOKEN")).build();
    }

    private Event getLineCallbackEvent(InputStream lambdaInput) throws Exception {
        String json = IOUtils.toString(lambdaInput, "UTF-8");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        CallbackRequest callbackRequest = objectMapper.readValue(json, CallbackRequest.class);
        Event e = callbackRequest.getEvents().get(0);
        return e;
    }
}
