package de.eve.mobile;

/** Recovery observes an existing backend turn; it must never submit the input again. */
final class TurnRecovery {
  static boolean canResume(String id) {
    return id != null && !id.isEmpty() && !id.equals("sending");
  }

  static String streamPath(String id) {
    if (!canResume(id)) throw new IllegalArgumentException("Kein bestätigter Auftrag vorhanden");
    return "/api/message/stream/" + EveApi.path(id);
  }
}
