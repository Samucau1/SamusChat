// Measures scheduling and packet timestamps only; never stores audio samples.
export const installAudioTiming = `(()=>{
  window.audioTiming={starts:0,gaps:0,maxGapMs:0,overlaps:0,maxArrivalMs:0,sequenceGaps:0};
  window.audioTimingEnd=0;window.audioTimingArrival=0;window.audioTimingSequence=-1;
  window.reverseAudioProbe={frames:0,rms:0};
  if(!window.audioProbeInstalled){
    const copy=AudioBuffer.prototype.copyToChannel;
    AudioBuffer.prototype.copyToChannel=function(samples,...args){if(this.sampleRate===48000){let sum=0;for(const v of samples)sum+=v*v;window.reverseAudioProbe.frames++;window.reverseAudioProbe.rms=Math.max(window.reverseAudioProbe.rms,Math.sqrt(sum/samples.length));}return copy.call(this,samples,...args);};
    window.audioProbeInstalled=true;
  }
  if(!window.timingPatched){
    const start=AudioBufferSourceNode.prototype.start;
    AudioBufferSourceNode.prototype.start=function(at,...args){
      if(this.buffer?.sampleRate===48000){const p=window.audioTiming,when=at??this.context.currentTime,gap=when-window.audioTimingEnd;
        if(window.audioTimingEnd&&gap>.005){p.gaps++;p.maxGapMs=Math.max(p.maxGapMs,gap*1000)}
        if(window.audioTimingEnd&&gap<-.005)p.overlaps++;
        window.audioTimingEnd=when+this.buffer.duration;p.starts++;
      }return start.call(this,at,...args);
    };
    const decode=AudioDecoder.prototype.decode;
    AudioDecoder.prototype.decode=function(chunk){const now=performance.now(),p=window.audioTiming;
      if(window.audioTimingArrival)p.maxArrivalMs=Math.max(p.maxArrivalMs,now-window.audioTimingArrival);
      window.audioTimingArrival=now;
      if(window.audioTimingSequence>=0&&chunk.timestamp-window.audioTimingSequence>25000)p.sequenceGaps++;
      window.audioTimingSequence=chunk.timestamp;return decode.call(this,chunk);
    };
    window.timingPatched=true;
  }return true;
})()`;
