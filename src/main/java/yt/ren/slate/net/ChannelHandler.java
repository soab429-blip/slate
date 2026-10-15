package yt.ren.slate.net;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.ArrayDeque;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelHandler extends ChannelDuplexHandler implements Runnable {
    private static final AtomicInteger counter = new AtomicInteger();
    private final ArrayDeque<Object[]> in = new ArrayDeque<>();
    private final ArrayDeque<Object[]> out = new ArrayDeque<>();
    private Future<?> pollTask;

    private int onPacket(Object packet, boolean outbound, long elapsed, int id) {
        return PacketDispatch.dispatch(packet, outbound, elapsed, id);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        int id = counter.getAndIncrement();
        int action = onPacket(message, true, 0L, id);
        if (action == 2) {
            return;
        }
        if ((action == 0 && out.isEmpty()) || (action != 0 && action != 1)) {
            super.write(context, message, promise);
            return;
        }
        out.add(new Object[]{context, message, promise, System.currentTimeMillis(), id});
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        int id = counter.getAndIncrement();
        int action = onPacket(message, false, 0L, id);
        if (action == 2) {
            return;
        }
        if ((action == 0 && in.isEmpty()) || (action != 0 && action != 1)) {
            super.channelRead(context, message);
            return;
        }
        in.add(new Object[]{context, message, System.currentTimeMillis(), id});
    }

    @Override
    public void handlerAdded(ChannelHandlerContext context) throws Exception {
        pollTask = context.executor().scheduleWithFixedDelay(this, 4L, 4L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
        pollTask.cancel(false);
        while (!in.isEmpty()) {
            Object[] entry = in.poll();
            context.fireChannelRead(entry[1]);
        }
        while (!out.isEmpty()) {
            Object[] entry = out.poll();
            context.write(entry[1], (ChannelPromise) entry[2]);
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        pollInbound(now);
        pollOutbound(now);
    }

    private void pollInbound(long now) {
        while (!in.isEmpty()) {
            Object[] entry = in.peek();
            Object packet = entry[1];
            long elapsed = now - (Long) entry[2];
            int id = (Integer) entry[3];
            switch (onPacket(packet, false, elapsed, id)) {
                case 1:
                    return;
                case 2:
                    in.removeFirst();
                    return;
                default:
                    ((ChannelHandlerContext) entry[0]).fireChannelRead(packet);
                    in.removeFirst();
                    break;
            }
        }
    }

    private void pollOutbound(long now) {
        ChannelHandlerContext flush = null;
        while (!out.isEmpty()) {
            Object[] entry = out.peek();
            Object packet = entry[1];
            long elapsed = now - (Long) entry[3];
            int id = (Integer) entry[4];
            int action = onPacket(packet, true, elapsed, id);
            if (action == 1) {
                break;
            }
            if (action == 2) {
                out.removeFirst();
                return;
            }
            ChannelHandlerContext context = (ChannelHandlerContext) entry[0];
            context.write(packet, (ChannelPromise) entry[2]);
            out.removeFirst();
            flush = context;
        }
        if (flush != null) {
            flush.flush();
        }
    }
}
