package yt.ren.slate.net;

import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;

public class PacketDispatch {
    public static int dispatch(Object packet, boolean outbound, long elapsed, int id) {
        ClientRuntime runtime = ClientRuntime.get();
        runtime.acquire();
        int action = 0;
        try {
            if (runtime.bypass == 0) {
                PacketEvent event = new PacketEvent(outbound, packet, elapsed, id);
                runtime.registry.post(outbound ? EventTypes.PACKET_SEND : EventTypes.PACKET_RECEIVE, event);
                action = event.action;
            }
        } finally {
            runtime.release();
        }
        return action;
    }
}
