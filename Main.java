import java.util.*;
import java.util.concurrent.*;

public class Main {
    static final int SEATS = 100;

    static void ok(String msg)   { System.out.println(msg); }
    static void err(String msg)  { System.out.println(msg); }
    static void info(String msg) { System.out.println(msg); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("stress")) { StressTest.printAll(200, 1000, 64); return; }

        BookingService svc;
        try {
            svc = new BookingService(SEATS, "seats", true);
        } catch (RuntimeException e) {
            err("Could not connect to the database (" + Db.describe() + ").");
            err("Reason: " + Db.explain(e));
            return;
        }
        Strategy strat = Strategy.PER_SEAT_LOCK;
        Scanner in = new Scanner(System.in);
        ok("Connected to database: " + Db.describe());

        while (true) {
            System.out.println("\n=========== SEAT BOOKING  (strategy: " + strat + ") ===========");
            System.out.println(" 1) Show seats          2) Book a seat        3) Cancel my booking");
            System.out.println(" 4) Change strategy     5) Race demo          6) Stress test");
            System.out.println(" 7) Reset all seats     0) Exit");
            String choice = ask(in, "Choose: ");
            if (choice == null) return;
            try {
                switch (choice) {
                    case "1" -> showSeats(svc);
                    case "2" -> {
                        showSeats(svc);
                        int id = seat(ask(in, "Seat (e.g. B7): "));
                        String user = name(ask(in, "Your name: "));
                        if (svc.book(strat, id, user)) ok("Booked seat " + label(id) + " for " + user + ".");
                        else err("Seat " + label(id) + " is already taken (by " + svc.ownerOf(id) + ").");
                    }
                    case "3" -> {
                        int id = seat(ask(in, "Seat to cancel (e.g. B7): "));
                        String user = name(ask(in, "Your name: "));
                        if (svc.cancel(id, user)) ok("Booking cancelled.");
                        else err("Nothing cancelled: that seat isn't booked under that name.");
                    }
                    case "4" -> {
                        Strategy[] all = Strategy.values();
                        for (int i = 0; i < all.length; i++)
                            System.out.println("  " + (i + 1) + ") " + all[i]);
                        int k = Integer.parseInt(ask(in, "Pick 1-" + all.length + ": ").trim());
                        if (k < 1 || k > all.length) throw new IllegalArgumentException("pick 1-" + all.length);
                        strat = all[k - 1];
                        ok("Strategy changed to " + strat);
                    }
                    case "5" -> race(svc, strat, 30);
                    case "6" -> StressTest.printAll(200, 1000, 64);
                    case "7" -> { svc.reset(); ok("All seats are free again."); }
                    case "0" -> { info("Bye!"); return; }
                    default -> err("Invalid choice.");
                }
            } catch (IllegalArgumentException e) {
                err("Invalid input: " + e.getMessage());
            }
        }
    }

    static String ask(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.hasNextLine() ? in.nextLine().trim() : null;
    }

    static void showSeats(BookingService svc) {
        System.out.println("\n" + svc.seatMap());
        long dbl = svc.doubleBookings();
        System.out.println("\n"
                + "booked=" + svc.bookedSeats() + "  "
                + "free=" + (svc.size() - svc.bookedSeats()) + "  "
                + "doubleBookings=" + dbl);
    }

    static int seat(String s) {
        if (s == null || s.isEmpty()) throw new IllegalArgumentException("empty seat");
        s = s.toUpperCase();
        if (Character.isDigit(s.charAt(0))) return Integer.parseInt(s);
        int row = s.charAt(0) - 'A', num = Integer.parseInt(s.substring(1));
        if (row < 0 || row >= 10 || num < 1 || num > 10) throw new IllegalArgumentException("seat must be A1..J10");
        return row * 10 + num - 1;
    }

    static String label(int id) { return "" + (char) ('A' + id / 10) + (id % 10 + 1); }

    static String name(String s) {
        String u = s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "");
        if (u.isEmpty()) throw new IllegalArgumentException("name required (letters/digits only)");
        return u;
    }

    static void race(BookingService svc, Strategy s, int users) throws Exception {
        svc.reset();
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            final String u = "u" + i;
            fs.add(pool.submit(() -> { go.await(); return svc.book(s, 0, u); }));
        }
        go.countDown();
        int wins = 0;
        for (Future<Boolean> f : fs) if (f.get()) wins++;
        pool.shutdown();
        String msg = users + " users raced for seat A1 using " + s + ": " + wins + " got it.";
        if (wins > 1) System.out.println(msg + "  >>> DOUBLE BOOKING! <<<");
        else ok(msg + "  Exactly one winner.");
    }
}