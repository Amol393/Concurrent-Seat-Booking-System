# Ticket Booking - Terminal + MySQL (Java 17+)

Console app, no web frontend. Seats are stored in a MySQL database through JDBC.

## One-time setup
1. Install MySQL Server and make sure it is running (default port 3306).
2. Copy the MySQL connector jar (mysql-connector-j-8.x.jar) into the  lib/  folder.
3. Open  db.properties  and set your MySQL user and password (and port if it is not 3306).
   The database "booking" and the table are created automatically.

## Run
    run.bat            (Windows)   |   ./run.sh   (Mac/Linux)
    run.bat stress                 -> benchmark of all 4 strategies

The first line shows what it connected to, e.g.
    Connected to database: MySQL at localhost, port 3306, database 'booking', user 'root'

## Files
- Main.java            terminal menu (show / book / cancel / strategy / race / stress / reset)
- BookingService.java  booking logic: no lock, one global lock, one lock per seat, atomic SQL (CAS)
- Db.java              JDBC connection (reads db.properties)
- StressTest.java      multi-threaded benchmark (separate table stress_seats)
- db.properties        host, port, database, user, password
- lib/                 JDBC driver jars

## No MySQL?  Use SQLite instead
In db.properties remove the # before the line  url=jdbc:sqlite:booking.db
(the SQLite driver jar is already in lib/).
