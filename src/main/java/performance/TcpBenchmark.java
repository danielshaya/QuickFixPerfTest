package performance;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

/**
 * Created by daniel on 02/07/2015.
 * Simple program to test loopback speeds and latencies.
 */
/* running on an i7-3790X 3.5 Ghz with Ubuntu 15.04 - low latency

Starting latency test rate: 200000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 1,577,060 / 2,885,680  3,154,120 / 3,154,120  3,154,120 / 3,154,120 - 3,154,120 micro seconds

Starting latency test rate: 160000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 13  22 / 31  56 / 109 - 135 micro seconds

Starting latency test rate: 140000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 7.0  17 / 19  23 / 88 - 135 micro seconds

Starting latency test rate: 120000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 5.8  17 / 18  22 / 65 - 100 micro seconds

Starting latency test rate: 100000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 5.8  17 / 18  21 / 76 - 104 micro seconds


On my MBP

Starting latency test rate: 10000
Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 26  54 / 72  319 - 1,930 micro seconds

Starting latency test rate: 10000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 27  58 / 76  135 - 803 micro seconds

Starting latency test rate: 5000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 26  60 / 72  434 - 2,060 micro seconds

With the full fix message:

Starting latency test rate: 10000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 25 / 38  68 / 121  1,610 - 4,330 micro seconds

Starting latency test rate: 5000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 24 / 32  65 / 84  336 - 1,470 micro seconds

Starting latency test rate: 2000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 26 / 60  72 / 92  270 - 1,210 micro seconds
 */
public class TcpBenchmark {

    public final static int PORT = 8007;
    static final boolean COORDINATED_OMISSION = Boolean.getBoolean("coordinated.omission");

    private static final int SERVER_CPU = Integer.getInteger("server.cpu", 0);
    private static final int CLIENT_CPU = Integer.getInteger("client.cpu", 0);
    private static final int MAX_TESTS = 500_000;

    public static void main(@NotNull String... args) throws IOException {
        if (COORDINATED_OMISSION)
            System.out.println("### Running with Coordinated Omission ###");
        int port = args.length < 1 ? PORT : Integer.parseInt(args[0]);
        TcpBenchmark lbpp = new TcpBenchmark();

        lbpp.runServer(port);
        Jvm.pause(200);

        if (CLIENT_CPU > 0) {
            System.out.println("client cpu: " + CLIENT_CPU);
            Affinity.setAffinity(CLIENT_CPU);
        }
        SocketChannel socket = SocketChannel.open(new InetSocketAddress(port));
        socket.socket().setTcpNoDelay(true);
        socket.configureBlocking(false);

        for (int i : new int[]{10_000, 10_000, 5_000, 2_000})
            lbpp.testLatency(i, socket);
        System.exit(0);
    }

    public void runServer(int port) throws IOException {

        new Thread(() -> {
            if (SERVER_CPU > 0) {
                System.out.println("server cpu: " + SERVER_CPU);
                Affinity.setAffinity(SERVER_CPU);
            }
            ServerSocketChannel ssc = null;
            SocketChannel socket = null;
            try {
                ssc = ServerSocketChannel.open();
                ssc.bind(new InetSocketAddress(port));
                System.out.println("listening on " + ssc);

                socket = ssc.accept();
                socket.socket().setTcpNoDelay(true);
                socket.configureBlocking(false);

                System.out.println("Connected " + socket);

                ByteBuffer bb = ByteBuffer.allocateDirect(32 * 1024);
                int length;
                while ((length = socket.read(bb)) >= 0) {
                    if (length > 0) {
                        bb.flip();
                        bb.position(0);

                        if (socket.write(bb) < 0)
                            throw new EOFException();

                        bb.clear();
                    }
                }
            } catch (IOException ignored) {
            } finally {
                System.out.println("... disconnected " + socket);
                try {
                    if (ssc != null)
                        ssc.close();
                } catch (IOException ignored) {
                }
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException ignored) {
                }
            }
        }, "server").start();

    }

    public void testLatency(int targetThroughput, SocketChannel socket) throws IOException {
        System.out.print("\nStarting latency test rate: " + targetThroughput + " ... ");
        int tests = Math.min(120 * targetThroughput, MAX_TESTS);
        int count = 0;
        long now = System.nanoTime();
        long rate = (long) (1e9 / targetThroughput);

//        ByteBuffer bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
//        bb.putLong(0x123456789ABCDEFL);

        String fixMessage = "8=FIX.4.2\u00019=211\u000135=D\u000134=3\u000149=MY-INITIATOR-SERVICE\u000152=20160229-" +
                "09:04:14.459\u000156=MY-ACCEPTOR-SERVICE\u00011=ABCTEST1\u000111=863913604164909\u000121=3\u000122=5" +
                "\u000138=1\u000140=2\u000144=200\u000148=LCOM1\u000154=1\u000155=LCOM1\u000159=0\u000160=20160229-09:" +
                "04:14.459\u0001167=FUT\u0001200=201106\u000110=144\u0001\n";

        byte[] fixMessageBytes = fixMessage.getBytes();
        int length = fixMessageBytes.length;
        ByteBuffer bb = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        bb.put(fixMessageBytes);
        byte[] bytesReturned = new byte[235];

        Histogram h = new Histogram();
        for (int i = -20000; i < tests; i++) {
            if (COORDINATED_OMISSION)
                now = System.nanoTime();
            now += rate;
            while (System.nanoTime() < now)
                ;

            bb.position(0);
            while (bb.remaining() > 0)
                if (socket.write(bb) < 0)
                    throw new EOFException();

            bb.position(0);
            while (bb.remaining() > 0)
                if (socket.read(bb) < 0)
                    throw new EOFException();

//            if (bb.getLong(0) != 0x123456789ABCDEFL)
//                throw new AssertionError("read error");

            bb.flip();
            bb.get(bytesReturned);
            if(!Arrays.equals(bytesReturned, fixMessageBytes)) {
                throw new AssertionError("read error");
            }

            if (i >= 0)
                h.sample(System.nanoTime() - now);
        }

        System.out.println("Loop back echo latency " + h.toMicrosFormat() + " micro seconds");
    }
}