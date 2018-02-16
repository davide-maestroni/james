/*
 * Copyright 2018 Davide Maestroni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dm.jale.ext;

import java.io.Serializable;

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.Observer;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class InRangeDoubleObserver implements Observer<AsyncEvaluations<Double>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final double mEnd;

  private final double mIncrement;

  private final boolean mIsInclusive;

  private final double mStart;

  InRangeDoubleObserver(final double start, final double end, final double increment,
      final boolean isInclusive) {
    mStart = start;
    mEnd = end;
    mIncrement = increment;
    mIsInclusive = isInclusive;
  }

  public void accept(final AsyncEvaluations<Double> evaluations) throws Exception {
    double value = mStart;
    @SuppressWarnings("UnnecessaryLocalVariable") final double end = mEnd;
    @SuppressWarnings("UnnecessaryLocalVariable") final double increment = mIncrement;
    if (mIsInclusive) {
      while (value < end) {
        evaluations.addValue(value);
        value += increment;
      }

    } else {
      while (value <= end) {
        evaluations.addValue(value);
        value += increment;
      }
    }

    evaluations.set();
  }
}
