package com.chatapp.chatapp_backend.call;

import java.time.*;

public final class CallRules {
    private CallRules() {}
    public static boolean expired(CallSession call, Instant now) {
        if (call.terminal()) return false;
        if (call.getState().equals("RINGING")) return now.isAfter(call.getCreatedAt().plusSeconds(45));
        if (call.getState().equals("CONNECTING") && now.isAfter(call.getCreatedAt().plusSeconds(105))) return true;
        return now.isAfter(call.getCallerSeen().plusSeconds(45)) || now.isAfter(call.getCalleeSeen().plusSeconds(45));
    }
    public static void participant(CallSession call, String email) {
        if (!email.equals(call.getCaller()) && !email.equals(call.getCallee())) throw new SecurityException("Chamada privada");
    }
    public static void sdp(String sdp, String kind) {
        if (sdp==null || sdp.length()>65536 || !sdp.startsWith("v=0") || !sdp.contains("m=audio"))
            throw new IllegalArgumentException("SDP " + kind + " invalido");
    }
}
