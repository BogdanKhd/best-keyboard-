# Mechanical Keyboard — NATURAL

This version intentionally avoids the "Quake 3" character.

Audio:
- one real Cherry MX Black / ABS recording from the supplied source;
- stereo is preserved;
- no pitch shifting;
- no artificial panning;
- no compression or soft-clipping;
- only a tiny ±3.5% volume variation;
- natural ~0.5 s sample tail;
- polyphonic mixing (up to 24 overlapping presses);
- key-repeat suppression;
- low-latency 256-frame audio buffer.

Run:
cd ~/Desktop/MechanicalKeyboardNatural
mvn clean compile
java -cp "target/classes:$HOME/.m2/repository/com/github/kwhat/jnativehook/2.2.2/jnativehook-2.2.2.jar" MechanicalKeyboardNatural
