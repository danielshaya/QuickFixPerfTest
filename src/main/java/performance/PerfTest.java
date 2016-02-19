package performance;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;

import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * Created by daniel on 19/02/2016.
 */
public class PerfTest {
    private final static int MESSAGE_COUNT = 20_000;
    private final static int IGNORE = 10_000;
    private final static int THROUGHPUT = 5_000;
    private final static int RATE = 1_000_000_000/THROUGHPUT;
    //private final static boolean ACCOUNT_FOR_COORDINATED_OMMISSION = true;

    private static long[] serverReceivedAt = new long[MESSAGE_COUNT];
    private static long[] clientSentAt = new long[MESSAGE_COUNT];

    public static void main(String[] args) throws ConfigError, InvalidMessage, IOException, SessionNotFound {
        Executors.newSingleThreadExecutor().submit(() ->
        {
            QFServer server = new QFServer();
            server.start();
        });
        QFClient client = new QFClient();
        client.start();

        runTest(client, false);
        runTest(client, true);
    }

    public static void runTest(QFClient client, boolean ACCOUNT_FOR_COORDINATED_OMMISSION) throws IOException, SessionNotFound, InvalidMessage, ConfigError {
        long now = 0;
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            if(i >= IGNORE){
                if(i==IGNORE)now=System.nanoTime();

                if(ACCOUNT_FOR_COORDINATED_OMMISSION) {
                    now += RATE;
                    while (System.nanoTime() < now)
                        ;
                }else{
                    Jvm.busyWaitMicros(RATE/1000);
                    now = System.nanoTime();
                }
                clientSentAt[i-IGNORE] = now;

            }else{
                now = System.nanoTime();
            }

            client.sendExecutionReport();
        }

        Histogram histogram = new Histogram();
        long totalTime = 0;
        for(int i=0; i<MESSAGE_COUNT-IGNORE; i++){
            long diff = serverReceivedAt[i] - clientSentAt[i];
            //System.out.println(diff /1000);
            histogram.sample(diff);
            totalTime += diff;
        }
        System.out.println("------------------------------------------------------------------------------------------------------");
        System.out.println("account for co-ordinated:" + ACCOUNT_FOR_COORDINATED_OMMISSION + " target throughtput:" + THROUGHPUT + "/s" + " = 1 message every " + (RATE/1000) + "us");
        System.out.println("totalCount:" + (MESSAGE_COUNT-IGNORE) + " ave:" + (totalTime/((MESSAGE_COUNT-IGNORE)*1000)));
        System.out.println("totalCount:" + histogram.totalCount() + " by percentile:" + histogram.toMicrosFormat());
    }

    public static class QFServer implements Application{
        public void start() {
            SocketAcceptor socketAcceptor = null;
            try {
                SessionSettings executorSettings = new SessionSettings(
                        "src/main/resources/acceptorSettings.txt");
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

        int count = 0;
        /**
         * (non-Javadoc)
         *
         * @see quickfix.Application#fromApp(quickfix.Message, quickfix.SessionID)
         */
        @Override
        public void fromApp(Message message, SessionID sessionId)
                throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
                UnsupportedMessageType {
            //System.out.println("Server receives " + message);
            if(count >=IGNORE)serverReceivedAt[count-IGNORE] = System.nanoTime();
            count ++;
            if(count==MESSAGE_COUNT){
                count =0;
            }
        }
    }


    public static class QFClient implements Application{
        private SessionID sessionId = null;

        public void start() {
            SocketInitiator socketInitiator = null;
            try {
                SessionSettings sessionSettings = new SessionSettings("src/main/resources/initiatorSettings.txt");
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

        private void sendExecutionReport() throws InvalidMessage, ConfigError, IOException, SessionNotFound {
            ExecutionReport executionReport = new ExecutionReport();
            executionReport.set(new AvgPx(110.11));
            executionReport.set(new CumQty(7));
            executionReport.set(new ExecID("tkacct.151124.e.EFX.122.6"));
            executionReport.set(new OrderID("tkacct.151124.e.EFX.122.6"));
            executionReport.set(new Side('1'));
            executionReport.set(new Symbol("EFX"));
            executionReport.set(new ExecType('2'));
            executionReport.set(new ExecTransType('0'));
            executionReport.set(new OrdStatus('0'));
            executionReport.set(new LeavesQty(0));
            Session.sendToTarget(executionReport, sessionId);
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
