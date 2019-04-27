package com.lyc.downloader.utils;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
/**
 * @author liuyuchuan
 * @date 2019-04-26
 * @email kevinliu.sir@qq.com
 */
public class UniqueDequeue<E> implements Deque<E> {
    private final Deque<E> realDeque;
    private final Set<E> set;
    public UniqueDequeue(Deque<E> realDeque, Set<E> set) {
        this.realDeque = realDeque;
        this.set = set;
        Queue<E> queue = new ArrayDeque<>();
        while (!realDeque.isEmpty()) {
            E e = realDeque.pollFirst();
            if (set.add(e)) {
                queue.offer(e);
            }
        }
        realDeque.clear();
        realDeque.addAll(queue);
    }
    public UniqueDequeue(Deque<E> realDeque) {
        this(realDeque, new HashSet<>());
    }
    public UniqueDequeue() {
        this(new ArrayDeque<>(), new HashSet<>());
    }
    @Override
    public void addFirst(E e) {
        if (set.add(e)) {
            realDeque.add(e);
        }
    }
    @Override
    public void addLast(E e) {
        if (set.add(e)) {
            realDeque.addLast(e);
        }
    }
    @Override
    public boolean offerFirst(E e) {
        return set.add(e) && realDeque.offerFirst(e);
    }
    @Override
    public boolean offerLast(E e) {
        return set.add(e) && realDeque.offerLast(e);
    }
    @Override
    public E removeFirst() {
        E e = realDeque.removeFirst();
        set.remove(e);
        return e;
    }
    @Override
    public E removeLast() {
        E e = realDeque.removeLast();
        set.remove(e);
        return e;
    }
    @Override
    public E pollFirst() {
        E e = realDeque.pollFirst();
        set.remove(e);
        return e;
    }
    @Override
    public E pollLast() {
        E e = realDeque.pollLast();
        set.remove(e);
        return e;
    }
    @Override
    public E getFirst() {
        return realDeque.getFirst();
    }
    @Override
    public E getLast() {
        return realDeque.getLast();
    }
    @Override
    public E peekFirst() {
        return realDeque.peekFirst();
    }
    @Override
    public E peekLast() {
        return realDeque.peekLast();
    }
    @Override
    public boolean removeFirstOccurrence(Object o) {
        return set.remove(o) && realDeque.removeFirstOccurrence(o);
    }
    @Override
    public boolean removeLastOccurrence(Object o) {
        return set.remove(o) && realDeque.removeLastOccurrence(o);
    }
    @Override
    public boolean add(E e) {
        return set.add(e) && realDeque.add(e);
    }
    @Override
    public boolean offer(E e) {
        return set.add(e) && realDeque.offer(e);
    }
    @Override
    public E remove() {
        E remove = realDeque.remove();
        set.remove(remove);
        return remove;
    }
    @Override
    public E poll() {
        E poll = realDeque.poll();
        set.remove(poll);
        return poll;
    }
    @Override
    public E element() {
        return realDeque.element();
    }
    @Override
    public E peek() {
        return realDeque.peek();
    }
    @Override
    public void push(E e) {
        if (set.add(e)) {
            realDeque.push(e);
        }
    }
    @Override
    public E pop() {
        E pop = realDeque.pop();
        set.remove(pop);
        return pop;
    }
    @Override
    public boolean remove(Object o) {
        return set.remove(o) && realDeque.remove(o);
    }
    @Override
    public boolean containsAll(Collection<?> c) {
        return set.containsAll(c);
    }
    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean add = false;
        for (E e : c) {
            if (set.add(e)) {
                add = true;
                realDeque.add(e);
            }
        }
        return add;
    }
    @Override
    public boolean removeAll(Collection<?> c) {
        boolean remove = false;
        for (Object o : c) {
            if (set.remove(o)) {
                remove = true;
                realDeque.remove(o);
            }
        }
        return remove;
    }
    @Override
    public boolean retainAll(Collection<?> c) {
        boolean change = set.retainAll(c);
        if (change) {
            Iterator<E> iterator = realDeque.iterator();
            while (iterator.hasNext()) {
                if (!set.contains(iterator.next())) {
                    iterator.remove();
                }
            }
        }
        return change;
    }
    @Override
    public void clear() {
        set.clear();
        realDeque.clear();
    }
    @Override
    public boolean contains(Object o) {
        return set.contains(o);
    }
    @Override
    public int size() {
        return set.size();
    }
    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }
    @Override
    public Iterator<E> iterator() {
        return realDeque.iterator();
    }
    @Override
    public Object[] toArray() {
        return realDeque.toArray();
    }
    @Override
    public <T> T[] toArray(T[] a) {
        return realDeque.toArray(a);
    }
    @Override
    public Iterator<E> descendingIterator() {
        return realDeque.descendingIterator();
    }
}
