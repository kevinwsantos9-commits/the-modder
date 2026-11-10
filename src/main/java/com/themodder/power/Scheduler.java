package com.themodder.power;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/** Agendador simples (roda no tick do servidor) usado para efeitos em várias fases. */
public final class Scheduler {
    private static final class Task {
        int delay;
        final int duration;
        int idx = 0;
        final Runnable once;
        final IntConsumer rep;

        Task(int delay, int duration, Runnable once, IntConsumer rep) {
            this.delay = delay;
            this.duration = duration;
            this.once = once;
            this.rep = rep;
        }
    }

    private static final List<Task> tasks = new ArrayList<>();
    private static final List<Task> pending = new ArrayList<>();

    private Scheduler() {}

    public static void run(int delay, Runnable r) {
        pending.add(new Task(delay, 0, r, null));
    }

    public static void repeat(int delay, int duration, IntConsumer r) {
        pending.add(new Task(delay, duration, null, r));
    }

    public static void clear() {
        tasks.clear();
        pending.clear();
    }

    public static void tick() {
        if (!pending.isEmpty()) {
            tasks.addAll(pending);
            pending.clear();
        }
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.delay > 0) {
                t.delay--;
                continue;
            }
            try {
                if (t.rep != null) {
                    t.rep.accept(t.idx++);
                    if (t.idx >= t.duration) it.remove();
                } else {
                    t.once.run();
                    it.remove();
                }
            } catch (Throwable e) {
                e.printStackTrace();
                it.remove();
            }
        }
    }
}
