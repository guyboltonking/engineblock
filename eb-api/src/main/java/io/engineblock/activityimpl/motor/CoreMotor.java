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
package io.engineblock.activityimpl.motor;

import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.RateLimiter;
import io.engineblock.activityapi.*;
import io.engineblock.activityapi.cycletracking.markers.Marker;
import io.engineblock.activityimpl.ActivityDef;
import io.engineblock.activityimpl.SlotStateTracker;
import io.engineblock.metrics.ActivityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.engineblock.activityapi.RunState.*;

/**
 * <p>ActivityMotor is a Runnable which runs in one of an activity's many threads.
 * It is the iteration harness for individual cycles of an activity. Each ActivityMotor
 * instance is responsible for taking input from a LongSupplier and applying
 * the provided LongConsumer to it on each cycle. These two parameters are called
 * input and action, respectively.
 * </p>
 */
public class CoreMotor implements ActivityDefObserver, Motor, Stoppable {

    private static final Logger logger = LoggerFactory.getLogger(CoreMotor.class);
    private long slotId;
    private Input input;
    private Action action;
    private ActivityDef activityDef;
    private SlotStateTracker slotStateTracker;
    private AtomicReference<RunState> slotState;
    private RateLimiter rateLimiter; // Only for use in phasing
    private int stride = 1;
    private Marker marker;

    /**
     * Create an ActivityMotor.
     *
     * @param activityDef The activity def that this motor will be associated with.
     * @param slotId      The enumeration of the motor, as assigned by its executor.
     * @param input       A LongSupplier which provides the cycle number inputs.
     */
    public CoreMotor(
            ActivityDef activityDef,
            long slotId,
            Input input) {
        this.activityDef = activityDef;
        this.slotId = slotId;
        setInput(input);
        slotStateTracker = new SlotStateTracker(slotId);
        slotState = slotStateTracker.getAtomicSlotState();
        onActivityDefUpdate(activityDef);
    }


    /**
     * Create an ActivityMotor.
     *
     * @param activityDef The activity def that this motor is based on.
     * @param slotId      The enumeration of the motor, as assigned by its executor.
     * @param input       A LongSupplier which provides the cycle number inputs.
     * @param action      An LongConsumer which is applied to the input for each cycle.
     */
    public CoreMotor(
            ActivityDef activityDef,
            long slotId,
            Input input,
            Action action
    ) {
        this(activityDef, slotId, input);
        setAction(action);
    }
    /**
     * Create an ActivityMotor.
     *
     * @param activityDef The activity def that this motor is based on.
     * @param slotId      The enumeration of the motor, as assigned by its executor.
     * @param input       A LongSupplier which provides the cycle number inputs.
     * @param action      An LongConsumer which is applied to the input for each cycle.
     * @param marker     An optional tracker.
     */
    public CoreMotor(
            ActivityDef activityDef,
            long slotId,
            Input input,
            Action action,
            Marker marker
    ) {
        this(activityDef, slotId, input);
        setAction(action);
        setResultSink(marker);
    }

    /**
     * Set the input for this ActivityMotor.
     *
     * @param input The LongSupplier that provides the cycle number.
     * @return this ActivityMotor, for chaining
     */
    @Override
    public Motor setInput(Input input) {
        this.input = input;
        return this;
    }

    @Override
    public Input getInput() {
        return input;
    }


