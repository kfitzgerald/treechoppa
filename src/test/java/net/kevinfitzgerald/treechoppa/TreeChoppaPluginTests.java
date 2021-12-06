package net.kevinfitzgerald.treechoppa;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TreeChoppaPluginTests {

  private ServerMock server;

  @BeforeEach
  public void setUp() {
    server = MockBukkit.mock();
    MockBukkit.load(TreeChoppaPlugin.class);
  }

  @Test
  public void shouldFirePlayerJoinEvent() {

    server.addPlayer();

    server.getPluginManager().assertEventFired(PlayerJoinEvent.class);
  }

  @AfterEach
  public void tearDown() {
    MockBukkit.unmock();
  }
}
