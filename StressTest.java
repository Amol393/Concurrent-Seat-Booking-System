import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class StressTest {
    record Result(String strategy, int attempts, int successes, int seatsBooked, int doubleBookings, long ms) {
        String json() {
            return String.format("{\"strategy\":\"%s\",\"attempts\":%d,\"successes\":%d,\"seatsBooked\":%d,\"doubleBookings\":%d,\"ms\":%d}",
                    strategy, attempts, successes, seatsBooked, doubleBookings, ms);
        }
    }

    static Result run(Strategy s, int seats, int attempts, int threads) throws InterruptedException {
        BookingService svc = new BookingService(seats, "stress_seats", false);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(attempts);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            final int u = i;
            pool.submit(() -> {
                try {
                    start.await();
                    int seat = ThreadLocalRandom.current().nextInt(seats);
                    if (svc.book(s, seat, "u" + u)) ok.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally { done.countDown(); }
            });
        }
        long t0 = System.nanoTime();
        start.countDown();
        done.await();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        return new Result(s.label, attempts, ok.get(), svc.bookedSeats(), svc.doubleBookings(), ms);
    }

    static void printAll(int seats, int attempts, int threads) throws InterruptedException {
        System.out.printf("seats=%d attempts=%d threads=%d%n", seats, attempts, threads);
        System.out.printf("%-28s %10s %11s %15s %9s%n", "STRATEGY", "BOOKINGS", "SEATS USED", "DOUBLE BOOKINGS", "TIME (ms)");
        for (Strategy s : Strategy.values()) {
            Result r = run(s, seats, attempts, threads);
            System.out.printf("%-28s %10d %11d %15d %9d%n", r.strategy(), r.successes(), r.seatsBooked(), r.doubleBookings(), r.ms());
        }
    }
}