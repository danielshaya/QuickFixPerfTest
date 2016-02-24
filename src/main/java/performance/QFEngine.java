package performance;

import quickfix.*;
import quickfix.Message;
import quickfix.MessageFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Created by daniel on 19/02/2016.
 */
public class QFEngine {
    private QFClient client;
    private Consumer<Message> notifier;

    public QFEngine(Consumer<Message> notifier, String acceptorFile, String initiatorFile) throws ConfigError, InvalidMessage, IOException, SessionNotFound {
        this.notifier = notifier;

        if(acceptorFile != null) {
            Executors.newSingleThreadExecutor().submit(() ->
            {
                QFServer server = new QFServer();
                server.start(acceptorFile);
            });
        }

        if(initiatorFile != null) {
            Executors.newSingleThreadExecutor().submit(() ->
            {
                client = new QFClient();
                client.start(initiatorFile);
            });
        }
    }

    public void sendMessage(Message message) throws IOException, SessionNotFound, InvalidMessage, ConfigError {
        client.sendMessage(message);
    }

    public class QFServer implements Application{
        public void start(String file) {
            SocketAcceptor socketAcceptor = null;
            try {
                SessionSettings executorSettings = new SessionSettings(
                        "src/main/resources/" +file);
                FileStoreFactory fileStoreFactory = new FileStoreFactory(
                        executorSettings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                FileLogFactory fileLogFactory = new FileLogFactory(executorSettings);
                socketAcceptor = new SocketAcceptor(this, fileStoreFactory,
                        executorSettings, fileLogFactory, messageFactory);
                socketAcceptor.start();
            } catch (ConfigError e) {
                e.printStackTrace();
            }
        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#onCreate(quickfix.SessionID)
         */
        @Override
        public void onCreate(SessionID sessionId) {
            System.out.println("Executor Session Created with SessionID = " + sessionId);
        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#onLogon(quickfix.SessionID)
         */
        @Override
        public void onLogon(SessionID sessionId) {
            System.out.println("client logged on");
        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#onLogout(quickfix.SessionID)
         */
        @Override
        public void onLogout(SessionID sessionId) {

        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#toAdmin(quickfix.Message, quickfix.SessionID)
         */
        @Override
        public void toAdmin(Message message, SessionID sessionId) {

        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#fromAdmin(quickfix.Message, quickfix.SessionID)
         */
        @Override
        public void fromAdmin(Message message, SessionID sessionId)
                throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
                RejectLogon {

        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#toApp(quickfix.Message, quickfix.SessionID)
         */
        @Override
        public void toApp(Message message, SessionID sessionId) throws DoNotSend {
            System.out.println(message);
        }

        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#fromApp(quickfix.Message, quickfix.SessionID)
         */
        @Override
        public void fromApp(Message message, SessionID sessionId)
                throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
                UnsupportedMessageType {
            notifier.accept(message);
            System.out.println("Server receives " + message);
        }
    }


    public class QFClient implements Application{
        private SessionID sessionId = null;

        public void start(String file) {
            SocketInitiator socketInitiator;
            try {
                SessionSettings sessionSettings = new SessionSettings("src/main/resources/" + file);
                FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
                FileLogFactory logFactory = new FileLogFactory(sessionSettings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                socketInitiator = new SocketInitiator(this,
                        fileStoreFactory, sessionSettings, logFactory,
                        messageFactory);
                socketInitiator.start();
                sessionId = socketInitiator.getSessions().get(0);
                Session.lookupSession(sessionId).logon();
                while(!Session.lookupSession(sessionId).isLoggedOn()){
                    Thread.sleep(100);
                }

            } catch (ConfigError e) {
                e.printStackTrace();
            } catch (Exception exp) {
                exp.printStackTrace();
            }
        }

        public void sendMessage(Message message) throws InvalidMessage, ConfigError, IOException, SessionNotFound {
            Session.sendToTarget(message, sessionId);
        }

        @Override
        public void fromAdmin(Message arg0, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, RejectLogon {
            System.out.println("Successfully called fromAdmin for sessionId : "
                    + arg0);
        }

        @Override
        public void fromApp(Message arg0, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
            System.out.println("Successfully called fromApp for sessionId : "
                    + arg0);
        }

        @Override
        public void onCreate(SessionID arg0) {
            System.out.println("Successfully called onCreate for sessionId : "
                    + arg0);
        }

        @Override
        public void onLogon(SessionID arg0) {
            System.out.println("Successfully logged on for sessionId : " + arg0);
        }

        @Override
        public void onLogout(SessionID arg0) {
            System.out.println("Successfully logged out for sessionId : " + arg0);
        }

        @Override
        public void toAdmin(Message message, SessionID sessionId) {
            System.out.println("Inside toAdmin " + message);
        }

        @Override
        public void toApp(Message arg0, SessionID arg1) throws DoNotSend {
            //System.out.println("Message : " + arg0 + " for sessionid : " + arg1);
        }
    }
}
