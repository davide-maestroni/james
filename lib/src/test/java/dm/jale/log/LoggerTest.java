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

package dm.jale.log;

import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
public class LoggerTest {

  @Test
  public void test() {
    MyFormatter formatter = new MyFormatter();
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(formatter);
    final java.util.logging.Logger loggerr = java.util.logging.Logger.getLogger("");
    //    loggerr.removeHandler(loggerr.getHandlers()[0]);
    //    loggerr.addHandler(handler);
    loggerr.getHandlers()[0].setLevel(Level.FINEST);
    loggerr.setLevel(Level.FINEST);
    final Logger logger = Logger.newLogger(this);
    //java.util.logging.Logger.getLogger(LoggerTest.class.getName()).setLevel(Level.FINEST);
    logger.dbg("Test DEBUG");
    logger.wrn("Test WARNING");
    logger.err("Test ERROR");
  }

  private class MyFormatter extends SimpleFormatter {

    @Override
    public synchronized String format(final LogRecord record) {
      return "[" + ManagementFactory.getThreadMXBean()
                                    .getThreadInfo(record.getThreadID())
                                    .getThreadName() + "] " + super.format(record);
    }
  }
}
