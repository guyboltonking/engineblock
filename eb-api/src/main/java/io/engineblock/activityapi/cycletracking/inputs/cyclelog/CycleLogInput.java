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

package io.engineblock.activityapi.cycletracking.inputs.cyclelog;

import io.engineblock.activityapi.Activity;
import io.engineblock.activityapi.cycletracking.buffers.cycles.CycleSegment;
import io.engineblock.activityapi.cycletracking.buffers.cycles.CycleSegmentBuffer;
import io.engineblock.activityapi.cycletracking.buffers.results.CycleResult;
import io.engineblock.activityapi.cycletracking.buffers.results.CycleResultsSegment;
import io.engineblock.activityapi.cycletracking.buffers.results_rle.CycleResultsRLEBufferReadable;
import io.engineblock.activityapi.input.SegmentedInput;
import io.engineblock.util.SimpleConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

public class CycleLogInput implements SegmentedInput, AutoCloseable, Iterable<CycleResultsSegment> {
    private final static Logger logger = LoggerFactory.getLogger(CycleLogInput.class);
    private final Iterator<CycleResultsSegment> cycleResultSegmentIterator;
    private RandomAccessFile raf;
    private MappedByteBuffer mbb;
    private Iterator<CycleResult> segmentIter;

    public CycleLogInput(Activity activity) {
        SimpleConfig conf = new SimpleConfig(activity, "input");
        mbb = initMappedBuffer(conf.getString("file").orElse(activity.getAlias()) + ".cyclelog");
        cycleResultSegmentIterator = new CycleResultsRLEBufferReadable(mbb).iterator();
        segmentIter = cycleResultSegmentIterator.next().iterator();
    }

    public CycleLogInput(String filename) {
        File cycleFile = null;
        try {
            cycleFile = new File(filename);
            if (!cycleFile.exists()) {
                cycleFile = new File(cycleFile + ".cyclelog");
                if (!cycleFile.exists()) {
                    throw new RuntimeException("Cyclelog file does not exist:" + filename);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mbb = initMappedBuffer(cycleFile.getPath());
        cycleResultSegmentIterator = new CycleResultsRLEBufferReadable(mbb).iterator();
        segmentIter = cycleResultSegmentIterator.next().iterator();
    }

    @Override
    public synchronized CycleSegment getInputSegment(int segmentLength) {

        CycleSegmentBuffer csb = new CycleSegmentBuffer(segmentLength);

        while (csb.remaining() > 0) {

            while (!segmentIter.hasNext() && cycleResultSegmentIterator.hasNext()) {
                segmentIter = cycleResultSegmentIterator.next().iterator();
            }
            if (segmentIter.hasNext()) {
                csb.append(segmentIter.next().getCycle());
            } else {
                if (csb.remaining() == segmentLength) {
                    return null;
                } else {
                    break;
                }
            }
        }
        return csb.toReadable();
    }

//            // acquire a buffered interval result
//            if (currentBuffer == null) {
//                currentBuffer = CycleResultsRLEBufferReadable.forOneRleSpan(mbb);
//                if (currentBuffer == null) {
//                    // or return null if none are left
//                    return null;
//                } else {
//                    strider = new CycleResultStrider(currentBuffer.getCycleResultIterable().iterator());
//                }
//            }
//            CycleResultsSegment cycleResultsSegment = strider.getCycleResultsSegment(remaining);
//            if (cycleResultsSegment!=null) {
//                for (CycleResult cycleResult : cycleResultsSegment) {
//                    csb.append(cycleResult.getCycle());
//                }
//            }
//            // else try again, because there are apparently more RLESegments to read.
//
//            remaining = csb.remaining();

    private MappedByteBuffer initMappedBuffer(String filename) {
        File filepath = new File(filename);
        if (!filepath.exists()) {
            throw new RuntimeException("file path '" + filename + "' does not exist!");
        }
        try {
            raf = new RandomAccessFile(filepath, "r");
            mbb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return mbb;
    }

    @Override
    public void close() throws Exception {
        if (raf != null) {
            raf.close();
            mbb = null;
        }
    }

    @NotNull
    @Override
    public Iterator<CycleResultsSegment> iterator() {
        CycleResultsRLEBufferReadable cycleResultsSegments = new CycleResultsRLEBufferReadable(mbb.duplicate());
        return cycleResultsSegments.iterator();
    }

}