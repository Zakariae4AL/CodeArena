/**
 * Pushes live updates to browsers over WebSocket (STOMP): judging progress on
 * /topic/submissions/{id} and scoreboards on /topic/contests/{id}/scoreboard.
 */
@ApplicationModule(displayName = "Realtime")
package dev.codearena.realtime;

import org.springframework.modulith.ApplicationModule;
