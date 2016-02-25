package performance;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderList;
import quickfix.fix42.NewOrderSingle;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;

/**
 * Created by daniel on 19/02/2016.
 */
public class PerfTest {
    private final static int MESSAGE_COUNT = 40_000;
    private final static int IGNORE = 12_000;
    private final static int THROUGHPUT = 2_000;
    private final static int RATE = 1_000_000_000/THROUGHPUT;

    private static long[] serverReceivedAt = new long[MESSAGE_COUNT];
    private static long[] clientSentAt = new long[MESSAGE_COUNT];
    private static Histogram histogram = new Histogram();
    private static int count = 0;
    private static int RUNS = 6;

    public static void main(String[] args) throws ConfigError, InvalidMessage, IOException, SessionNotFound {
        Executors.newSingleThreadExecutor().submit(() ->
        {
            long lastTime = System.nanoTime();
            while(true){
                long time = System.nanoTime();
                if(time - lastTime > 5e6){
                    System.out.println("DELAY " + (time - lastTime)/100_000/10.0 + "ms");
                }
                lastTime = time;
            }
        });

        Executors.newSingleThreadExecutor().submit(() ->
        {
            QFServer server = new QFServer();
            server.start();
        });
        QFClient client = new QFClient();
        client.start();

        for(int i=0; i<RUNS; i++) {
            runTest(client, i % 2 != 0);
            count = 0;
            histogram = new Histogram();
        }
    }

    public static void runTest(QFClient client, boolean ACCOUNT_FOR_COORDINATED_OMMISSION) throws IOException, SessionNotFound, InvalidMessage, ConfigError {
        long now = 0;
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            if(i >= IGNORE){
                if(i==IGNORE){
                    Jvm.pause(500);
                    now=System.nanoTime();
                }

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

            client.sendNewOrderSingle();
        }

        while(histogram.totalCount() < MESSAGE_COUNT-IGNORE){
            Thread.yield();
        }

        System.out.println("------------------------------------------------------------------------------------------------------");
        System.out.println("Correcting for co-ordinated:" + ACCOUNT_FOR_COORDINATED_OMMISSION + " target throughtput:" + THROUGHPUT + "/s" + " = 1 message every " + (RATE/1000) + "us");
        //System.out.println("totalCount:" + (MESSAGE_COUNT-IGNORE) + " ave:" + (totalTime/((MESSAGE_COUNT-IGNORE)*1000)));
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
            //System.out.println(message);
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
            try {
                sendExecutionReport(sessionId, ((NewOrderSingle)message).getClOrdID());
            } catch (InvalidMessage invalidMessage) {
                invalidMessage.printStackTrace();
            } catch (ConfigError configError) {
                configError.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SessionNotFound sessionNotFound) {
                sessionNotFound.printStackTrace();
            }
        }

        private void sendExecutionReport(SessionID sessionId, ClOrdID clientOrderID) throws InvalidMessage, ConfigError, IOException, SessionNotFound {
            ExecutionReport executionReport = new ExecutionReport();
            executionReport.set(new AvgPx(110.11));
            executionReport.set(new CumQty(7));
            executionReport.set(clientOrderID);
            executionReport.set(new ClientID("TEST"));
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

        private void sendNewOrderSingle() throws InvalidMessage, ConfigError, IOException, SessionNotFound {
            NewOrderSingle newOrderSingle = new NewOrderSingle();

            newOrderSingle.set(new OrdType('2'));
            newOrderSingle.set(new Side('1'));
            newOrderSingle.set(new Symbol("LCOM1"));
            newOrderSingle.set(new ClOrdID(Long.toString(System.nanoTime())));
            newOrderSingle.set(new HandlInst('3'));
            newOrderSingle.set(new TransactTime(new Date()));
            newOrderSingle.set(new OrderQty(1));
            newOrderSingle.set(new Price(200.0));
            newOrderSingle.set(new TimeInForce('0'));
            newOrderSingle.set(new MaturityMonthYear("201106"));
            newOrderSingle.set(new SecurityType("FUT"));
            newOrderSingle.set(new IDSource("5"));
            newOrderSingle.set(new SecurityID("LCOM1"));
            newOrderSingle.set(new Account("ABCTEST1"));
            Session.sendToTarget(newOrderSingle, sessionId);
        }

        @Override
        public void fromAdmin(Message arg0, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, RejectLogon {
            System.out.println("Successfully called fromAdmin for sessionId : "
                    + arg0);
        }


        @Override
        public void fromApp(Message message, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
            long startTime = Long.parseLong(((ExecutionReport) message).getClOrdID().getValue());
            if(count++ >= IGNORE) {
                histogram.sample(System.nanoTime() - startTime);
            }
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

            if (isMessageOfType(message, MsgType.LOGON)) {
                ResetSeqNumFlag resetSeqNumFlag = new ResetSeqNumFlag();
                resetSeqNumFlag.setValue(true);
                ((quickfix.fix42.Logon)message).set(resetSeqNumFlag);
            }
            System.out.println("Inside toAdmin " + message);
            System.out.println("Inside toAdmin " + message);
        }

        @Override
        public void toApp(Message arg0, SessionID arg1) throws DoNotSend {
            //System.out.println("Message : " + arg0 + " for sessionid : " + arg1);
        }

        private boolean isMessageOfType(Message message, String type) {
            try {
                return type.equals(message.getHeader().getField(new MsgType()).getValue());
            } catch (FieldNotFound e) {
                return false;
            }
        }
    }
}
