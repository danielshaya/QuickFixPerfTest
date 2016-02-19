package server;

import quickfix.*;


/**
 * Created by daniel on 11/01/2016.
 */
public class QFServer {
    public static void main(String[] args) {
        SocketAcceptor socketAcceptor = null;
        try {
            SessionSettings executorSettings = new SessionSettings(
                    "src/main/resources/acceptorSettings.txt");
            Application application = new TestTradeAppExecutor();
            FileStoreFactory fileStoreFactory = new FileStoreFactory(
                    executorSettings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            FileLogFactory fileLogFactory = new FileLogFactory(executorSettings);
            socketAcceptor = new SocketAcceptor(application, fileStoreFactory,
                    executorSettings, fileLogFactory, messageFactory);
            socketAcceptor.start();
        } catch (ConfigError e) {
            e.printStackTrace();
        }

    }

    static class TestTradeAppExecutor implements Application {
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
            System.out.println(message);
        }
    }
}
