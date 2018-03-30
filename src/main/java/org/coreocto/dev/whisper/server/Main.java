package org.coreocto.dev.whisper.server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.apache.log4j.Logger;
import org.coreocto.dev.whisper.bean.NewMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.sleep;

public class Main {

//    private final AtomicReference<HashMap<String,String>> userTokenMap = new AtomicReference<HashMap<String, String>>();

    static Map<String, String> userTokenMap = new HashMap<>();

    //static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);
    static final Logger LOGGER = Logger.getLogger(Main.class);

    public static final String NOTIFICATION_CONTENT = "content";
    public static final String NOTIFICATION_FROM = "from";
    public static final String NOTIFICATION_DATE = "date";

    public static void main(String[] args) {
        try (InputStream serviceAccount = Main.class.getResourceAsStream("/whisper-firebase-firebase-adminsdk-zhmk8-8a17e22225.json")) {

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://whisper-firebase.firebaseio.com")
                    .build();

            FirebaseApp.initializeApp(options);

            Query userQuery = FirebaseDatabase.getInstance().getReference().child("tusers").orderByChild("email");

            userQuery.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    LOGGER.debug("" + snapshot.getChildrenCount());
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        Object o = dataSnapshot.getValue();
                        if (o instanceof Map) {
                            Map dataMap = (HashMap) dataSnapshot.getValue();
                            String token = (String) dataMap.get("token");

                            if (token != null && token.length() == 152) {
                                String email = (String) dataMap.get("email");
                                synchronized (userTokenMap) {
                                    userTokenMap.put(email, token);
                                }
                                LOGGER.debug("" + o);
                            }
                        } else if (o instanceof String) { //only the token is updated at client side
                            LOGGER.debug("" + o);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.error(error.getDetails());
                }
            });

            Query msgQuery = FirebaseDatabase.getInstance().getReference().child("tmessages").orderByChild("createDt");

            msgQuery.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    LOGGER.debug("ChildrenCount = " + snapshot.getChildrenCount());
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {

                        NewMessage newMessage = dataSnapshot.getValue(NewMessage.class);

                        if (newMessage.getStatus() == 0) {

                            LOGGER.debug("message.status = " + newMessage.getStatus());
                            String content = newMessage.getContent();
                            String to = newMessage.getTo();
                            String from = newMessage.getFrom();
                            long createDt = newMessage.getCreateDt();

                            synchronized (userTokenMap) {
                                String token = userTokenMap.get(to);
                                if (token != null && token.length() == 152) {
                                    // See documentation on defining a message payload.
                                    Message message = Message.builder()
                                            .putData(NOTIFICATION_CONTENT, content + Constants.EXTRA_SEP + from + Constants.EXTRA_SEP + createDt)
                                            //.putData(NOTIFICATION_FROM, from)
                                            //.putData(NOTIFICATION_DATE, createDt + "")
                                            //throw error when adding more data :(
                                            //current workaround, concat it into a string and split it at client side
                                            .setToken(token)
                                            .build();

                                    // Send a message to the device corresponding to the provided registration token.
                                    String response = null;
                                    try {
                                        response = FirebaseMessaging.getInstance().sendAsync(message).get();

                                        // Response is a message ID string.
                                        LOGGER.debug("Successfully sent message: " + response);

                                    } catch (InterruptedException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    } catch (ExecutionException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }

                                    //mark the message as sent
                                    Map<String, Object> newStatus = new HashMap<>();
                                    newStatus.put("status", 1);
                                    dataSnapshot.getRef().updateChildrenAsync(newStatus);

                                } else {
                                    LOGGER.error("invalid token: " + token);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.error(error.getDetails());
                }
            });

            //the call above is async, we need to wait until it returns the result
            while (true) {
                sleep(5000);
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
