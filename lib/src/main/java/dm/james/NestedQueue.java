/*
 * Copyright 2017 Davide Maestroni
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

package dm.james;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import dm.james.util.DoubleQueue;

/**
 * Implementation of a nested queue ensuring that data are returned in the same order as they are
 * added, even if added later through a nested queue.
 * <p>
 * Created by davide-maestroni on 09/30/2014.
 *
 * @param <E> the element data type.
 */
class NestedQueue<E> {

  private boolean mClosed;

  private DoubleQueue<Object> mQueue = new DoubleQueue<Object>();

  private QueueManager<E> mQueueManager;

  /**
   * Constructor.
   */
  NestedQueue() {
    mQueueManager = new SimpleQueueManager();
  }

  /**
   * Check if the specified internal queue can be pruned.
   *
   * @param queue the queue.
   * @return whether the queue can be pruned.
   */
  private static boolean canPrune(@NotNull final NestedQueue<?> queue) {
    return queue.mClosed && queue.mQueue.isEmpty();
  }

  /**
   * Adds the specified element to the queue.
   * <p>
   * Note that the element can be null.
   *
   * @param element the element to add.
   * @throws IllegalStateException if the queue has been already closed.
   */
  void add(@Nullable final E element) {
    mQueue.add(element);
  }

  /**
   * Adds all the elements in the specified collection.
   * <p>
   * Note that null elements are supported as well.
   *
   * @param elements the collection of elements to add.
   * @throws IllegalStateException if the queue has been already closed.
   */
  void addAll(@NotNull final Collection<? extends E> elements) {
    mQueue.addAll(elements);
  }

  /**
   * Adds a nested queue to this one.
   *
   * @return the newly added nested queue.
   * @throws IllegalStateException if the queue has been already closed.
   */
  @NotNull
  NestedQueue<E> addNested() {
    final InnerNestedQueue<E> queue = new InnerNestedQueue<E>();
    mQueue.add(queue);
    mQueueManager = new NestedQueueManager();
    return queue;
  }

  /**
   * Clears the queue.
   */
  void clear() {
    mQueue.clear();
    mQueueManager = new SimpleQueueManager();
  }

  /**
   * Closes this queue.
   * <br>
   * After the method returns no further additions can be made to this queue. Though, elements can
   * be safely removed.
   */
  void close() {
    mClosed = true;
    mQueue = new ReadOnlyQueue<Object>(mQueue);
  }

  // TODO: 29/07/2017 javadoc
  boolean isClosed() {
    return mQueueManager.isClosed();
  }

  /**
   * Check if the queue does not contain any element.
   *
   * @return whether the queue is empty.
   */
  boolean isEmpty() {
    return mQueueManager.isEmpty();
  }

  /**
   * Removes the first element added into the queue.
   *
   * @return the element.
   * @throws java.util.NoSuchElementException if the queue is empty.
   */
  E removeFirst() {
    return mQueueManager.removeFirst();
  }

  /**
   * Removes all the elements from this queue and add them to the specified collection.
   *
   * @param collection the collection to fill.
   */
  void transferTo(@NotNull final Collection<? super E> collection) {
    mQueueManager.transferTo(collection);
  }

  /**
   * Removes all the elements from this queue and put them into the specified array, starting from
   * {@code dstPos} position.
   * <br>
   * If the array is bigger than the required length, the remaining elements will stay untouched,
   * and the number of transferred data will be returned.
   * <br>
   * On the contrary, if the array is not big enough to contain all the data, only the fitting
   * number of elements will be transferred, and a negative number, whose absolute value
   * represents
   * the number of data still remaining in the queue, will be returned.
   * <br>
   * If the queue is empty, {@code 0} will be returned.
   *
   * @param dst     the destination array.
   * @param destPos the destination position in the array.
   * @param <T>     the array component type.
   * @return the number of transferred elements or the negated number of elements still remaining
   * in the queue.
   */
  <T> int transferTo(@NotNull final T[] dst, final int destPos) {
    return mQueueManager.transferTo(dst, destPos);
  }

  /**
   * Interface describing a manager of the internal queue.
   *
   * @param <E> the element data type.
   */
  private interface QueueManager<E> {

    // TODO: 30/07/2017 javadoc
    boolean isClosed();

    /**
     * Check if the queue does not contain any element.
     *
     * @return whether the queue is empty.
     */
    boolean isEmpty();

    /**
     * Removes the first element added into the queue.
     *
     * @return the element.
     * @throws java.util.NoSuchElementException if the queue is empty.
     */
    E removeFirst();

    /**
     * Removes all the elements from this queue and add them to the specified collection.
     *
     * @param collection the collection to fill.
     */
    void transferTo(@NotNull Collection<? super E> collection);

    /**
     * Removes all the elements from this queue and put them into the specified array, starting from
     * {@code dstPos} position.
     * <br>
     * If the array is bigger than the required length, the remaining elements will stay untouched,
     * and the number of transferred data will be returned.
     * <br>
     * On the contrary, if the array is not big enough to contain all the data, only the fitting
     * number of elements will be transferred, and a negative number, whose absolute value
     * represents
     * the number of data still remaining in the queue, will be returned.
     * <br>
     * If the queue is empty, {@code 0} will be returned.
     *
     * @param dst     the destination array.
     * @param destPos the destination position in the array.
     * @param <T>     the array component type.
     * @return the number of transferred elements or the negated number of elements still remaining
     * in the queue.
     */
    <T> int transferTo(@NotNull T[] dst, int destPos);
  }