    /**
     * Set the action for this ActivityMotor.
     *
     * @param action The LongConsumer that will be applied to the next cycle number.
     * @return this ActivityMotor, for chaining
     */
    @Override
    public Motor setAction(Action action) {
        this.action = action;
        return this;
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public long getSlotId() {
        return this.slotId;
    }

    @Override
    public SlotStateTracker getSlotStateTracker() {
        return slotStateTracker;
    }

    @Override
    public Motor setResultSink(Marker marker) {
        this.marker = marker;
        return this;
    }

    @Override
    public Marker getResultSink() {
        return this.marker;
    }


    @Override
    public void run() {

        try {
            Timer cyclesTimer = ActivityMetrics.timer(activityDef, "cycles");
            Timer phasesTimer = ActivityMetrics.timer(activityDef, "phases");
            Timer stridesTimer = ActivityMetrics.timer(activityDef, "strides");

            if (slotState.get() == Finished) {
                logger.warn("Input was already exhausted for slot " + slotId + ", remaining in finished state.");
            }

            slotStateTracker.enterState(Running);

            MultiPhaseAction multiPhaseAction = null;
            if (action instanceof MultiPhaseAction) {
                multiPhaseAction = ((MultiPhaseAction) action);
            }

            long cyclenum;
            AtomicLong cycleMax = input.getMaxCycle();

            action.init();

            while (slotState.get() == Running) {

                long thisIntervalStart = input.getCycleInterval(stride);
                long nextIntervalStart = thisIntervalStart + stride;

                if (action instanceof StrideAware) {
                    ((StrideAware) action).setInterval(thisIntervalStart, stride);
                }

                if (thisIntervalStart >= cycleMax.get()) {
                    logger.trace("input exhausted (input " + thisIntervalStart + "), stopping motor thread " + slotId);
                    slotStateTracker.enterState(Finished);
                    continue;
                }

                try (Timer.Context stridesTime = stridesTimer.time()) {

                    for (cyclenum = thisIntervalStart; cyclenum < nextIntervalStart; cyclenum++) {

                        if (slotState.get() != Running) {
                            logger.trace("motor stopped after input (input " + cyclenum + "), stopping motor thread " + slotId);
                            continue;
                        }
                        int result = -1;
                        try (Timer.Context cycleTime = cyclesTimer.time()) {

                            //logger.trace("cycle " + cyclenum);
                            try (Timer.Context phaseTime = phasesTimer.time()) {
                                result = action.runCycle(cyclenum);
                            }

                            if (multiPhaseAction != null) {
                                while (multiPhaseAction.incomplete()) {
                                    if (rateLimiter != null) {
                                        rateLimiter.acquire();
                                    }
                                    try (Timer.Context phaseTime = phasesTimer.time()) {
                                        result = multiPhaseAction.runPhase(cyclenum);
                                    }
                                }
                            }

                            try {
                                if (marker != null) {
                                    marker.onCycleResult(cyclenum, result);
                                }
                            } catch (Exception t) {
                                throw t;
                            }
                        }
                    }
                }

            }

            //MetricsContext.getInstance().getMetrics().getTimers().get("foo").getMeanRate();
            if (slotState.get() == Stopping) {
                slotStateTracker.enterState(Stopped);
            }
        } catch (Throwable t) {
            throw t;
        }
    }


    @Override
    public String toString() {
        return "slot:" + this.slotId + "; state:" + slotState.get();
    }

    @Override
    public void onActivityDefUpdate(ActivityDef activityDef) {
        if (input instanceof ActivityDefObserver) {
            ((ActivityDefObserver) input).onActivityDefUpdate(activityDef);
        }
        if (action instanceof ActivityDefObserver) {
            ((ActivityDefObserver) action).onActivityDefUpdate(activityDef);
        }

        if (input instanceof RateLimiterProvider) {
            rateLimiter = ((RateLimiterProvider) input).getRateLimiter();
        } else {
            rateLimiter = null;
        }

        this.stride = activityDef.getParams().getOptionalInteger("stride").orElse(1);
    }

    @Override
    public synchronized void requestStop() {
        if (slotState.get() == Running) {
            if (input instanceof Stoppable) {
                ((Stoppable) input).requestStop();
            }
            if (action instanceof Stoppable) {
                ((Stoppable) action).requestStop();
            }
            slotStateTracker.enterState(RunState.Stopping);
        } else {
            if (slotState.get()!=Stopped && slotState.get() !=Stopping) {
                logger.warn("attempted to stop motor " + this.getSlotId() + ": from non Running state:" + slotState.get());
            }
        }
    }

}
