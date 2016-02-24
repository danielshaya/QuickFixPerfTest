package performance;

import net.openhft.chronicle.core.Jvm;
import quickfix.ConfigError;
import quickfix.InvalidMessage;
import quickfix.SessionNotFound;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;

import java.io.IOException;

/**
 * Created by daniel on 19/02/2016.
 */
public class QFEngineMain {
    public static void main(String[] args) throws IOException, SessionNotFound, InvalidMessage, ConfigError {
        QFEngine serverEngine = new QFEngine(s-> System.out.println("server received" + s), "qfServerAcceptorSettings.txt", null);


        QFEngine clientEngine = new QFEngine(s-> System.out.println("client received" + s), null, "qfClientInitiatorSettings.txt");

        Jvm.pause(3000);

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

        clientEngine.sendMessage(executionReport);
    }
}
