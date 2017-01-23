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
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.event.*;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.response.BotApiResponse;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
            if (e instanceof MessageEvent) {
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
            } else if (e instanceof PostbackEvent) {
                handlePostbackEvent((PostbackEvent) e);
            } else if (e instanceof JoinEvent) {
                handleJoinEvent((JoinEvent) e);
            } else {
                logger.log("It isn't a MessageEvent instance...\n");
            }
        } catch (IOException ioe) {
            logger.log("Something wrong ...\n");
            ioe.printStackTrace();
        } catch (Exception e) {
            logger.log("It isn't a Line Message...\n");
            e.printStackTrace();
        }
        return "Success to call the Lambda function.";
    }

    private void handleJoinEvent(JoinEvent event) throws Exception {
        logger.log(String.format("Got join event: %s\n", event));
        String replyToken = event.getReplyToken();
        Source source = event.getSource();
        if (source instanceof GroupSource) {
            this.reply(replyToken,
                       Arrays.asList(
                               new TextMessage("I joined a group : " + ((GroupSource) source).getGroupId()),
                               new TextMessage(getHelpMessages())
                       ));
        } else if (source instanceof RoomSource) {
            this.reply(replyToken,
                       Arrays.asList(
                               new TextMessage("I joined a room : " + ((GroupSource) source).getGroupId()),
                               new TextMessage(getHelpMessages())
                       ));
        } else {
            this.reply(replyToken,
                       Arrays.asList(
                               new TextMessage("I joined a ??? : " + ((GroupSource) source).getGroupId()),
                               new TextMessage(getHelpMessages())
                       ));
        }
    }

    private void handlePostbackEvent(PostbackEvent event) throws Exception {
        logger.log(String.format("Got postBack event: %s\n", event));
        String replyToken = event.getReplyToken();
        String data = event.getPostbackContent().getData();
        Source source = event.getSource();
        switch (data) {
            case "bye:yes": {
                this.replyText(replyToken, "Bye, see you again ...");
                lineMessagingService.leaveGroup(
                        ((GroupSource) source).getGroupId()
                ).execute();
                break;
            }

            case "bye:no": {
                this.replyText(replyToken, "Ok, let us keep talking!");
                break;
            }

            default:
                this.replyText(replyToken, "Got postback event : " + event.getPostbackContent().getData());
        }
    }

    private void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        logger.log("Got text event: " + event + "\n");
        handleTextContent(event.getReplyToken(), event, event.getMessage());
    }

    private String getHelpMessages() {
        String helpText = "You can input the following messages to see each results:\n" +
                          "(0) readme\n" +
                          "(1) byebye\n" +
                          "(2) Get my ID\n";
        return helpText;
    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws Exception {
        String text = content.getText();

        logger.log(String.format("Got text message : %s\n", text));

        switch (text) {
            case "readme":
            case "0": {
                String helpText = getHelpMessages();
                this.replyText(replyToken, helpText);
                break;
            }

            case "byebye":
            case "1": {
                leaveRoomOrGroup(replyToken, event);
                break;
            }

            case "Get my ID":
            case "2": {
                String id = getSourceId(event);
                this.replyText(replyToken, "Your ID is " + id);
                break;
            }

            default: {
                echoUserMessage(replyToken, text);
            }
        }
    }

    private void leaveRoomOrGroup(String replyToken, Event event) throws Exception {
        Source source = event.getSource();
        if (source instanceof GroupSource) {
            ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                    "I am going to leave this group, are you sure?",
                    new PostbackAction("Yes,right now!", "bye:yes"),
                    new PostbackAction("No,stay here!", "bye:no")
            );
            TemplateMessage templateMessage = new TemplateMessage(
                    "Sorry, I don't support the Confirm function in your platform. :(",
                    confirmTemplate
            );
            this.reply(replyToken, templateMessage);
        } else if (source instanceof RoomSource) {
            ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                    "I am going to leave this room, are you sure?",
                    new PostbackAction("Yes,right now!", "bye:yes"),
                    new PostbackAction("No,stay here!", "bye:no")
            );
            TemplateMessage templateMessage = new TemplateMessage(
                    "Sorry, I don't support the Confirm function in your platform. :(",
                    confirmTemplate
            );
            this.reply(replyToken, templateMessage);
        } else {
            this.replyText(replyToken, "Bot can't leave from 1:1 chat");
        }
    }

    private void echoUserMessage(String replyToken, String text) throws Exception {
        String helpText = getHelpMessages();
        logger.log(String.format("Returns echo message : %s\n", text));
        this.reply(
                replyToken,
                Arrays.asList(
                        new TextMessage("You said : " + text),
                        new TextMessage(helpText)
                )
        );
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) throws Exception {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) throws Exception {
        Response<BotApiResponse> apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(replyToken, messages))
                .execute();
        logger.log(String.format("Sent messages: %s %s", apiResponse.message(), apiResponse.code()) + "\n");
    }

    private void buildLineMessagingService() throws Exception {
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

    private void replyText(@NonNull String replyToken, @NonNull String message) throws Exception {
        if (replyToken.isEmpty()) {
            throw new IllegalArgumentException("replyToken must not be empty");
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000 - 2) + "……";
        }
        this.reply(replyToken, new TextMessage(message));
    }

    private String getSourceId(Event event) throws Exception {
        Source source = event.getSource();
        String id;
        if (source instanceof GroupSource) {
            id = ((GroupSource) source).getGroupId();
        } else if (source instanceof RoomSource) {
            id = ((RoomSource) source).getRoomId();
        } else {
            id = source.getUserId();
        }
        return id;
    }
}
