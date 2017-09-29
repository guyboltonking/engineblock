/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.engineblock.activityapi.cycletracking.buffers;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * This is just a typed-data holder for efficient transfer of tracked data.
 * It holds a base cycle value, and a byte buffer view of cycle values.
 */
public class CycleSegment implements Iterable<CycleResult> {

    /**
     * The base cycle value, the minimum cycle in the segment.
     */
    public long cycle;

    /**
     * A view of status codes in byte form.
     */
    public byte[] codes;

    public static CycleSegment forData(long cycle, byte[] buffer, int offset, int len) {
        CycleSegment s = new CycleSegment();
        s.cycle = cycle;
        s.codes = Arrays.copyOfRange(buffer,offset,offset+len);
        return s;
    }

    public static CycleSegment forData(long cycle, byte[] buffer) {
        CycleSegment s = new CycleSegment();
        s.cycle=cycle;
        s.codes = buffer;
        return s;
    }

    @NotNull
    @Override
    public Iterator<CycleResult> iterator() {
        return new CycleSegmentIterator();
    }

    private class CycleSegmentIterator implements Iterator<CycleResult> {

        private int index=0;

        @Override
        public boolean hasNext() {
            return (index<codes.length);
        }

        @Override
        public CycleResult next() {
            CycleSegmentResult cycleSegmentResult = new CycleSegmentResult(cycle + index, codes[index]);
            index++;
            return cycleSegmentResult;
        }
    }

    @Override
    public String toString() {
        return "CycleSegment{" +
                "cycle=" + cycle +
                ", codes=" +
                ( codes.length<100 ? Arrays.toString(codes) : Arrays.toString(Arrays.copyOfRange(codes,0,100))) +
                '}';
    }

    private class CycleSegmentResult implements CycleResult {
        private final long cycle;
        private final int result;

        public CycleSegmentResult(long cycle, int result) {
            this.cycle = cycle;
            this.result = result;
        }

        @Override
        public long getCycle() {
            return cycle;
        }

        @Override
        public int getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "CycleSegmentResult{" +
                    "cycle=" + cycle +
                    ", result=" + result +
                    '}';
        }
    }

}
