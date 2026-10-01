package com.chatapp.chatapp_backend.call;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class CallRulesTest {
    private CallSession call(String state) {
        var c=new CallSession();c.setCaller("a");c.setCallee("b");c.setState(state);
        c.setCreatedAt(Instant.EPOCH);c.setCallerSeen(Instant.EPOCH);c.setCalleeSeen(Instant.EPOCH);return c;
    }
    @Test void ringExpiresAfter45Seconds(){assertThat(CallRules.expired(call("RINGING"),Instant.EPOCH.plusSeconds(46))).isTrue();}
    @Test void activeRequiresBothHeartbeats(){var c=call("ACTIVE");c.setCallerSeen(Instant.EPOCH.plusSeconds(100));assertThat(CallRules.expired(c,Instant.EPOCH.plusSeconds(100))).isTrue();}
    @Test void terminalDoesNotExpireAgain(){assertThat(CallRules.expired(call("ENDED"),Instant.now())).isFalse();}
    @Test void onlyParticipantsCanRead(){assertThatThrownBy(()->CallRules.participant(call("RINGING"),"x")).isInstanceOf(SecurityException.class);}
    @Test void rejectsInvalidOrOversizedSdp(){
        assertThatThrownBy(()->CallRules.sdp("x","offer")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->CallRules.sdp("v=0\nm=audio"+"x".repeat(65536),"offer")).isInstanceOf(IllegalArgumentException.class);
    }
}
