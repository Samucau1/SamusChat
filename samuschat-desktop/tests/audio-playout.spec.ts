import { test, expect } from '@playwright/test';
import { planAudioChunk } from '../src/models/audioPlayout';

test('jitter in 20ms packets does not create gaps or overlap in the playback clock', () => {
  const arrivals = [0, .018, .052, .053, .095, .096, .14, .145];
  let next: number | undefined;
  const starts = arrivals.map(now => { const at = planAudioChunk(now, next, .02); if (at !== null) next = at + .02; return at; });
  starts.forEach((at, i) => expect(at).toBeCloseTo(.24 + .02 * i, 6));
});

test('a burst is bounded and never resets the clock on top of queued audio', () => {
  let next: number | undefined;
  const starts: number[] = [];
  for (let i=0; i<20; i++) { const at=planAudioChunk(0,next,.02); if(at!==null){starts.push(at);next=at+.02;} }
  expect(starts.length).toBe(8);
  expect(next).toBeCloseTo(.4,6);
  starts.slice(1).forEach((at,i)=>expect(at).toBeCloseTo(starts[i]+.02,6));
  expect(planAudioChunk(.1,next,.02)).toBeCloseTo(.4,6);
});

test('after a real underrun playback restarts with bounded headroom', () => {
  expect(planAudioChunk(1,.5,.02)).toBeCloseTo(1.24,6);
  expect(planAudioChunk(1,undefined,NaN)).toBeNull();
});

test('a chunk arriving just before its deadline does not trigger an artificial rebuffer', () => {
  expect(planAudioChunk(1,1.003,.02)).toBeCloseTo(1.003,6);
});
