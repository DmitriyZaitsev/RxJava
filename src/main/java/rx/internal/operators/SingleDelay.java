/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rx.internal.operators;

import java.util.concurrent.TimeUnit;

import rx.*;
import rx.Scheduler.Worker;
import rx.Single.OnSubscribe;
import rx.functions.Action0;

/**
 * Signal the success or error value on the Scheduler's thread.
 *
 * @param <T> the value type
 */
public final class SingleDelay<T> implements Single.OnSubscribe<T> {

    final Single.OnSubscribe<T> source;

    final long delay;

    final TimeUnit unit;

    final Scheduler scheduler;

    public SingleDelay(OnSubscribe<T> source, long delay, TimeUnit unit, Scheduler scheduler) {
        this.source = source;
        this.scheduler = scheduler;
        this.delay = delay;
        this.unit = unit;
    }

    @Override
    public void call(SingleSubscriber<? super T> t) {
        Worker w = scheduler.createWorker();

        ObserveOnSingleSubscriber<T> parent = new ObserveOnSingleSubscriber<T>(t, w, delay, unit);

        t.add(w);
        t.add(parent);

        source.call(parent);
    }

    static final class ObserveOnSingleSubscriber<T> extends SingleSubscriber<T>
    implements Action0 {
        final SingleSubscriber<? super T> actual;

        final Worker w;

        final long delay;

        final TimeUnit unit;

        T value;
        Throwable error;

        public ObserveOnSingleSubscriber(SingleSubscriber<? super T> actual, Worker w, long delay, TimeUnit unit) {
            this.actual = actual;
            this.w = w;
            this.delay = delay;
            this.unit = unit;
        }

        @Override
        public void onSuccess(T value) {
            this.value = value;
            w.schedule(this, delay, unit);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            w.schedule(this, delay, unit);
        }

        @Override
        public void call() {
            try {
                Throwable ex = error;
                if (ex != null) {
                    error = null;
                    actual.onError(ex);
                } else {
                    T v = value;
                    value = null;
                    actual.onSuccess(v);
                }
            } finally {
                w.unsubscribe();
            }
        }
    }
}
