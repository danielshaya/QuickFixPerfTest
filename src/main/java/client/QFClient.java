package client;

import quickfix.*;
import quickfix.field.BeginSeqNo;
import quickfix.field.EndSeqNo;
import quickfix.fix42.ResendRequest;

import java.io.IOException;

/**
 * Created by daniel on 11/01/2016.
 */
public class QFClient {
    public static void main(String[] args) {
        SocketInitiator socketInitiator = null;
        try {
            SessionSettings sessionSettings = new SessionSettings("src/main/resources/initiatorSettings.txt");
            Application application = new TestApplication();
            FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
            FileLogFactory logFactory = new FileLogFactory(sessionSettings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            socketInitiator = new SocketInitiator(application,
                    fileStoreFactory, sessionSettings, logFactory,
                    messageFactory);
            socketInitiator.start();
            SessionID sessionId = socketInitiator.getSessions().get(0);
            Session.lookupSession(sessionId).logon();
            while(!Session.lookupSession(sessionId).isLoggedOn()){
                Thread.sleep(100);
            }

            for (int i = 0; i < 3; i++) {
                sendExecutionReport(sessionId);

                System.out.println("SenderNum:" + Session.lookupSession(sessionId).getExpectedSenderNum());
                System.out.println("TargetNum:" + Session.lookupSession(sessionId).getExpectedTargetNum());
            }


            //Session.lookupSession(sessionId).setNextSenderMsgSeqNum(89);
            //sendResend(sessionId);

            Thread.sleep(2000);
            Session.lookupSession(sessionId).logout();



            System.in.read();
        } catch (ConfigError e) {
            e.printStackTrace();
        } catch (Exception exp) {
            exp.printStackTrace();
        } finally {
            if (socketInitiator != null) {
                socketInitiator.stop(true);
            }
        }
    }

    private static void sendExecutionReport(SessionID sessionId) throws InvalidMessage, ConfigError, IOException {
        String testMessage = ("8=FIX.4.2|9=321|35=8|34=548|49=TKNG|52=20151124-14:30:10.761|56=APEX|6=110.11|" +
                "14=7|17=tkacct.151124.e.EFX.122.6|20=0|37=tkacct.151124.e.EFX.122|" +
                "39=2|54=1|55=EFX|150=2|151=0|" +
                "10=135|").replace( '|', (char) 0x01);
        Message message = MessageUtils.parse(Session.lookupSession(sessionId), testMessage);


        Session.lookupSession(sessionId).send(message);
    }

    private static void sendResend(SessionID sessionId)throws InvalidMessage, ConfigError{
        String testMessage = ("8=FIX.4.2|9=321|35=2|34=548|49=TKNG|52=20151124-14:30:10.761|56=APEX|7=2|16=3|" +
                "10=74|").replace( '|', (char) 0x01);
        Message message = MessageUtils.parse(Session.lookupSession(sessionId), testMessage);

        ResendRequest resendRequest = new ResendRequest();
        resendRequest.set(new BeginSeqNo(1));
        resendRequest.set(new EndSeqNo(1));

        Session.lookupSession(sessionId).send(resendRequest);
    }

    static class TestApplication extends MessageCracker implements Application {
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
            System.out.println("Message : " + arg0 + " for sessionid : " + arg1);
        }
    }
}