  /**
   * Internal class used to discriminate between an element and a nested queue.
   *
   * @param <E> the element data type.
   */
  private static class InnerNestedQueue<E> extends NestedQueue<E> {}

  /**
   * Read only queue implementation.
   *
   * @param <E> the element type.
   */
  private static class ReadOnlyQueue<E> extends DoubleQueue<E> {

    private final DoubleQueue<E> mQueue;

    /**
     * Constructor.
     *
     * @param queue the wrapped queue.
     */
    private ReadOnlyQueue(@NotNull final DoubleQueue<E> queue) {
      mQueue = queue;
    }

    @Override
    public boolean addAll(final Collection<? extends E> collection) {
      throw exception();
    }

    @Override
    public boolean isEmpty() {
      return mQueue.isEmpty();
    }

    @Override
    public boolean add(@Nullable final E element) {
      throw exception();
    }

    @Override
    public void clear() {
      mQueue.clear();
    }

    @Override
    public E peekFirst() {
      return mQueue.peekFirst();
    }

    @Override
    public E removeFirst() {
      return mQueue.removeFirst();
    }

    @Override
    public <T> int transferTo(@NotNull final T[] dst, final int destPos) {
      return mQueue.transferTo(dst, destPos);
    }

    @Override
    public void transferTo(@NotNull final Collection<? super E> collection) {
      mQueue.transferTo(collection);
    }

    @NotNull
    private IllegalStateException exception() {
      return new IllegalStateException("the queue is closed");
    }
  }

  /**
   * Nested queue manager implementation.
   */
  private class NestedQueueManager implements QueueManager<E> {

    public boolean isClosed() {
      if (!mClosed) {
        return false;
      }

      final DoubleQueue<Object> queue = mQueue;
      for (final Object element : queue) {
        if (element instanceof InnerNestedQueue) {
          if (!((InnerNestedQueue) element).isClosed()) {
            return false;
          }
        }
      }

      return true;
    }

    @SuppressWarnings("unchecked")
    public boolean isEmpty() {
      final DoubleQueue<Object> queue = mQueue;
      while (!queue.isEmpty()) {
        final Object element = queue.peekFirst();
        if (element instanceof InnerNestedQueue) {
          final NestedQueue<E> nested = (NestedQueue<E>) element;
          final boolean isEmpty = nested.isEmpty();
          if (canPrune(nested)) {
            queue.removeFirst();
            continue;
          }

          return isEmpty;
        }

        return false;
      }

      return true;
    }

    @SuppressWarnings("unchecked")
    public E removeFirst() {
      final DoubleQueue<Object> queue = mQueue;
      while (true) {
        final Object element = queue.peekFirst();
        if (element instanceof InnerNestedQueue) {
          final NestedQueue<E> nested = (NestedQueue<E>) element;
          if (canPrune(nested)) {
            queue.removeFirst();
            continue;
          }

          final E e = nested.removeFirst();
          if (canPrune(nested)) {
            queue.removeFirst();
          }

          return e;
        }

        return (E) queue.removeFirst();
      }
    }

    @SuppressWarnings("unchecked")
    public void transferTo(@NotNull final Collection<? super E> collection) {
      final DoubleQueue<Object> queue = mQueue;
      while (!queue.isEmpty()) {
        final Object element = queue.peekFirst();
        if (element instanceof InnerNestedQueue) {
          final NestedQueue<E> nested = (NestedQueue<E>) element;
          nested.transferTo(collection);
          if (canPrune(nested)) {
            queue.removeFirst();
            continue;
          }

          return;

        } else {
          collection.add((E) queue.removeFirst());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public <T> int transferTo(@NotNull final T[] dst, final int destPos) {
      int i = destPos;
      int result = 0;
      final int length = dst.length;
      final DoubleQueue<Object> queue = mQueue;
      while (!queue.isEmpty()) {
        final Object element = queue.peekFirst();
        if (element instanceof InnerNestedQueue) {
          final NestedQueue<E> nested = (NestedQueue<E>) element;
          final int n = nested.transferTo(dst, i);
          if (n < 0) {
            return n;
          }

          i += n;
          result += n;
          if (canPrune(nested)) {
            queue.removeFirst();
            continue;
          }

          return result;

        } else if (i < length) {
          dst[i++] = (T) queue.removeFirst();
          ++result;

        } else {
          return -1;
        }
      }

      return result;
    }
  }

  /**
   * Simple queue manager implementation.
   */
  private class SimpleQueueManager implements QueueManager<E> {

    public boolean isClosed() {
      return mClosed;
    }

    public boolean isEmpty() {
      return mQueue.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public E removeFirst() {
      return (E) mQueue.removeFirst();
    }

    @SuppressWarnings("unchecked")
    public void transferTo(@NotNull final Collection<? super E> collection) {
      ((DoubleQueue<E>) mQueue).transferTo(collection);
    }

    public <T> int transferTo(@NotNull final T[] dst, final int destPos) {
      return mQueue.transferTo(dst, destPos);
    }
  }
}
