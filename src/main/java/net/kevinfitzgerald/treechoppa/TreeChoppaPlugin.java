package net.kevinfitzgerald.treechoppa;

import kr.entree.spigradle.annotations.PluginMain;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Tag;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

@PluginMain
public class TreeChoppaPlugin extends JavaPlugin implements Listener {

  FileConfiguration config = getConfig();

  private long decayDelay;
  private long searchDelay;
  private int logLimit;
  private boolean queueRunning = false;

  /**
   * Tools able to be used to fast-chop trees
   */
  private final List<Material> axes = Arrays.asList(
          Material.DIAMOND_AXE,
          Material.GOLDEN_AXE,
          Material.IRON_AXE,
          Material.STONE_AXE,
          Material.WOODEN_AXE,
          Material.NETHERITE_AXE
  );

  /**
   * Directions in which to iterate logs,
   */
  private final LinkedList<Vector> directions = new LinkedList<>(Arrays.asList(
          // Above touching face
          new Vector(0, 1, 0),

          // Above touching edge
          new Vector(1, 1, 0),
          new Vector(-1, 1, 0),
          new Vector(0, 1, 1),
          new Vector(0, 1, -1),

          // Above touching corner
          new Vector(1, 1, 1),
          new Vector(1, 1, -1),
          new Vector(-1, 1, 1),
          new Vector(-1, 1, -1),

          // Side touching face
          new Vector(1, 0, 0),
          new Vector(-1, 0, 0),
          new Vector(0, 0, 1),
          new Vector(0, 0, -1),

          // Side touching edge
          new Vector(1, 0, 1),
          new Vector(1, 0, -1),
          new Vector(-1, 0, 1),
          new Vector(-1, 0, -1)
  ));

  /**
   * Leaves that need to get deleted
   */
  private final HashSet<Block> scheduledBlocks = new HashSet<>();
  private static final List<BlockFace> neighbors = Arrays.asList(
          BlockFace.UP,
          BlockFace.NORTH,
          BlockFace.EAST,
          BlockFace.SOUTH,
          BlockFace.WEST,
          BlockFace.DOWN
  );

  public TreeChoppaPlugin() {

  }

  public TreeChoppaPlugin(
      JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
    super(loader, description, dataFolder, file);
  }

  @Override
  public void onEnable() {

    config.addDefault("decay_delay", 2L);
    config.addDefault("search_delay", 5L);
    config.addDefault("log_limit", 250);
    config.options().copyDefaults(true);
    saveConfig();

    this.logLimit = config.getInt("log_limit");
    this.decayDelay = config.getLong("decay_delay");
    this.searchDelay = config.getLong("search_delay");

    getServer().getPluginManager().registerEvents(this, this);
  }

  @Override
  public void onDisable() {
    // Clean up
    scheduledBlocks.clear();
  }

//  @EventHandler
//  public void onPlayerJoin(PlayerJoinEvent event) {
//    getLogger().info("Player joined.");
//  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    Block block = event.getBlock();

    // If the block is a log and player is in survival
    if (Tag.LOGS.isTagged(block.getType()) && player.getGameMode() == GameMode.SURVIVAL) {
      // Player is using a proper axe
      ItemStack tool = player.getInventory().getItemInMainHand();
      if (this.isMagicTool(tool)) {
        this.breakAllLogs(player, block, tool);
      }
    } else if (Tag.LEAVES.isTagged(block.getType()) && player.getGameMode() == GameMode.SURVIVAL) {
      // Player is using a proper axe
      ItemStack tool = player.getInventory().getItemInMainHand();
      if (this.isMagicTool(tool)) {
//        Leaves leaves = (Leaves) block.getBlockData();
//        getLogger().info("Leaf distance: " + leaves.getDistance());
        this.addAdjacentLeavesToQueue(block);
        processLeaves();
      }
    }
  }

  protected boolean isMagicTool(ItemStack tool) {
    return this.axes.contains(tool.getType()) && tool.getEnchantments().containsKey(Enchantment.VANISHING_CURSE);
  }

  protected LinkedList<Location> findAllLogs(Block originalBlock) {
    LinkedHashSet<Location> logQueue = new LinkedHashSet<>();
    LinkedList<Location> foundLogs = new LinkedList<>();
    boolean foundNaturalLeaf = false;

    logQueue.add(originalBlock.getLocation());

    while (!logQueue.isEmpty()) {

      // Check the block adjacent in all directions to the current block
      Location location = logQueue.iterator().next();
      logQueue.remove(location);

      for (Vector vector : directions) {

        // Get the block info
        Location candidateLocation = location.clone().add(vector);
        Block candidate = candidateLocation.getBlock();

        // If the block was not already visited, queue it
        if (originalBlock.getType() == candidate.getType() && !foundLogs.contains(candidateLocation)) {
          logQueue.add(candidateLocation);

        // Otherwise if the block is a leaf, flag it
        } else if (Tag.LEAVES.isTagged(candidate.getType()) && !((Leaves)candidate.getBlockData()).isPersistent()) {
          foundNaturalLeaf = true;
        }
      }

      // Queue the log for destruction
      foundLogs.push(location);
      if (foundLogs.size() > this.logLimit) { // safeguard
        getLogger().info("HIT LOG LIMIT");
        break;
      }
    }

//    if (foundNaturalLeaf) {
//      // TODO - delete leaves
//    }

    // Remove the original block
    foundLogs.remove(originalBlock.getLocation());

//    for (Location l : foundLogs) {
//      getLogger().info("LOG: " + l);
//    }

    if (!foundNaturalLeaf) {
//      getLogger().info("NO NATURAL LEAVES FOUND");
      return new LinkedList<>();
    } else {
      return foundLogs;
    }

  }

  protected void breakAllLogs(Player player, Block block, ItemStack tool) {
    LinkedList<Location> logs = this.findAllLogs(block);
    int logsBroken = 0;

    ItemMeta meta = tool.getItemMeta();
    Damageable toolDamage = ((Damageable) meta);


    for (Location log : logs) {
      // Check if the axe has broken and abort if so
      if (meta != null && toolDamage.getDamage() == 0) {
        break;
      }

      log.getBlock().breakNaturally(tool);

      getServer().getScheduler().runTaskLater(this, () -> this.addAdjacentLeavesToQueue(log.clone().getBlock()), this.searchDelay);

      logsBroken++;
    }

    if (meta != null) {
      getLogger().info(player.getDisplayName() + " broke "+logsBroken+" logs");
      toolDamage.setDamage(toolDamage.getDamage() + logsBroken);
    }

    tool.setItemMeta(toolDamage);
  }

  private void addAdjacentLeavesToQueue(Block targetBlock) {

//    getLogger().info("Looking for adjacent leaves, size is: " + scheduledBlocks.size());

    Collections.shuffle(neighbors); // do we really care?
    for (BlockFace neighborFace: neighbors) {

      // Get the adjacent block, and skip non-leaf / non-persistent blocks
      final Block block = targetBlock.getRelative(neighborFace);
      if (!Tag.LEAVES.isTagged(block.getType())) {
        continue;
      }

      Leaves leaves = (Leaves) block.getBlockData();
      if (leaves.isPersistent()) {
        continue;
      }

      if (this.scheduledBlocks.contains(block)) {
        continue; // Already queued
      }

      scheduledBlocks.add(block);
    }

    // Schedule leaf decay if some were found
    processLeaves();

//    getLogger().info("Done, size is now: " + scheduledBlocks.size());
  }

  private void processLeafQueue() {

//    getLogger().info("Processing...");
    boolean decayed;

    // Iterate until one block is decayed
    do {
      // Stop if the queue is empty
      if (scheduledBlocks.isEmpty()) break;

      // Get the next block in the queue
      Block block = scheduledBlocks.iterator().next();

      // Make it go away
      decayed = decay(block);
    } while (!decayed);

    // If there are still more blocks to process, recursively schedule
    this.queueRunning = false;
    processLeaves();
  }

  private void processLeaves() {
//    getLogger().info("Leaf queue is: " + scheduledBlocks.size());
    if (!scheduledBlocks.isEmpty() && !this.queueRunning) {
      this.queueRunning = true;
      getServer().getScheduler().runTaskLater(this, this::processLeafQueue, this.decayDelay);
    }
  }

  private boolean decay(Block block) {
    // Not scheduled? Ignore
    if (!this.scheduledBlocks.remove(block)) return false;

    // Chunk not loaded? Ignore
    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;

    // Not leaves? Ignore
    if (!Tag.LEAVES.isTagged(block.getType())) return false;

    Leaves leaves = (Leaves) block.getBlockData();

    // Not natural? Ignore
    if (leaves.isPersistent()) return false;

    // Verify distance from log (leaves 6 blocks or less from a log do not decay)
//    getLogger().info("Leaf distance: " + leaves.getDistance());
    if (leaves.getDistance() < 7) return false; // Decay only if further than 7 blocks from the log? why?

    // Generate a new leaf decay event and if cancelled, stop
    LeavesDecayEvent event = new LeavesDecayEvent(block);
    getServer().getPluginManager().callEvent(event);
    if (event.isCancelled()) return false;

    // Make it happened capt'n
    block.breakNaturally();

    // Look for neighboring leaves
    this.addAdjacentLeavesToQueue(block);

    return true;
  }

}
